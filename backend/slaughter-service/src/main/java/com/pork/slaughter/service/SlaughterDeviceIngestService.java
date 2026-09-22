package com.pork.slaughter.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.util.BusinessNoGenerator;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.slaughter.client.BreedingClient;
import com.pork.slaughter.dto.DeviceIngestRequests;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.entity.EntryInspection;
import com.pork.slaughter.entity.RactopamineTest;
import com.pork.slaughter.entity.SlaughterInspection;
import com.pork.slaughter.mapper.CarcassStampMapper;
import com.pork.slaughter.mapper.EntryInspectionMapper;
import com.pork.slaughter.mapper.RactopamineTestMapper;
import com.pork.slaughter.mapper.SlaughterInspectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备通道写入（入场查验 / 瘦肉精检测 / 屠宰检验 / 胴体盖章）。
 * <p>
 * 底线规则：
 * <ol>
 *   <li><b>结果缺失不入库</b>：瘦肉精仪器没出结果时不得生成"阴性"记录；检验终端没给结论时
 *       不得生成"合格"检验——否则等于机器替兽医签合格结论。</li>
 *   <li><b>判定权在人</b>：检验结论必须落具体兽医（veterinary），盖章必须落授权兽医。</li>
 *   <li><b>盖章以检验为前提</b>：自动盖章机上报时，业务侧硬校验该猪已有结论为"合格"的
 *       屠宰检验记录，没有就不准打章——人工确认也绕不过这道。</li>
 *   <li><b>来源必须可追</b>：source/source_ref/raw_payload 三件套强制落库，审计时能区分人工与设备。</li>
 * </ol>
 * 不在这里抛业务异常——可人工兜底的情况统一用 {@code written=false + retryable=true} 回执，
 * 由接入层放进待人工处理队列，避免设备端把「暂时不满足条件」误当成「数据错误」而反复重推。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaughterDeviceIngestService {

    /** 同一头猪在建批次前重复查验，属于重复上报而非新数据 */
    private static final String SOURCE_DEVICE = "DEVICE";

    private final EntryInspectionMapper entryMapper;
    private final RactopamineTestMapper testMapper;
    private final SlaughterInspectionMapper inspectionMapper;
    private final CarcassStampMapper stampMapper;
    private final ChainEventPublisher chainEvents;
    private final BreedingClient breedingClient;

    /** 入场查验：设备已采集，落库即可；重复上报按「耳标 + 批次」去重返回既有记录 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestEntry(DeviceIngestRequests.DeviceEntry request) {
        if (request.getArriveTime() == null) {
            return rejected("到厂时间缺失，无法生成查验记录");
        }

        EntryInspection exists = entryMapper.selectOne(Wrappers.<EntryInspection>lambdaQuery()
                .eq(EntryInspection::getEarTagNo, request.getEarTagNo())
                .eq(StringUtils.hasText(request.getBatchNo()), EntryInspection::getBatchNo, request.getBatchNo())
                .last("LIMIT 1"));
        if (exists != null) {
            // 幂等：接入层已按 bizKey 去重，这里是跨设备/人工录入的兜底
            log.info("ingest_entry_duplicate earTagNo={} batchNo={} id={}",
                    request.getEarTagNo(), request.getBatchNo(), exists.getId());
            return written(exists.getId(), "该耳标在本批次已有查验记录，返回既有记录");
        }

        EntryInspection entity = new EntryInspection();
        entity.setPigId(request.getPigId());
        entity.setEarTagNo(request.getEarTagNo());
        entity.setBatchNo(request.getBatchNo());
        entity.setSourceFarm(request.getSourceFarm());
        entity.setArriveTime(request.getArriveTime());
        entity.setVehicleNo(request.getVehicleNo());
        entity.setWeight(request.getWeight());
        entity.setQuarantineCert(request.getQuarantineCert());
        entity.setHealthCheck(request.getHealthCheck() == null ? 1 : request.getHealthCheck());
        entity.setCertVerified(request.getCertVerified() == null ? 0 : request.getCertVerified());
        entity.setAbnormalNote(request.getAbnormalNote());
        entity.setInspector(StringUtils.hasText(request.getInspector()) ? request.getInspector() : "设备自动采集");
        entity.setSource(SOURCE_DEVICE);
        entity.setSourceRef(request.getSourceRef());
        entity.setRawPayload(request.getRawPayload());
        entity.setRemark(request.getRemark());
        // 闸口设备只能判「有异常体征/无异常体征」，落地为合格/不合格需要兽医在人工入口确认：
        // 有异常体征 → 不合格(2) 直接拦下；无异常体征 → 合格(1)
        entity.setStatus(Integer.valueOf(1).equals(entity.getHealthCheck()) ? 1 : 2);
        entity.setCreateTime(LocalDateTime.now());
        entryMapper.insert(entity);
        return written(entity.getId(), "入场查验已入账");
    }

    /** 瘦肉精检测：仪器结果必填；阳性结果落库但不阻断，由屠宰检验环节判定 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestRactopamine(DeviceIngestRequests.DeviceRactopamine request) {
        if (request.getResult() == null) {
            // 结构性错误：设备只推了耳标没推结果，绝不默认阴性
            return rejected("仪器未输出检测结果，禁止入库（不得默认按阴性处理）");
        }
        if (!StringUtils.hasText(request.getBatchNo())) {
            // batch_no 在库里是 NOT NULL：这里先挡一道，免得脏数据落到数据库层炸成 500，
            // 设备端拿到"系统内部异常"只会以为是网络问题而反复重推
            return rejected("缺少批次号(batchNo)，检测记录无法归属批次");
        }
        if (request.getPigId() == null) {
            // 耳标无法关联生猪档案时由接入层转人工，这里只做最后兜底
            return manual("检测记录未关联到生猪档案(耳标 " + request.getEarTagNo() + ")，请人工核对");
        }

        RactopamineTest exists = testMapper.selectOne(Wrappers.<RactopamineTest>lambdaQuery()
                .eq(RactopamineTest::getSampleNo, request.getSampleNo())
                .last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_ractopamine_duplicate sampleNo={} id={}", request.getSampleNo(), exists.getId());
            return written(exists.getId(), "该样本已有检测记录，返回既有记录");
        }

        RactopamineTest entity = new RactopamineTest();
        entity.setPigId(request.getPigId());
        entity.setEarTagNo(request.getEarTagNo());
        entity.setBatchNo(request.getBatchNo());
        entity.setSampleNo(request.getSampleNo());
        entity.setTestNo(BusinessNoGenerator.next("RT"));
        entity.setTestType(StringUtils.hasText(request.getTestType()) ? request.getTestType() : "瘦肉精快速检测");
        entity.setTestTime(request.getTestTime() == null ? LocalDateTime.now() : request.getTestTime());
        entity.setTestMethod(request.getTestMethod());
        entity.setTestTarget(request.getTestTarget());
        entity.setSamplePart(request.getSamplePart());
        entity.setResult(request.getResult());
        // 0=待检测 1=检测中 2=已完成：仪器已出结果即为已完成
        entity.setStatus(2);
        entity.setDetectionLimit(request.getDetectionLimit());
        entity.setOperator(StringUtils.hasText(request.getOperator()) ? request.getOperator() : "设备自动采集");
        entity.setSource(SOURCE_DEVICE);
        entity.setSourceRef(request.getSourceRef());
        entity.setRawPayload(request.getRawPayload());
        entity.setReportUrl(request.getReportUrl());
        entity.setRemark(request.getRemark());
        entity.setCreateTime(LocalDateTime.now());
        testMapper.insert(entity);
        return written(entity.getId(), Integer.valueOf(1).equals(request.getResult()) ? "检测结果阴性，已入账" : "检测结果阳性，已入账并需阻断后续流程");
    }

    private Map<String, Object> written(Long id, String reason) {
        return result(true, id, reason, false);
    }

    /** 屠宰检验（工位终端）：结论与检验人由接入层硬校验，这里兜底并处理幂等/上链/状态推进 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestInspection(DeviceIngestRequests.DeviceInspection request) {
        if (request.getResult() == null) {
            // 结构性错误：终端没给结论，绝不默认按合格处理
            return rejected("终端未给出检验结论(result)，禁止入库（不得默认按合格处理）");
        }
        if (!StringUtils.hasText(request.getBatchNo())) {
            return rejected("缺少批次号(batchNo)，检验记录无法归属批次");
        }
        if (request.getPigId() == null) {
            return manual("检验记录未关联到生猪档案（耳标 " + request.getEarTagNo() + "），请人工核对");
        }
        Integer inspectType = request.getInspectType() == null ? 2 : request.getInspectType();

        SlaughterInspection exists = inspectionMapper.selectOne(Wrappers.<SlaughterInspection>lambdaQuery()
                .eq(SlaughterInspection::getPigId, request.getPigId())
                .eq(SlaughterInspection::getBatchNo, request.getBatchNo())
                .eq(SlaughterInspection::getInspectType, inspectType)
                .last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_inspection_duplicate pigId={} batchNo={} type={} id={}",
                    request.getPigId(), request.getBatchNo(), inspectType, exists.getId());
            return written(exists.getId(), "该猪在本批次已有同类型检验记录，返回既有记录");
        }

        SlaughterInspection entity = new SlaughterInspection();
        entity.setPigId(request.getPigId());
        entity.setEarTagNo(request.getEarTagNo());
        entity.setBatchNo(request.getBatchNo());
        entity.setInspectNo(BusinessNoGenerator.next("SI"));
        entity.setInspectType(inspectType);
        entity.setInspectTime(LocalDateTime.now());
        entity.setTemperature(request.getTemperature());
        entity.setOrganCheck(request.getOrganCheck());
        entity.setResult(request.getResult());
        // 状态机：1合格 2不合格（0待检验只属于人工待办，设备上报时必有结论）
        entity.setStatus(Integer.valueOf(1).equals(request.getResult()) ? 1 : 2);
        entity.setConclusion(request.getConclusion());
        entity.setIssueDesc(request.getIssueDesc());
        entity.setDisposal(request.getDisposal());
        entity.setVeterinary(request.getVeterinary());
        entity.setLicenseNo(request.getLicenseNo());
        entity.setSource(SOURCE_DEVICE);
        entity.setSourceRef(request.getSourceRef());
        entity.setRawPayload(request.getRawPayload());
        entity.setContentHash(HashUtil.sha256Json(request));
        entity.setCreateTime(LocalDateTime.now());
        inspectionMapper.insert(entity);

        // 与人工入口一致：检验记录上链存证；宰后/同步检验意味着屠宰完成，推进生猪状态（失败不阻塞）
        chainEvents.publish(ChainTxMessage.create("SLAUGHTER_INSPECT", entity.getId(), entity.getInspectNo(),
                entity.getContentHash(), Map.of("inspectNo", entity.getInspectNo(), "pigId", entity.getPigId())));
        if (inspectType >= 2) {
            breedingClient.advanceStatus(request.getPigId(), 3);
        }
        return written(entity.getId(), Integer.valueOf(1).equals(request.getResult())
                ? "检验结论合格，已入账（检验人：" + request.getVeterinary() + "）"
                : "检验结论不合格，已入账并需阻断后续流程");
    }

    /** 胴体盖章（自动盖章机）：硬校验"已有合格检验"，人工确认也绕不过这道 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestStamp(DeviceIngestRequests.DeviceStamp request) {
        if (request.getPigId() == null) {
            return manual("盖章记录未关联到生猪档案（耳标 " + request.getEarTagNo() + "），请人工核对");
        }
        CarcassStamp exists = stampMapper.selectOne(Wrappers.<CarcassStamp>lambdaQuery()
                .eq(CarcassStamp::getCarcassNo, request.getCarcassNo()).last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_stamp_duplicate carcassNo={} id={}", request.getCarcassNo(), exists.getId());
            return written(exists.getId(), "该胴体已有盖章记录，返回既有记录");
        }
        Long passed = inspectionMapper.selectCount(Wrappers.<SlaughterInspection>lambdaQuery()
                .eq(SlaughterInspection::getPigId, request.getPigId())
                .eq(StringUtils.hasText(request.getBatchNo()), SlaughterInspection::getBatchNo, request.getBatchNo())
                .eq(SlaughterInspection::getResult, 1));
        if (passed == null || passed == 0) {
            // 确定性拒绝：没有合格检验就不存在"补一个条件再盖章"的情形，force 也不放行
            return rejected("该猪（耳标 " + request.getEarTagNo() + "）没有结论为「合格」的屠宰检验记录，禁止自动盖章");
        }

        CarcassStamp entity = new CarcassStamp();
        entity.setPigId(request.getPigId());
        entity.setEarTagNo(request.getEarTagNo());
        entity.setBatchNo(request.getBatchNo());
        entity.setCarcassNo(request.getCarcassNo());
        entity.setStampNo(BusinessNoGenerator.next("ST"));
        entity.setStampType(StringUtils.hasText(request.getStampType()) ? request.getStampType() : "检疫合格章");
        entity.setStampTime(LocalDateTime.now());
        entity.setStampPosition(StringUtils.hasText(request.getStampPosition()) ? request.getStampPosition() : "胴体两侧臀部");
        entity.setVeterinary(request.getVeterinary());
        entity.setStatus(1);
        entity.setRemark(request.getRemark());
        entity.setSource(SOURCE_DEVICE);
        entity.setSourceRef(request.getSourceRef());
        entity.setRawPayload(request.getRawPayload());
        entity.setContentHash(HashUtil.sha256Json(request));
        entity.setCreateTime(LocalDateTime.now());
        stampMapper.insert(entity);

        chainEvents.publish(ChainTxMessage.create("SLAUGHTER_INSPECT", entity.getId(), entity.getStampNo(),
                entity.getContentHash(), Map.of("stampNo", entity.getStampNo(), "pigId", entity.getPigId())));
        return written(entity.getId(), "胴体已盖章入账（章号 " + entity.getStampNo() + "）");
    }

    private Map<String, Object> manual(String reason) {
        return result(false, null, reason, true);
    }

    private Map<String, Object> rejected(String reason) {
        return result(false, null, reason, false);
    }

    private Map<String, Object> result(boolean written, Long id, String reason, boolean retryable) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("written", written);
        body.put("id", id);
        body.put("reason", reason);
        body.put("retryable", retryable);
        return body;
    }
}
