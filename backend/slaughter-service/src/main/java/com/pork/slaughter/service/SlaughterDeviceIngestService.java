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
import com.pork.slaughter.support.PreSlaughterGate;
import com.pork.slaughter.support.RactopamineGuard;
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
 * 设备通道写入（入场查验 / 瘦肉精检测 / 屠宰检验 / 胴体盖章）。
 * <p>
 * 底线规则：
 * <ol>
 *   <li><b>入场以出栏审批为前提</b>：未经出栏审批通过的猪不得入场查验——审批是「要不要卖、卖给谁」
 *       的责任判断，没有它，进厂这件事就没有依据。force 也不放行。</li>
 *   <li><b>终态的猪不再入厂</b>：已屠宰、异常死亡/淘汰的猪是终态，不能再入场查验——
 *       一头猪只能进厂一次，放第二次会把两条互不相干的追溯链挂到同一个耳标上。</li>
 *   <li><b>结果缺失不入库</b>：瘦肉精仪器没出结果时不得生成"阴性"记录；检验终端没给结论时
 *       不得生成"合格"检验——否则等于机器替兽医签合格结论。</li>
 *   <li><b>一批阳性整批停</b>：瘦肉精是<b>按批抽检</b>，抽检结论落在「批」上——检出阳性时，
 *       该猪及其所属批次的猪一律不得进入屠宰检验及后续环节（判定见 {@link com.pork.slaughter.support.RactopamineGuard}）。
 *       阳性是要走隔离与上报处置的事实，补材料解决不了，因此确定性拒绝，force 也不放行。
 *       这一道只约束<b>宰后类</b>环节（宰后检验 / 盖章 / 胴体批次）——宰前检查不受它约束，
 *       否则阳性猪连一条「宰前不合格」的处置记录都留不下。</li>
 *   <li><b>未检不得屠宰</b>：检测记录本身就是「这批猪被检过」的凭证，查不到记录（自身没有，
 *       所属批次也没有）就是没检过，同样一律不得进入屠宰检验及后续环节。这条与阳性阻断同一道判定、
 *       同一批粒度，同样只约束<b>宰后类</b>环节，见 {@link com.pork.slaughter.support.RactopamineGuard}。</li>
 *   <li><b>宰前检查前置</b>：宰后检疫是「屠宰之后」的环节，前提是这头猪已经通过宰前检查、准予屠宰。
 *       查不到合格的宰前检查记录——不管是没检过，还是检了判不合格——一律不得进入宰后检验及后续环节。
 *       规程要求的是逐头临床检查，所以这一道判<b>头</b>不判批，见
 *       {@link com.pork.slaughter.support.PreSlaughterGate}。</li>
 *   <li><b>宰前检查只记录、不设前置，但不得与瘦肉精自相矛盾</b>：宰前检查是屠宰前的临床筛查，
 *       不设前置门槛（实验室检测可能还没做，不能拿「未检」卡它）；但已检出阳性的猪（自身检出，
 *       或所属批次受控）只能如实判「不合格 / 不准予屠宰」，不能判「合格」——
 *       否则库里会留下一条「瘦肉精阳性 + 宰前合格」的病态记录。</li>
 *   <li><b>判定权在人</b>：检验结论必须落具体兽医（veterinary），盖章必须落授权兽医。</li>
 *   <li><b>盖章以宰后检疫为前提</b>：自动盖章机上报时，业务侧硬校验该猪已有结论为"合格"的
 *       <b>宰后类</b>检验记录（宰后检验 / 宰后同步检验），没有就不准打章——盖章发生在同步检疫之后，
 *       只有一条宰前合格不算数，人工确认也绕不过这道。</li>
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
    /**
     * 「宰后类」检验的 inspect_type 集合：2=宰后检验、3=宰后同步检验。
     * <p>
     * 按法规口径这两个是同一个环节——《动物检疫管理办法》第二十条「动物屠宰过程实行<b>全流程同步检疫</b>」，
     * 宰后检疫的标准做法就是同步检疫，所以盖章时它们都算「已经过了宰后检疫」。
     */
    private static final List<Integer> POST_SLAUGHTER_TYPES = List.of(2, 3);

    private final EntryInspectionMapper entryMapper;
    private final RactopamineTestMapper testMapper;
    private final SlaughterInspectionMapper inspectionMapper;
    private final CarcassStampMapper stampMapper;
    private final ChainEventPublisher chainEvents;
    private final BreedingClient breedingClient;
    private final RactopamineGuard ractopamineGuard;
    private final PreSlaughterGate preSlaughterGate;

    /**
     * 入场查验：设备已采集，落库即可；重复上报按「耳标 + 批次」去重返回既有记录。
     * <p>
     * 两道硬前提，都在业务侧判、人工确认（force）也放不过去：
     * <ol>
     *   <li>这头猪必须<b>已经取得出栏审批（通过）</b>。未经审批就进厂，等于把「要不要卖、卖给谁」
     *       这道人的责任关卡绕过去了——这不是「补一个字段」能解决的问题（要去走审批流程），
     *       因此确定性拒绝。</li>
     *   <li>这头猪的状态必须是<b>「在养」或「已出栏」</b>。「已屠宰」「异常」是终态：
     *       一头已经屠宰过的猪不可能再进厂一次，放进来只会把两条追溯链挂到同一个耳标上。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestEntry(DeviceIngestRequests.DeviceEntry request) {
        if (request.getArriveTime() == null) {
            return rejected("到厂时间缺失，无法生成查验记录");
        }
        if (request.getPigId() == null) {
            // 耳标无法关联生猪档案时由接入层转人工，这里只做最后兜底
            return manual("查验记录未关联到生猪档案（耳标 " + request.getEarTagNo() + "），请人工核对");
        }

        // 第一道：出栏审批必须已通过。放在最前面，因为它是「这头猪凭什么能进厂」的答案，
        // 检疫证、健康体征都只是进场后的合规细节。
        BreedingClient.ExitApplyCheck apply = breedingClient.exitApplyOfPig(request.getPigId());
        if (!apply.reachable()) {
            // 核不了就是不核不了：宁可排队等人工，也不能默许进来
            return manual(apply.reason());
        }
        if (!apply.approved()) {
            return rejected("该猪（耳标 " + request.getEarTagNo() + "）" + apply.reason()
                    + "，未经出栏审批通过不得入场查验");
        }

        // 第二道：状态必须还在可流通的环节里。终态（已屠宰 / 异常）的猪确定性拒绝，
        // 同「重复入厂」一样属于业务事实，补材料解决不了，所以不给重试机会。
        BreedingClient.PigState state = breedingClient.pigState(request.getPigId());
        if (!state.reachable()) {
            return manual(state.reason());
        }
        if (!state.enterable()) {
            return rejected("该猪（耳标 " + request.getEarTagNo() + "）当前状态为「"
                    + (StringUtils.hasText(state.label()) ? state.label() : "未知") + "」，已不能再入场查验"
                    + "（只有「在养」「已出栏」的猪可以入场）");
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
        entity.setInspector(StringUtils.hasText(request.getInspector()) ? request.getInspector() : "未记录");
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

    /**
     * 瘦肉精检测：仪器结果必填。
     * <p>
     * 阳性结果<b>照常入账留证</b>——阳性是要留痕、要隔离、要按规定上报的事实，不能因为「不好看」就不落库；
     * 阻断判断不在这里做，而是由屠宰检验、胴体盖章、胴体批次创建三方在写入前各查一次
     * {@link RactopamineGuard}，避免「谁先写入谁后写入」的时序漏洞。
     */
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
        entity.setOperator(StringUtils.hasText(request.getOperator()) ? request.getOperator() : "未记录");
        entity.setSource(SOURCE_DEVICE);
        entity.setSourceRef(request.getSourceRef());
        entity.setRawPayload(request.getRawPayload());
        entity.setReportUrl(request.getReportUrl());
        entity.setRemark(request.getRemark());
        entity.setCreateTime(LocalDateTime.now());
        testMapper.insert(entity);
        return written(entity.getId(), Integer.valueOf(1).equals(request.getResult())
                ? "检测结果阴性，已入账"
                : "检测结果阳性，已入账；该猪及批次「" + request.getBatchNo() + "」已被阻断，不得进入屠宰检验及后续环节");
    }

    private Map<String, Object> written(Long id, String reason) {
        return result(true, id, reason, false);
    }

    /**
     * 屠宰检验（工位终端）：结论与检验人由接入层硬校验，这里兜底并处理幂等/上链/状态推进。
     * <p>
     * 前置闸门按检验类型分档：<b>宰后类</b>（宰后检验 / 宰后同步检验）要过「瘦肉精阳性·未检」与
     * 「未做宰前检查」两道；<b>宰前检查</b>本身不设前置，只是已检出阳性的猪不得判「合格」。
     */
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
        boolean isPostSlaughter = POST_SLAUGHTER_TYPES.contains(inspectType);

        // 瘦肉精阻断（阳性 + 未检）：**只约束宰后类**。宰前检查是屠宰前的临床筛查，它的职责恰恰是
        // 查出不合格的猪——拿结论可能还没出的实验室检测去卡它，等于「体检前先证明你没病」，逻辑倒置，
        // 也会让阳性猪连一条「宰前不合格」的处置记录都留不下来。真正的安全闸门在「宰后检验入口」
        // 与「盖章入口」两道，那里不放宽。放在幂等查找之前——被拦的猪之后设备再推，要拿到「被拒绝」
        // 而不是「返回既有记录」，否则等于让幂等把阻断结论洗成一条正常的检验记录。
        if (isPostSlaughter) {
            String ractopamineBlock = ractopamineGuard.blockReason(request.getPigId(), request.getBatchNo());
            if (ractopamineBlock != null) {
                return rejected(ractopamineBlock);
            }
        }

        // 宰前检查前置：宰后检验、宰后同步检验都是「屠宰之后」的环节，前提是这头猪已经通过宰前检查、
        // 准予屠宰（《动物检疫管理办法》第二十条：屠宰前应当逐头临床检查，健康的方可屠宰）。
        // 只约束宰后类——宰前检查记录本身当然不受这道约束，否则一头猪也进不来。
        // 同样放在幂等查找之前：被拦的猪之后设备再推，要拿到「被拒绝」而不是「返回既有记录」。
        if (isPostSlaughter) {
            String preSlaughterBlock = preSlaughterGate.blockReason(request.getPigId(), request.getEarTagNo());
            if (preSlaughterBlock != null) {
                return rejected(preSlaughterBlock);
            }
        }

        // 宰前检查本身不设前置门槛，但不得与瘦肉精结论自相矛盾：已检出阳性的猪（自身检出，或所属批次
        // 受控）只能如实记录「不合格 / 不准予屠宰」，不能判「合格」——否则库里会留下一条
        // 「瘦肉精阳性 + 宰前合格」的病态记录。「未检」不拦：宰前临床检查可能先于实验室检测完成。
        // 放在幂等之前，让被拦的重推拿到「被拒绝」而不是「返回既有记录」。
        if (!isPostSlaughter && Integer.valueOf(1).equals(request.getResult())
                && ractopamineGuard.isPositive(request.getPigId(), request.getBatchNo())) {
            return rejected("该猪（耳标 " + request.getEarTagNo() + "）或其所属批次已检出瘦肉精阳性，"
                    + "宰前检查不得判定为「合格」（阳性生猪须按规定隔离观察、急宰或无害化处理，"
                    + "请如实记录不合格结论与处置方式）");
        }

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

    /**
     * 胴体盖章（自动盖章机）：两道硬校验——「瘦肉精非阳性且已检」+「已有结论合格的宰后检验」，
     * 人工确认都绕不过去。盖章等于把肉品放行，所以判在写入之前。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestStamp(DeviceIngestRequests.DeviceStamp request) {
        if (request.getPigId() == null) {
            return manual("盖章记录未关联到生猪档案（耳标 " + request.getEarTagNo() + "），请人工核对");
        }
        // 瘦肉精阻断（阳性 + 未检）：盖章等于把肉品放行，阳性猪连章都不该有，没检过的猪同样不该有。
        // 这一道判在「已有合格检验」之前——被阻断的猪本来就拿不到合格检验，
        // 先判阻断才能给出「因为阳性/因为未检」而不是「因为没有合格检验」这个更准确的原因。
        String ractopamineBlock = ractopamineGuard.blockReason(request.getPigId(), request.getBatchNo());
        if (ractopamineBlock != null) {
            return rejected(ractopamineBlock);
        }
        CarcassStamp exists = stampMapper.selectOne(Wrappers.<CarcassStamp>lambdaQuery()
                .eq(CarcassStamp::getCarcassNo, request.getCarcassNo()).last("LIMIT 1"));
        if (exists != null) {
            log.info("ingest_stamp_duplicate carcassNo={} id={}", request.getCarcassNo(), exists.getId());
            return written(exists.getId(), "该胴体已有盖章记录，返回既有记录");
        }
        // 一头白条只有一枚章（`carcass_stamp` 上 `pig_id` 是唯一键，作废只是打 void_flag、行还在）。
        // 同一头猪换个胴体编号再报一次，既不是上面那种「同一号重推」，也不是能直接入账的新数据 ——
        // 必须在这里拦下来说清楚，否则会直接撞数据库唯一键，冒成一次「系统内部异常」。
        Map<String, Object> anyStamp = stampMapper.selectAnyByPigId(request.getPigId());
        if (anyStamp != null) {
            Object voidFlag = anyStamp.get("voidFlag");
            boolean voided = voidFlag instanceof Number && ((Number) voidFlag).intValue() == 1;
            String existsNo = String.valueOf(anyStamp.get("carcassNo"));
            log.info("ingest_stamp_pig_conflict pigId={} existsCarcassNo={} voided={} incomingCarcassNo={}",
                    request.getPigId(), existsNo, voided, request.getCarcassNo());
            if (voided) {
                return manual("该白条（耳标 " + request.getEarTagNo() + "）原有一枚已作废的章（胴体编号 "
                        + existsNo + "）；一头白条只能有一枚章，需重盖请人工核对处理");
            }
            return manual("该白条（耳标 " + request.getEarTagNo() + "）已有盖章记录（胴体编号 "
                    + existsNo + "），本次上报的胴体编号是 " + request.getCarcassNo()
                    + "，两者不一致：请人工核对是重复盖章还是编号录错");
        }
        // 盖章发生在「同步检疫之后」：必须有一条结论合格的**宰后类**检验（宰后检验 / 宰后同步检验）。
        // 这里不能像以前那样「任意类型有一条合格就算数」——只做过宰前检查的猪也满足「有合格检验」，
        // 但它根本还没经过宰后检疫，盖章等于替一头没检过的肉品放行。
        Long passed = inspectionMapper.selectCount(Wrappers.<SlaughterInspection>lambdaQuery()
                .eq(SlaughterInspection::getPigId, request.getPigId())
                .eq(StringUtils.hasText(request.getBatchNo()), SlaughterInspection::getBatchNo, request.getBatchNo())
                .in(SlaughterInspection::getInspectType, POST_SLAUGHTER_TYPES)
                .eq(SlaughterInspection::getResult, 1));
        if (passed == null || passed == 0) {
            // 确定性拒绝：没有合格检验就不存在"补一个条件再盖章"的情形，force 也不放行
            return rejected("该猪（耳标 " + request.getEarTagNo() + "）没有结论为「合格」的宰后检验记录，"
                    + "禁止自动盖章（盖章发生在同步检疫之后，须先完成宰前检查与宰后同步检疫并判合格）");
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
