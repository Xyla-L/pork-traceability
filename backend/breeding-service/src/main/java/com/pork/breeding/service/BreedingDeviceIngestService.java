package com.pork.breeding.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.dto.BreedingDeviceIngestRequests;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.entity.QuarantineCertificate;
import com.pork.breeding.entity.SlaughterApply;
import com.pork.breeding.entity.VaccineRecord;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.mapper.QuarantineCertMapper;
import com.pork.breeding.mapper.SlaughterApplyMapper;
import com.pork.breeding.mapper.VaccineRecordMapper;
import com.pork.breeding.support.ImmunityGate;
import com.pork.core.util.BusinessNoGenerator;
import com.pork.core.util.HashUtil;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备通道写入（养殖建档 / 免疫注射 / 产地检疫出证 / 申请出栏）。
 * <p>
 * 两条底线：
 * <ol>
 *   <li><b>无批号不入库</b>：疫苗批号是溯源疫苗来源的唯一线索，缺失一律拒绝，设备不得默认值。</li>
 *   <li><b>来源必须可追</b>：source/source_ref/raw_payload 三件套强制落库。
 *       pig_individual 例外用 data_source——它的 source 列是业务上的"自繁/外购"，早被占用。</li>
 * </ol>
 * 可人工兜底的情况统一 {@code written=false + retryable=true} 回执，由接入层转待人工队列；
 * 确定性错误（猪已出栏、已有一条有效申报）用 {@code retryable=false} 判拒绝。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BreedingDeviceIngestService {

    private static final String SOURCE_DEVICE = "DEVICE";

    /** 出栏申报审批状态：0 待审 / 1 通过 / 2 驳回（与 SlaughterApplyServiceImpl 的常量口径保持一致） */
    private static final int APPLY_STATUS_PENDING = 0;
    private static final int APPLY_STATUS_APPROVED = 1;

    /** 生猪状态：1 在养 / 2 已出栏 / 3 已屠宰 / 4 异常淘汰 */
    private static final int PIG_STATUS_FARMING = 1;
    private static final int PIG_STATUS_SLAUGHTERED = 3;

    private final PigIndividualMapper pigMapper;
    private final VaccineRecordMapper vaccineMapper;
    private final QuarantineCertMapper certMapper;
    private final SlaughterApplyMapper applyMapper;
    private final ChainEventPublisher chainEvents;
    private final ImmunityGate immunityGate;

    /**
     * 养殖建档：耳标读写器。
     * <p>
     * 设备能力边界：读写器只提供耳标号，批次信息（养殖场、批次号、品种、进场日期）
     * 是人的知识，由人工按批次登记。个体行由批次按耳标区间自动展开，
     * 所以设备上报只有两种结局：耳标落在某个批次区间内 → 幂等返回既有档案；
     * 耳标不在任何区间 → 转待人工（人工登记批次后重放/确认即入账）。
     * <b>设备不再直接建档，个体也不再逐头建档</b>。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestTag(BreedingDeviceIngestRequests.DeviceTag request) {
        PigIndividual exists = pigMapper.selectOne(Wrappers.<PigIndividual>lambdaQuery()
                .eq(PigIndividual::getEarTagNo, request.getEarTagNo()).last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_tag_duplicate earTagNo={} id={}", request.getEarTagNo(), exists.getId());
            return written(exists.getId(), "该耳标已在批次档案中，返回既有档案");
        }

        // 新耳标：批次信息（养殖场、批次号、品种、进场日期）设备给不出，也不该给 ——
        // 转待人工，人在生猪批次档案登记批次（耳标区间覆盖该号）后再确认入账。
        return manual("读写器只读到耳标号 " + request.getEarTagNo()
                + "，该耳标不在任何已登记批次区间内：请先到「生猪批次档案」登记批次（耳标区间含此号），"
                + "再到接入监控确认入账");
    }

    /** 免疫注射：疫苗批号必填（缺批号拒收），同一头猪同批号同时间已记录则幂等返回 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestVaccine(BreedingDeviceIngestRequests.DeviceVaccine request) {
        if (request.getPigId() == null) {
            return manual("未关联到生猪档案（耳标 " + request.getEarTagNo() + "），请人工核对");
        }

        // 只有「在养」的猪能记录免疫：已出栏/已屠宰的猪打疫苗在逻辑上不成立，
        // 撞上这一条说明耳标读错、报文串号或设备重放了历史报文 —— 一律拒绝，不留待人工
        PigIndividual pig = pigMapper.selectById(request.getPigId());
        if (pig != null && pig.getStatus() != null && pig.getStatus() != PIG_STATUS_FARMING) {
            return refused("该生猪当前状态为「" + pigStatusLabel(pig.getStatus())
                    + "」，不能再记录免疫（在养状态才能接种）");
        }

        VaccineRecord exists = vaccineMapper.selectOne(Wrappers.<VaccineRecord>lambdaQuery()
                .eq(VaccineRecord::getPigId, request.getPigId())
                .eq(VaccineRecord::getVaccineName, request.getVaccineName())
                .eq(VaccineRecord::getBatchNo, request.getVaccineBatchNo())
                .eq(request.getInjectTime() != null, VaccineRecord::getInjectTime, request.getInjectTime())
                .last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_vaccine_duplicate pigId={} id={}", request.getPigId(), exists.getId());
            return written(exists.getId(), "该免疫记录已存在，返回既有记录");
        }

        VaccineRecord record = new VaccineRecord();
        record.setPigId(request.getPigId());
        record.setVaccineName(request.getVaccineName());
        record.setBatchNo(request.getVaccineBatchNo());
        record.setManufacturer(request.getManufacturer());
        record.setInjectTime(request.getInjectTime() == null ? LocalDateTime.now() : request.getInjectTime());
        record.setDosage(request.getDosage());
        record.setInjectSite(request.getInjectSite());
        // 接种人只能是现场操作注射器的兽医本人：拿不到就如实记「未记录」，
        // 绝不能用设备名（如「INJ-001 自动采集」）顶替 —— 那样追溯链上查到的是机器
        record.setOperator(StringUtils.hasText(request.getOperator()) ? request.getOperator() : "未记录");
        // 凭证照片：接入层已核验 fileId 真实存在，这里只做存储（空列表也写成 "[]"，与人工编辑口径一致）
        record.setFileIds(JSON.toJSONString(request.getFileIds() == null ? List.of() : request.getFileIds()));
        record.setSource(SOURCE_DEVICE);
        record.setSourceRef(request.getSourceRef());
        record.setRawPayload(request.getRawPayload());
        record.setCreateTime(LocalDateTime.now());
        vaccineMapper.insert(record);
        // 免疫记录上链存证：检疫出证（ImmunityGate）认的就是这些档案，事实必须可追
        Map<String, Object> vacPayload = Map.of(
                "pigId", record.getPigId(),
                "vaccineName", record.getVaccineName(),
                "vaccineBatchNo", StringUtils.hasText(record.getBatchNo()) ? record.getBatchNo() : "",
                "injectTime", record.getInjectTime() == null ? "" : record.getInjectTime().toString(),
                "operator", StringUtils.hasText(record.getOperator()) ? record.getOperator() : "未记录");
        chainEvents.publish(ChainTxMessage.create("VACCINE_RECORD", record.getId(),
                record.getPigId() + ":" + record.getVaccineName(),
                HashUtil.sha256Json(request), vacPayload));
        return written(record.getId(), "免疫记录已入账");
    }

    /**
     * 产地检疫出证：检疫证扫码登记终端。
     * <p>
     * 与其它设备通道的本质差别：<b>扫码不产生结论</b>。检疫结论与证都由官方作出，
     * 终端只是把证面上的证号/耳标/机构/有效期/官方兽医读进来替代手抄，所以这里不做任何"判定"，
     * 只做三件事：同一张证不重复登记、同一头猪不重复持证、签发人如实落库。
     * <p>
     * 可人工兜底的情况统一 {@code written=false + retryable=true}：
     * 例如这头猪已有一张有效证（换发须先作废原证），人工作废后重放这条即可入账。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestQuarantineCert(BreedingDeviceIngestRequests.DeviceQuarantineCert request) {
        if (request.getPigId() == null) {
            return manual("未关联到生猪档案（耳标 " + request.getEarTagNo() + "），请人工核对");
        }

        // 0) 业务前置：扫码登记的是「一张已经开出的证」，但证的前提必须成立，否则等于给违规调运补凭据。
        //    三条判定按确定性排序：已屠宰（不可能调运）> 未出栏但已调运（人判断）> 无免疫档案（法定前置缺失）
        PigIndividual pig = pigMapper.selectById(request.getPigId());
        if (pig != null && pig.getStatus() != null) {
            if (pig.getStatus() == PIG_STATUS_SLAUGHTERED) {
                return refused("该生猪已屠宰，不能再登记产地检疫证（产地检疫是调运前置，猪已不在场）");
            }
            if (pig.getStatus() != PIG_STATUS_FARMING) {
                return manual("该生猪当前状态为「" + pigStatusLabel(pig.getStatus())
                        + "」，已不在养殖场，产地检疫证应在出栏前签发，请人工核对纸质证与调运记录");
            }
        }
        LocalDateTime certIssueTime = request.getIssueTime() == null ? LocalDateTime.now() : request.getIssueTime();
        String immunityBlock = immunityGate.blockReason(request.getPigId(), certIssueTime);
        if (immunityBlock != null) {
            return refused(immunityBlock);
        }

        // 1) 同一张证重复登记：设备重发或网络重试都会走到这里，幂等返回既有记录，不新建
        QuarantineCertificate exists = certMapper.selectOne(Wrappers.<QuarantineCertificate>lambdaQuery()
                .eq(QuarantineCertificate::getCertNo, request.getCertNo()).last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_quarantine_duplicate certNo={} id={}", request.getCertNo(), exists.getId());
            return written(exists.getId(), "该检疫证已登记，返回既有记录");
        }

        // 2) 这头猪已有一张有效证：属于换发场景。作废是终态动作、必须实名留痕，
        //    所以不在这里悄悄覆盖，交人工确认（先作废原证，再放行这条）
        QuarantineCertificate current = certMapper.selectOne(Wrappers.<QuarantineCertificate>lambdaQuery()
                .eq(QuarantineCertificate::getPigId, request.getPigId()).last("LIMIT 1"));
        if (current != null) {
            return manual("该生猪已有有效检疫证（" + current.getCertNo() + "），如需换发请先作废原证再登记");
        }

        QuarantineCertificate cert = new QuarantineCertificate();
        cert.setPigId(request.getPigId());
        cert.setCertNo(request.getCertNo());
        cert.setIssueOrg(request.getIssueOrg());
        cert.setIssueTime(certIssueTime);
        cert.setValidUntil(request.getValidUntil());
        // 签发人只能是证面上的官方兽医：拿不到就如实记「未记录」，绝不用设备名顶替 ——
        // 否则追溯链上「谁签发的这张证」查到的是一台机器
        cert.setInspector(StringUtils.hasText(request.getInspector()) ? request.getInspector() : "未记录");
        cert.setCertType("产地检疫");
        // 扫描件与 CA 签名：扫码终端拿不到时如实留空，不写占位字符串冒充（列已允许 NULL）
        List<String> fileIds = request.getFileIds() == null ? List.of() : request.getFileIds();
        cert.setFileId(fileIds.isEmpty() ? null : fileIds.get(0));
        cert.setCaSignature(StringUtils.hasText(request.getCaSignature()) ? request.getCaSignature() : null);
        cert.setContentHash(HashUtil.sha256Json(request));
        cert.setSource(SOURCE_DEVICE);
        cert.setSourceRef(request.getSourceRef());
        cert.setRawPayload(request.getRawPayload());
        cert.setCreateTime(LocalDateTime.now());
        certMapper.insert(cert);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certNo", cert.getCertNo());
        payload.put("pigId", cert.getPigId());
        payload.put("earTagNo", request.getEarTagNo());
        payload.put("issueOrg", cert.getIssueOrg());
        payload.put("inspector", cert.getInspector());
        // operator = 这张证的出证人，与人工出证路径语义一致（那边是登录出证人，这边是证面官方兽医）
        payload.put("operator", cert.getInspector());
        payload.put("action", "DEVICE_REGISTER");
        payload.put("deviceNo", request.getSourceRef());
        chainEvents.publish(ChainTxMessage.create("QUARANTINE_CERT", cert.getId(), cert.getCertNo(),
                cert.getContentHash(), payload));

        return written(cert.getId(), "产地检疫证已登记入账（耳标 " + request.getEarTagNo() + "）");
    }

    /**
     * 申请出栏：出栏口地磅（耳标识读 + 称重）。
     * <p>
     * 三条边界，都来自「设备只负责一半」这个事实：
     * <ol>
     *   <li><b>只有「在养」的猪能申报</b>（状态校验在接入层做，这里再兜一道）。</li>
     *   <li><b>一头猪只允许一条有效申报</b>（数据库 uk_pig）。设备重复称重、猪只折返都会撞上，
     *       不同流水号、体重略有差异，接入层的两道幂等拦不住，必须在这里按业务唯一性拦。</li>
     *   <li><b>已驳回的申报不自动复活</b>：驳回是审批人作出的结论（如「检疫不合格」），
     *       让地磅再称一次就自动重提，等于用机器推翻人的结论。这种情况转人工确认。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestExitApply(BreedingDeviceIngestRequests.DeviceExitApply request) {
        if (request.getPigId() == null) {
            return manual("未关联到生猪档案（耳标 " + request.getEarTagNo() + "），请人工核对");
        }

        SlaughterApply exists = applyMapper.selectOne(Wrappers.<SlaughterApply>lambdaQuery()
                .eq(SlaughterApply::getPigId, request.getPigId())
                .orderByDesc(SlaughterApply::getId)
                .last("LIMIT 1"));
        if (exists != null && exists.getApprovalStatus() != null) {
            if (exists.getApprovalStatus() == APPLY_STATUS_APPROVED) {
                return refused("该生猪的出栏申报已通过，本次称重不再重复申报");
            }
            if (exists.getApprovalStatus() == APPLY_STATUS_PENDING) {
                return refused("该生猪已有待审批的出栏申报（" + exists.getApplyNo() + "），本次称重不再重复申报");
            }
            // 已驳回：不自动复活，交人工决定要不要重新申报
            return manual("该生猪上一条出栏申报已被驳回"
                    + (StringUtils.hasText(exists.getRejectReason()) ? "（原因：" + exists.getRejectReason() + "）" : "")
                    + "，如确需重新出栏，请人工确认后申报");
        }

        SlaughterApply apply = new SlaughterApply();
        apply.setPigId(request.getPigId());
        apply.setApplyNo(BusinessNoGenerator.next("SA"));
        // 称重时刻取报文值，缺失时用入库时刻兜底（猪走出栏口即登记，两者等价）
        apply.setApplyTime(request.getApplyTime() == null ? LocalDateTime.now() : request.getApplyTime());
        apply.setWeightKg(request.getWeightKg() == null ? null : request.getWeightKg().doubleValue());
        apply.setTargetSlaughterhouse(request.getTargetSlaughterhouse());
        apply.setApprovalStatus(APPLY_STATUS_PENDING);
        apply.setSource(SOURCE_DEVICE);
        apply.setSourceRef(request.getSourceRef());
        apply.setRawPayload(request.getRawPayload());
        apply.setCreateTime(LocalDateTime.now());
        applyMapper.insert(apply);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applyNo", apply.getApplyNo());
        payload.put("pigId", apply.getPigId());
        payload.put("earTagNo", request.getEarTagNo());
        payload.put("weightKg", apply.getWeightKg());
        payload.put("targetSlaughterhouse", apply.getTargetSlaughterhouse());
        payload.put("action", "DEVICE_REGISTER");
        payload.put("deviceNo", request.getSourceRef());
        chainEvents.publish(ChainTxMessage.create("SLAUGHTER_APPLY", apply.getId(), apply.getApplyNo(),
                HashUtil.sha256Json(request), payload));

        return written(apply.getId(), "出栏称重登记完成（耳标 " + request.getEarTagNo() + "，"
                + (apply.getWeightKg() == null ? "体重未记录" : apply.getWeightKg() + " kg") + "）");
    }

    private Map<String, Object> written(Long id, String reason) {
        return result(true, id, reason, false);
    }

    /** 生猪状态的中文名：拒绝原因要给现场看得懂的词，不能把数字抛回去 */
    private String pigStatusLabel(Integer status) {
        return switch (status) {
            case 1 -> "在养";
            case 2 -> "已出栏";
            case 3 -> "已屠宰";
            case 4 -> "异常/淘汰";
            default -> "状态未知";
        };
    }

    private Map<String, Object> manual(String reason) {
        return result(false, null, reason, true);
    }

    /** 确定性错误：重试也不会成功，接入层据此判「已拒绝」 */
    private Map<String, Object> refused(String reason) {
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
