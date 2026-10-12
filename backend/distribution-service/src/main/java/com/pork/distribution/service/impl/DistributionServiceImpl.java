package com.pork.distribution.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.source.ManualOperator;
import com.pork.core.source.TraceStage;
import com.pork.core.util.BusinessNoGenerator;
import com.pork.core.util.HashUtil;
import com.pork.distribution.client.OrgClient;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.*;
import com.pork.distribution.mapper.*;
import com.pork.distribution.service.DistributionService;
import com.pork.distribution.support.BatchGuard;
import com.pork.distribution.support.CarcassBatchNo;
import com.pork.distribution.support.DeliveryPlanNo;
import com.pork.distribution.support.DriverSource;
import com.pork.distribution.support.MeatPartResolver;
import com.pork.distribution.support.PlanSource;
import com.pork.distribution.support.SplitYieldCheck;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import com.pork.web.source.SourceGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DistributionServiceImpl implements DistributionService {

    /** 人工录入来源标记（与设备采集 DEVICE 对应） */
    private static final String SOURCE_MANUAL = "MANUAL";
    /** 设备自动归集来源标记（屠宰线胴体称重分拣台） */
    private static final String SOURCE_DEVICE = "DEVICE";
    /** 归集状态：1=归集中 2=已封 */
    private static final int BATCH_OPEN = 1;
    private static final int BATCH_SEALED = 2;

    /** 发货计划状态：1=待发货 2=已排车（明细全部排完）3=已取消 4=部分排车（还有货在等车） */
    private static final int PLAN_PENDING = 1;
    private static final int PLAN_GENERATED = 2;
    private static final int PLAN_CANCELLED = 3;
    private static final int PLAN_PARTIAL = 4;
    /** 计划明细是否已排车：0=待排车 1=已排进某张运单 */
    private static final int ITEM_SHIPPED = 1;
    /** 运单来源：计划一键生成（人工点按钮）/ 装车口识别终端自动生成 */
    private static final String TRANSPORT_SOURCE_PLAN = "PLAN";
    private static final String TRANSPORT_SOURCE_DEVICE = "DEVICE";

    /**
     * 装车口「同一趟车继续装」的并入窗口（分钟）。
     * <p>
     * 同一车牌在这段时间内再次扫码，视为同一趟车在继续装货 → 追加到那张还没发车的运单上，
     * 而不是再开一张（详见 {@link #appendToTransport}）。窗口之外不并 ——
     * 隔了很久还挂着的「待发车」运单属于异常滞留，硬并进去等于替人认定它是活单。
     */
    @Value("${ingest.load.merge-window-minutes:120}")
    private int loadMergeWindowMinutes;
    /** 运单初始状态：待发车（与 depart/arrive 里的状态位同一口径） */
    private static final int TRANSPORT_PENDING = 1;
    /** 运单状态：运输中 */
    private static final int TRANSPORT_IN_TRANSIT = 2;
    /** 运单状态：已到达（= 全部站点都到了，进入待签收） */
    private static final int TRANSPORT_ARRIVED = 3;
    /** 运单状态：已签收（= 全部站点都签完） */
    private static final int TRANSPORT_SIGNED = 4;
    /** 计划号撞键后的重试次数（并发下两个请求可能算出同一个候选号） */
    private static final int PLAN_NO_RETRY = 5;
    /** 机构树里门店的类型值：订单只允许发往门店，别的一律转人工 */
    private static final String STORE_TYPE = "retail";

    /**
     * 「可发运批次」的排除口径（唯一来源，pagePendingSplits 与订单配货共用）：
     * 已被运单装载过、或已被「待发货 / 部分排车」计划的**未排车明细**占用。
     * 用实时引用关系判断，不用标记位 —— 标记位会与运单删除、计划取消失同步。
     */
    private static final String EXCLUDE_SHIPPED_BY_TRANSPORT = "SELECT split_batch_id FROM transport_item";
    private static final String EXCLUDE_TAKEN_BY_PENDING_PLAN =
            "SELECT i.split_batch_id FROM delivery_plan_item i JOIN delivery_plan p ON p.id = i.plan_id "
                    + "WHERE p.status IN (" + PLAN_PENDING + ", " + PLAN_PARTIAL + ") AND i.shipped = 0";
    /** 计划表 remark 是 VARCHAR(500)，拼出来的说明要截断保护 */
    private static final int PLAN_REMARK_MAX = 500;

    private final CarcassBatchMapper carcassBatchMapper;
    private final CarcassBatchPigMapper carcassBatchPigMapper;
    private final SplitBatchMapper splitBatchMapper;
    private final SplitJobMapper splitJobMapper;
    private final MeatPartMapper meatPartMapper;
    private final ColdChainTransportMapper transportMapper;
    private final TemperatureLogMapper temperatureLogMapper;
    private final StoreReceiptMapper receiptMapper;
    private final ChainEventPublisher chainEvents;
    private final com.pork.distribution.client.BreedingClient breedingClient;
    private final BatchGuard batchGuard;
    private final CarcassBatchNo carcassBatchNo;
    private final SourceGuard sourceGuard;
    /** 部位字典与「齐不齐」的口径都在它这里，避免再写一份 */
    private final SplitYieldCheck splitYieldCheck;
    private final DeliveryPlanMapper deliveryPlanMapper;
    private final DeliveryPlanItemMapper deliveryPlanItemMapper;
    private final TransportItemMapper transportItemMapper;
    /** 站点行程台账：一站一条，「到了没」的落点（装载明细装不下行程状态） */
    private final TransportStopMapper transportStopMapper;
    private final DeliveryPlanNo deliveryPlanNo;
    /** 目的地门店的权威来源在机构树（auth-service），本服务不自己维护一份门店表 */
    private final OrgClient orgClient;
    /** 部位定型口径与分割上报共用一份，避免「同一个别名一处认得、一处认不得」 */
    private final MeatPartResolver meatPartResolver;
    /** 分割车间监控快照：只读展示用（写入与淘汰在设备接入服务里） */
    private final WorkshopSnapshotMapper snapshotMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarcassBatch createBatch(DistributionRequests.BatchCreate request) {
        // 胴体批次是「设备优先」环节：分拣台按「屠宰场 + 屠宰日」自动归集是主路径，
        // 人工建批是无分拣台场景的补录：强制实名，操作人与来源一并上链留痕。
        ManualOperator operator = sourceGuard.requireManualAllowed(TraceStage.BATCH, "创建胴体批次");
        batchGuard.validateAll(request.pigIds(), null);
        CarcassBatch entity = new CarcassBatch();
        BeanUtils.copyProperties(request, entity);
        entity.setPigCount(request.pigIds() == null ? 0 : request.pigIds().size());
        // 人工批次直接落在「已封」：它不参与「当日同场白条并入」这套自动归集，设备也不去动它。
        // 状态列只描述归集是否还开着，人工批次本就不该是开着的。
        entity.setStatus(BATCH_SEALED);
        entity.setSource(SOURCE_MANUAL);
        // 人工批次把录入人落到来源上，与设备归集（分拣台编号）共用同一列，一眼能分清数据是怎么来的
        entity.setSourceRef(operator.display());
        entity.setCreateTime(LocalDateTime.now());
        if (StringUtils.hasText(request.batchNo())) {
            entity.setBatchNo(request.batchNo());
            carcassBatchMapper.insert(entity);
        } else {
            insertWithGeneratedNo(entity);
        }
        linkPigs(entity.getId(), request.pigIds());
        // 建批动作上链：批次是分割与冷链的起点，链上必须能看到「这一批是怎么来的、谁建的」。
        // （原先人工建批没有上链记录，前端却提示「数据已上链」——设备归集那条路径有链、人工这条没有。）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", entity.getBatchNo());
        payload.put("pigIds", request.pigIds());
        payload.put("slaughterhouse", entity.getSlaughterhouse());
        payload.put("totalWeightKg", entity.getTotalWeightKg());
        payload.put("action", "MANUAL_CREATE");
        payload.put("source", SOURCE_MANUAL);
        payload.put("operator", operator.display());
        publish("CARCASS_BATCH", entity.getId(), entity.getBatchNo() + ":MANUAL_CREATE",
                HashUtil.sha256Json(payload), payload);
        // 胴体批次创建意味着生猪已完成屠宰：同步推进生猪档案状态为 3=已屠宰（失败不阻塞业务）
        syncPigStatus(request.pigIds());
        return entity;
    }

    @Override
    public Page<CarcassBatch> pageBatches(String batchNo, String operator, LocalDate startDate, LocalDate endDate,
                                          long pageNum, long pageSize) {
        Page<CarcassBatch> page = carcassBatchMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<CarcassBatch>lambdaQuery()
                        .like(StringUtils.hasText(batchNo), CarcassBatch::getBatchNo, batchNo)
                        .like(StringUtils.hasText(operator), CarcassBatch::getOperator, operator)
                        // 创建日期范围：[开始日期 00:00:00, 结束日期次日 00:00:00)，结束日当天包含在内
                        .ge(startDate != null, CarcassBatch::getCreateTime,
                                startDate == null ? null : startDate.atStartOfDay())
                        .lt(endDate != null, CarcassBatch::getCreateTime,
                                endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                        .orderByDesc(CarcassBatch::getCreateTime));
        fillSplitCount(page.getRecords());
        return page;
    }

    /**
     * 给列表补「该批次已分割条数」。一次分组查询即可，不做 N+1；
     * 分割批次总量是几十条量级，直接按页内批次 id 过滤后统计。
     */
    private void fillSplitCount(List<CarcassBatch> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(CarcassBatch::getId).toList();
        Map<Long, Integer> counts = new HashMap<>();
        for (SplitBatch row : splitBatchMapper.selectList(Wrappers.<SplitBatch>lambdaQuery()
                .select(SplitBatch::getParentBatchId)
                .in(SplitBatch::getParentBatchId, ids))) {
            if (row.getParentBatchId() != null) {
                counts.merge(row.getParentBatchId(), 1, Integer::sum);
            }
        }
        records.forEach(r -> r.setSplitCount(counts.getOrDefault(r.getId(), 0)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBatch(Long id, DistributionRequests.BatchCreate request) {
        CarcassBatch entity = require(carcassBatchMapper.selectById(id), "胴体批次不存在");
        sourceGuard.requireManualAllowed(TraceStage.BATCH, "变更胴体批次");
        List<Long> oldPigs = entity.getPigIds() == null ? List.of() : entity.getPigIds();
        List<Long> newPigs = request.pigIds() == null ? List.of() : request.pigIds();

        // 设备自动归集的批次只开放备注（与分割批次同一口径）：关联生猪、屠宰场、总重量都是分拣台
        // 按实际过磅归出来的结果，允许改等于把「自动归集」改回「人工分组」；改了屠宰场更麻烦——
        // 后续设备上报会按新屠宰场再归一批，同一具白条就落在两个批次里。
        if (SOURCE_DEVICE.equals(entity.getSource())) {
            rejectDeviceBatchEdit(entity, request, oldPigs, newPigs);
            entity.setNote(request.note());
            carcassBatchMapper.updateById(entity);
            return;
        }

        // 批次号为溯源标识不可修改；仅当关联生猪集合发生变化时做业务校验
        if (!new HashSet<>(oldPigs).equals(new HashSet<>(newPigs))) {
            Long splitCount = splitBatchMapper.selectCount(Wrappers.<SplitBatch>lambdaQuery()
                    .eq(SplitBatch::getParentBatchId, id));
            if (splitCount != null && splitCount > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该批次已发生分割，不能变更关联生猪");
            }
            batchGuard.validateAll(newPigs, id);
        }
        entity.setPigIds(request.pigIds());
        entity.setPigCount(request.pigIds() == null ? 0 : request.pigIds().size());
        entity.setTotalWeightKg(request.totalWeightKg());
        entity.setSlaughterhouse(request.slaughterhouse());
        entity.setOperator(request.operator());
        entity.setNote(request.note());
        carcassBatchMapper.updateById(entity);
        syncLinks(id, newPigs);
        // 新加入批次的生猪同样需要推进为已屠宰
        syncPigStatus(newPigs);
    }

    /**
     * 封批：此后分拣台不再把当日白条并入本批次，下一头另开新批。
     * <p>
     * 达到头数上限会自动封批，但上限兜不住「换班」「换圈」这类中断点——
     * 夜班结束时批里可能只有几十头，剩下的位置不该被后班的白条填上，
     * 所以必须留一个人工封批的口子。封批是设备优先环节上的人工动作，强制实名并上链留痕。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sealBatch(Long id) {
        CarcassBatch entity = require(carcassBatchMapper.selectById(id), "胴体批次不存在");
        ManualOperator operator = sourceGuard.requireManualAllowed(TraceStage.BATCH, "封批胴体批次");
        if (carcassBatchMapper.sealById(id) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该批次已是封批状态，无需重复封批");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", entity.getBatchNo());
        payload.put("action", "MANUAL_SEAL");
        payload.put("pigCount", entity.getPigCount());
        payload.put("originalSource", entity.getSource());
        payload.put("operator", operator.display());
        publish("CARCASS_BATCH", id, entity.getBatchNo() + ":MANUAL_SEAL",
                HashUtil.sha256Json(payload), payload);
    }

    /** 生成批号并插入；序号被并发抢走就换下一个重试 */
    private void insertWithGeneratedNo(CarcassBatch entity) {
        LocalDate today = LocalDate.now();
        for (int skip = 0; skip < 10; skip++) {
            String candidate = carcassBatchNo.next(today, skip);
            if (carcassBatchNo.taken(candidate)) continue;
            entity.setBatchNo(candidate);
            try {
                carcassBatchMapper.insert(entity);
                return;
            } catch (DuplicateKeyException e) {
                // 并发下另一个请求抢了同一个序号，换一个再试
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "生成胴体批次号失败，请稍后重试");
    }

    /** 写关联行（「一头猪只归一批」的最终防线是这张表上的唯一键） */
    private void linkPigs(Long batchId, List<Long> pigIds) {
        if (pigIds == null) return;
        LocalDateTime now = LocalDateTime.now();
        for (Long pigId : pigIds.stream().filter(Objects::nonNull).distinct().toList()) {
            CarcassBatchPig link = new CarcassBatchPig();
            link.setBatchId(batchId);
            link.setPigId(pigId);
            link.setCreateTime(now);
            try {
                carcassBatchPigMapper.insert(link);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "生猪ID" + pigId + " 已被其他批次占用，一头猪只能归属一个批次（请刷新后重试）");
            }
        }
    }

    /** 人工改批时同步关联表：本批次不再包含的删掉、新加入的补上，别让关联表与 pig_ids 对不上 */
    private void syncLinks(Long batchId, List<Long> newPigs) {
        List<Long> keep = newPigs.stream().filter(Objects::nonNull).distinct().toList();
        if (keep.isEmpty()) {
            carcassBatchPigMapper.delete(Wrappers.<CarcassBatchPig>lambdaQuery()
                    .eq(CarcassBatchPig::getBatchId, batchId));
        } else {
            carcassBatchPigMapper.delete(Wrappers.<CarcassBatchPig>lambdaQuery()
                    .eq(CarcassBatchPig::getBatchId, batchId)
                    .notIn(CarcassBatchPig::getPigId, keep));
        }
        Set<Long> existing = carcassBatchPigMapper.selectList(Wrappers.<CarcassBatchPig>lambdaQuery()
                        .eq(CarcassBatchPig::getBatchId, batchId))
                .stream().map(CarcassBatchPig::getPigId).collect(java.util.stream.Collectors.toSet());
        linkPigs(batchId, keep.stream().filter(p -> !existing.contains(p)).toList());
    }

    /** 设备归集的批次不允许人工改业务字段（逐字段点名，操作员才知道踩到哪一条；只有备注放行） */
    private void rejectDeviceBatchEdit(CarcassBatch entity, DistributionRequests.BatchCreate request,
                                       List<Long> oldPigs, List<Long> newPigs) {
        List<String> changed = new ArrayList<>();
        if (!new HashSet<>(oldPigs).equals(new HashSet<>(newPigs))) changed.add("关联生猪");
        if (!SourceGuard.sameValue(entity.getTotalWeightKg(), request.totalWeightKg())) changed.add("总重量");
        if (!SourceGuard.sameValue(entity.getSlaughterhouse(), request.slaughterhouse())) changed.add("屠宰场");
        if (!SourceGuard.sameValue(entity.getOperator(), request.operator())) changed.add("操作人");
        if (!changed.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "该批次由分拣台按「屠宰场 + 屠宰日」自动归集，不允许人工修改：" + String.join("、", changed)
                            + "（自动归集的结果只能作废后由设备重推，本批次仅开放备注）");
        }
    }

    /** 将生猪档案状态推进为 3=已屠宰（内部调用，幂等、失败不阻塞） */
    private void syncPigStatus(List<Long> pigIds) {
        if (pigIds == null || pigIds.isEmpty()) return;
        for (Long pigId : pigIds.stream().distinct().toList()) {
            if (pigId != null) breedingClient.advanceStatus(pigId, 3);
        }
    }

    @Override
    public List<Map<String, Object>> getPigOccupancy() {
        // 查关联表（uk_pig 索引命中），不再遍历所有批次的 pig_ids 数组——那是全表扫 + 逐行解析 JSON
        List<CarcassBatchPig> links = carcassBatchPigMapper.selectList(null);
        if (links.isEmpty()) return List.of();
        Map<Long, String> noById = new HashMap<>();
        for (CarcassBatch b : carcassBatchMapper.selectBatchIds(
                links.stream().map(CarcassBatchPig::getBatchId).distinct().toList())) {
            noById.put(b.getId(), b.getBatchNo());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (CarcassBatchPig link : links) {
            String batchNo = noById.get(link.getBatchId());
            // 批次已作废时该猪视为未被占用，不报给前端（否则人工建批会把它当成不可选）
            if (batchNo == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("pigId", link.getPigId());
            m.put("batchId", link.getBatchId());
            m.put("batchNo", batchNo);
            result.add(m);
        }
        return result;
    }

    /* 胴体批次写入前的三道校验（瘦肉精 / 盖章 / 未被占用）已抽到 BatchGuard：
       人工入口与设备自动归集共用同一套判断，避免「设备拦了、手工放过去」。
       判定口径本身仍在屠宰服务（RactopamineGuard / StampGuard），这里只搬运结论。 */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long id) {
        require(carcassBatchMapper.selectById(id), "胴体批次不存在");
        // 存在一级分割时禁止删除，保证批次溯源链不断裂
        Long splitCount = splitBatchMapper.selectCount(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getParentBatchId, id).eq(SplitBatch::getSplitLevel, 1));
        if (splitCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该胴体批次下存在分割批次，无法删除");
        carcassBatchMapper.deleteById(id);
        // 批次作废后这头猪不再被占用、可以重新归批，所以关联行必须一并清掉——
        // 否则 uk_pig 会一直拦着它，进不了任何新批次（建一条「鬼占用」）。
        carcassBatchPigMapper.delete(Wrappers.<CarcassBatchPig>lambdaQuery()
                .eq(CarcassBatchPig::getBatchId, id));
    }

    /* 人工创建分割批次方法已删除：分割批次为设备独占环节，写入只走设备通道
       （DistributionDeviceIngestController /distribution/internal/ingest/split）。 */

    @Override
    public Page<SplitBatch> pageSplits(String keyword, long pageNum, long pageSize) {
        return splitBatchMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<SplitBatch>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w.like(SplitBatch::getBatchNo, keyword)
                        .or().like(SplitBatch::getProductName, keyword))
                .orderByDesc(SplitBatch::getCreateTime));
    }

    @Override
    public SplitBatch getSplit(Long id) {
        return require(splitBatchMapper.selectById(id), "分割批次不存在");
    }

    /**
     * 修改：只开放备注。
     * <p>
     * 品名、重量、包装数量、车间、温度都是批次结论——允许原地改，等于允许把
     * 「这批是后腿」改成「这批是里脊」，下游门店与追溯查询全部跟着错。错只能作废重建。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSplit(Long id, DistributionRequests.SplitUpdate request) {
        SplitBatch entity = require(splitBatchMapper.selectById(id), "分割批次不存在或已作废");

        java.util.List<String> changed = new java.util.ArrayList<>();
        if (!SourceGuard.sameValue(entity.getProductName(), request.productName())) changed.add("产品名称");
        if (!SourceGuard.sameValue(entity.getWeightKg(), request.weightKg())) changed.add("分割重量");
        if (!SourceGuard.sameValue(entity.getPackageCount(), request.packageCount())) changed.add("包装数量");
        if (!SourceGuard.sameValue(entity.getPackageType(), request.packageType())) changed.add("包装方式");
        if (!SourceGuard.sameValue(entity.getWorkshop(), request.workshop())) changed.add("分割车间");
        if (!SourceGuard.sameValue(entity.getWorkshopTemp(), request.workshopTemp())) changed.add("车间温度");
        if (!SourceGuard.sameValue(entity.getOperator(), request.operator())) changed.add("操作人");
        sourceGuard.rejectConclusionChange(TraceStage.SPLIT, changed);

        SplitBatch patch = new SplitBatch();
        patch.setId(id);
        patch.setNote(request.note());
        splitBatchMapper.updateById(patch);

        String editor = sourceGuard.currentOperator().display();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", entity.getBatchNo());
        payload.put("action", "MANUAL_EDIT");
        payload.put("editedFields", "备注");
        payload.put("operator", editor);
        payload.put("originalSource", entity.getSource());
        publish("SPLIT_BATCH_MANUAL_EDIT", id, entity.getBatchNo() + ":EDIT:" + System.currentTimeMillis(),
                HashUtil.sha256Json(payload), payload);
    }

    /**
     * 作废（原「删除」）：不做物理删除，只把 void_flag 翻成 1 并写下理由与操作人，同时上链。
     * <p>
     * 批次号是下游门店与追溯查询的引用键，删掉记录会让所有引用悬空，因此只允许作废。
     * 仍有子分割或已生成运输任务时不允许作废——那会让追溯链断在半途。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidSplit(Long id, String reason) {
        // 作废与新增同级受控：实名 + 理由 + 上链，防止「用作废绕开管控」
        ManualOperator voidBy = sourceGuard.requireVoidAllowed(TraceStage.SPLIT, reason);
        String cleanReason = reason.trim();
        SplitBatch split = require(splitBatchMapper.selectById(id), "分割批次不存在或已作废");
        Long childCount = splitBatchMapper.selectCount(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getParentBatchId, id)
                .eq(split.getSplitLevel() != null, SplitBatch::getSplitLevel, split.getSplitLevel() == null ? null : split.getSplitLevel() + 1));
        if (childCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该分割批次下存在子分割，无法作废");
        Long transportCount = transportMapper.selectCount(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getSplitBatchId, id));
        if (transportCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该分割批次已关联冷链运输任务，无法作废");

        String operator = voidBy.display();
        if (splitBatchMapper.voidById(id, cleanReason, operator) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作废失败：批次可能已被他人作废");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", split.getBatchNo());
        payload.put("action", "VOID");
        payload.put("voidReason", cleanReason);
        payload.put("operator", operator);
        payload.put("originalSource", split.getSource());
        Map<String, Object> hashSource = new LinkedHashMap<>(payload);
        hashSource.put("voidTime", LocalDateTime.now().toString());
        publish("SPLIT_BATCH_VOID", id, split.getBatchNo() + ":VOID", HashUtil.sha256Json(hashSource), payload);
    }

    @Override
    public Map<String, Object> getSplitTree(String batchNo) {
        CarcassBatch carcass = carcassBatchMapper.selectOne(Wrappers.<CarcassBatch>lambdaQuery().eq(CarcassBatch::getBatchNo, batchNo));
        if (carcass != null) {
            return Map.of("type", "CARCASS", "data", carcass,
                    "children", carcassSplitChildren(carcass.getId()));
        }
        SplitBatch split = splitBatchMapper.selectOne(Wrappers.<SplitBatch>lambdaQuery().eq(SplitBatch::getBatchNo, batchNo));
        if (split == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "批次不存在");
        return splitNode(split, new HashSet<>());
    }

    /**
     * 胴体批次下的子节点：先按「分割作业」分组，作业节点下面再挂各自的部位。
     * <p>
     * 这样「这头白条切成了什么」才是一份台账而不是一堆散件；出肉率与对账结论也挂在作业上。
     * 字典与作业建立之前写入的历史记录（{@code job_id} 为空）没有作业可归，仍然平铺在
     * 胴体批次下——不硬塞进某个作业里，那是编造事实。
     */
    private List<Map<String, Object>> carcassSplitChildren(Long carcassBatchId) {
        List<Map<String, Object>> children = new ArrayList<>();
        Set<Long> grouped = new HashSet<>();

        List<SplitJob> jobs = splitJobMapper.selectList(Wrappers.<SplitJob>lambdaQuery()
                .eq(SplitJob::getParentBatchId, carcassBatchId)
                .orderByAsc(SplitJob::getId));
        for (SplitJob job : jobs) {
            List<SplitBatch> items = splitBatchMapper.selectList(Wrappers.<SplitBatch>lambdaQuery()
                    .eq(SplitBatch::getJobId, job.getId())
                    .orderByAsc(SplitBatch::getId));
            if (items.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> itemNodes = new ArrayList<>();
            Set<String> reported = new HashSet<>();
            for (SplitBatch item : items) {
                itemNodes.add(splitNode(item, new HashSet<>()));
                grouped.add(item.getId());
                if (item.getPartCode() != null) {
                    reported.add(item.getPartCode());
                }
            }
            // 再补一条「未产出标准部位」对照：字典是固定的，一头猪按标准该切出哪些部位也是固定的，
            // 但只有实报的才算事实。两者一对照，「缺了哪块肉」在树上直接看得见，复核时不用再翻字典。
            // 它用独立节点类型渲染、不混进实产部位里——对照是提示，不是数据。
            List<MeatPart> missingParts = splitYieldCheck.missingStandardParts(reported);
            if (!missingParts.isEmpty()) {
                itemNodes.add(missingPartsNode(job, missingParts, "JOB", job.getJobNo()));
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", "SPLIT_JOB");
            node.put("data", job);
            node.put("children", itemNodes);
            children.add(node);
        }

        List<SplitBatch> orphans = splitBatchMapper.selectList(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getParentBatchId, carcassBatchId)
                .eq(SplitBatch::getSplitLevel, 1)
                .isNull(SplitBatch::getJobId)
                .orderByAsc(SplitBatch::getId));
        boolean hasOrphan = false;
        for (SplitBatch orphan : orphans) {
            if (!grouped.contains(orphan.getId())) {
                children.add(splitNode(orphan, new HashSet<>()));
                hasOrphan = true;
            }
        }
        // 字典与作业建立之前写入的历史记录没有作业可归，也就没法按「一头白条」做对照。但它们的
        // 部位编码已经按字典回填过，按「本批次报到过哪些标准部位」做一次批次级对照仍然成立——
        // 而这正是老数据唯一能看到的「完整结构」。措辞与作业级对照分开，不假装是逐头口径。
        if (hasOrphan) {
            Set<String> reportedCodes = new HashSet<>();
            collectPartCodes(carcassBatchId, 0, reportedCodes, 0);
            List<MeatPart> missingParts = splitYieldCheck.missingStandardParts(reportedCodes);
            if (!missingParts.isEmpty()) {
                children.add(missingPartsNode(null, missingParts, "BATCH", "batch:" + carcassBatchId));
            }
        }
        return children;
    }

    /**
     * 递归收集某节点下所有分割记录的部位编码（历史记录可能有多层，如「二分体 → 前腿肉」）。
     * <p>
     * 必须带层级条件：{@code parent_batch_id} 同时被胴体批次与分割批次使用，两个 id 空间会撞号，
     * 光按 id 查会把无关记录混进来（{@link #children} 同一处口径）。
     */
    private void collectPartCodes(Long parentId, int parentLevel, Set<String> out, int depth) {
        if (depth > 5) {
            return;
        }
        List<SplitBatch> rows = splitBatchMapper.selectList(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getParentBatchId, parentId)
                .eq(SplitBatch::getSplitLevel, parentLevel + 1)
                .orderByAsc(SplitBatch::getId));
        for (SplitBatch row : rows) {
            if (row.getPartCode() != null) {
                out.add(row.getPartCode());
            }
            collectPartCodes(row.getId(), row.getSplitLevel(), out, depth + 1);
        }
    }

    /**
     * 「未产出标准部位」对照节点：挂在分割作业（或胴体批次，针对无作业的历史记录）下，默认可展开。
     * <p>
     * 叶子不带重量、不带操作人——它们没有发生，只是一份对照清单；因此用 {@code MISSING_PART}
     * 独立类型，前端据此与实产部位用不同样式渲染，不会让人误以为真有这块肉。
     * {@code key} 供前端做稳定标识（伪节点没有库表 id）；{@code scope} 区分逐头口径与批次口径。
     */
    private Map<String, Object> missingPartsNode(SplitJob job, List<MeatPart> missingParts,
                                                 String scope, String ownerKey) {
        String keyPrefix = "MISS:" + ownerKey;
        long requiredCount = missingParts.stream()
                .filter(p -> p.getRequired() != null && p.getRequired() == 1)
                .count();
        List<Map<String, Object>> leaves = new ArrayList<>();
        for (MeatPart part : missingParts) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("key", keyPrefix + ":" + part.getPartCode());
            data.put("partCode", part.getPartCode());
            data.put("partName", part.getPartName());
            data.put("segment", part.getSegment());
            data.put("category", part.getCategory());
            data.put("required", part.getRequired());
            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("type", "MISSING_PART");
            leaf.put("data", data);
            leaf.put("children", List.of());
            leaves.add(leaf);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", keyPrefix);
        data.put("scope", scope);
        if (job != null) {
            data.put("jobNo", job.getJobNo());
        }
        data.put("count", missingParts.size());
        data.put("requiredCount", requiredCount);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "MISSING_PART_GROUP");
        node.put("data", data);
        node.put("children", leaves);
        return node;
    }

    /** 部位字典（启用中的），供演示上报与前端选择用 */
    @Override
    public List<MeatPart> listMeatParts() {
        return meatPartMapper.selectList(Wrappers.<MeatPart>lambdaQuery()
                .eq(MeatPart::getEnabled, 1)
                .orderByAsc(MeatPart::getSortNo));
    }

    /** 分割作业分页：作业是「一头白条的产出清单」这一层的检索入口 */
    @Override
    public Page<SplitJob> pageSplitJobs(String keyword, long pageNum, long pageSize) {
        return splitJobMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<SplitJob>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w.like(SplitJob::getJobNo, keyword)
                        .or().like(SplitJob::getParentBatchNo, keyword))
                .orderByDesc(SplitJob::getCreateTime));
    }

    private List<Map<String, Object>> children(Long parentId, int parentLevel, Set<String> visited) {
        String visitKey = parentLevel + ":" + parentId;
        if (!visited.add(visitKey))
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "检测到批次循环引用");
        List<SplitBatch> rows = splitBatchMapper.selectList(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getParentBatchId, parentId)
                .eq(SplitBatch::getSplitLevel, parentLevel + 1)
                .orderByAsc(SplitBatch::getId));
        List<Map<String, Object>> result = rows.stream().map(row -> splitNode(row, new HashSet<>(visited))).toList();
        visited.remove(visitKey);
        return result;
    }

    private Map<String, Object> splitNode(SplitBatch split, Set<String> visited) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "SPLIT");
        node.put("data", split);
        node.put("children", children(split.getId(), split.getSplitLevel(), visited));
        return node;
    }

    @Override
    public List<Map<String, Object>> recentSnapshots(String deviceNo, String batchNo, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        List<WorkshopSnapshot> rows = snapshotMapper.selectList(Wrappers.<WorkshopSnapshot>lambdaQuery()
                .eq(StringUtils.hasText(deviceNo), WorkshopSnapshot::getDeviceNo, deviceNo)
                // 按批次过滤：分割操作页看的是「这次分割的现场照片」，不是「这台相机最近的乱序照片」。
                // 不传 batchNo 时不过滤（保留「按相机看最近若干张」的旧口径，兼容未绑定的历史数据）。
                .eq(StringUtils.hasText(batchNo), WorkshopSnapshot::getBatchNo, batchNo)
                .orderByDesc(WorkshopSnapshot::getId)
                .last("LIMIT " + size));

        // 作业号一次批量查出来（同一批次的三张照片指向同一个作业，逐个查会打出 N 次同样的 SQL）
        Map<Long, String> jobNoById = rows.stream()
                .map(WorkshopSnapshot::getJobId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toMap(id -> id, id -> {
                    SplitJob job = splitJobMapper.selectById(id);
                    return job == null || job.getJobNo() == null ? "" : job.getJobNo();
                }));

        return rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("deviceNo", row.getDeviceNo());
            item.put("workshop", row.getWorkshop());
            item.put("batchNo", row.getBatchNo());
            item.put("jobId", row.getJobId());
            item.put("jobNo", row.getJobId() == null ? null : jobNoById.get(row.getJobId()));
            item.put("fileId", row.getFileId());
            item.put("capturedTime", row.getCapturedTime());
            item.put("createTime", row.getCreateTime());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> upstream(Long splitId) {
        List<Map<String, Object>> path = new ArrayList<>();
        SplitBatch current = require(splitBatchMapper.selectById(splitId), "分割批次不存在");
        Set<Long> visited = new HashSet<>();
        while (current != null) {
            if (!visited.add(current.getId())) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "检测到批次循环引用");
            path.add(Map.of("type", "SPLIT", "data", current));
            Long parentId = current.getParentBatchId();
            if (current.getSplitLevel() > 1) {
                SplitBatch parentSplit = splitBatchMapper.selectById(parentId);
                if (parentSplit == null || parentSplit.getSplitLevel() != current.getSplitLevel() - 1)
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "分割批次父级关系无效");
                current = parentSplit;
            } else {
                CarcassBatch carcass = carcassBatchMapper.selectById(parentId);
                if (carcass == null) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "胴体父批次不存在");
                path.add(Map.of("type", "CARCASS", "data", carcass));
                break;
            }
        }
        return path;
    }

    @Override
    public Map<String, Object> downstream(String batchNo) {
        Map<String, Object> tree = getSplitTree(batchNo);
        List<Long> splitIds = new ArrayList<>();
        collectSplitIds(tree, splitIds);
        List<ColdChainTransport> transports = splitIds.isEmpty() ? List.of()
                : transportMapper.selectList(Wrappers.<ColdChainTransport>lambdaQuery()
                        .in(ColdChainTransport::getSplitBatchId, splitIds)
                        .orderByAsc(ColdChainTransport::getCreateTime));
        List<Map<String, Object>> logistics = transports.stream().map(transport -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("transport", transport);
            item.put("temperatureLogs", temperatureLogMapper.selectList(Wrappers.<TemperatureLog>lambdaQuery()
                    .eq(TemperatureLog::getTransportId, transport.getId())
                    .orderByAsc(TemperatureLog::getRecordTime)));
            item.put("receipt", receiptMapper.selectOne(Wrappers.<StoreReceipt>lambdaQuery()
                    .eq(StoreReceipt::getTransportId, transport.getId()).last("LIMIT 1")));
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tree", tree);
        result.put("splitIds", splitIds);
        result.put("logistics", logistics);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ColdChainTransport createTransport(DistributionRequests.TransportCreate request) {
        SplitBatch batch = require(splitBatchMapper.selectById(request.splitBatchId()), "分割批次不存在");
        // 人工建单也要挡两道，口径与发货计划一致（loadPlanBatches）：
        // ① 已被别的「待发货/部分排车」计划的未排车明细占用 —— 两套单据抢同一批货，装车时必然打架；
        // ② 已经由某张运单发运过 —— 同一批货发两次是实质业务事故，不是"重发一次而已"。
        List<DeliveryPlanItem> taken = deliveryPlanItemMapper.selectList(Wrappers.<DeliveryPlanItem>lambdaQuery()
                .eq(DeliveryPlanItem::getSplitBatchId, request.splitBatchId())
                .eq(DeliveryPlanItem::getShipped, 0));
        for (DeliveryPlanItem item : taken) {
            DeliveryPlan other = deliveryPlanMapper.selectById(item.getPlanId());
            if (other != null && (Integer.valueOf(PLAN_PENDING).equals(other.getStatus())
                    || Integer.valueOf(PLAN_PARTIAL).equals(other.getStatus()))) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "分割批次 " + batch.getBatchNo() + " 已被待发货计划 " + other.getPlanNo()
                                + "（" + other.getStoreName() + "）占用，请改用该计划发运");
            }
        }
        Long shippedCount = transportItemMapper.selectCount(Wrappers.<TransportItem>lambdaQuery()
                .eq(TransportItem::getSplitBatchId, request.splitBatchId()));
        if (shippedCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "分割批次 " + batch.getBatchNo() + " 已由运单发运过，不能重复发货");
        }
        ColdChainTransport entity = new ColdChainTransport();
        BeanUtils.copyProperties(request, entity);
        entity.setTransportNo(StringUtils.hasText(request.transportNo()) ? request.transportNo() : BusinessNoGenerator.next("TR"));
        entity.setStatus(TRANSPORT_PENDING);
        // 来源标记：人工建的运单与计划生成、装车口生成的必须能分辨，否则出了发错店的事故追不到路径
        entity.setSource(SOURCE_MANUAL);
        // 司机是调度员填进去的，不是司机本人刷的 —— 来源记人工录入、不算本人确认。
        // 「人名非空」与「本人确认过」是两回事，追责时看的就是这两个字段。
        // 没填司机时来源留空：一个空名字配着「人工录入」的来源，只会让人以为有人录过
        //（真实情况是「等发车刷卡确认」，见 depart 那颗闸门）。
        entity.setDriverSource(StringUtils.hasText(entity.getDriverName()) ? DriverSource.MANUAL : null);
        entity.setDriverConfirmed(0);
        entity.setCreateTime(LocalDateTime.now());
        transportMapper.insert(entity);
        // 手工建的运单也记装载明细：否则「待发运」查询会把这批货当成从没发过，
        // 建计划时又能勾选一次，出现同一批货两张运单。
        linkTransportItems(entity.getId(), request.splitBatchId(), entity.getDestination());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTransport(Long id, DistributionRequests.TransportUpdate request) {
        ColdChainTransport entity = require(transportMapper.selectById(id), "运输任务不存在");
        require(splitBatchMapper.selectById(request.splitBatchId()), "分割批次不存在");
        // 已发车后货物已在途，不允许再更换分割批次
        if (!Objects.equals(entity.getSplitBatchId(), request.splitBatchId())
                && !Objects.equals(entity.getStatus(), 1)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "运单已发车，不能变更关联分割批次");
        }
        // 仅覆盖业务字段，运单号/状态/实际发车与到达时间/创建时间保持不变
        entity.setSplitBatchId(request.splitBatchId());
        entity.setVehicleNo(request.vehicleNo());
        entity.setVehicleType(request.vehicleType());
        entity.setRefrigeration(request.refrigeration());
        // 人工把司机姓名改掉了 ⇒ 这个新名字是手工录入的，不能再沿用原来的「刷卡确认」；
        // 只改电话或别的字段则不动来源（那是同一个人，确认强度没变）。
        // 注意必须在覆盖之前取旧值 —— 先赋值再比较永远相等。
        boolean driverChanged = !Objects.equals(entity.getDriverName(), request.driverName());
        entity.setDriverName(request.driverName());
        entity.setDriverPhone(request.driverPhone());
        // 被清空成「没有司机」时：来源也必须跟着空 ——「有来源却没名字」等于假装有人录过，
        // 比两个都空更糟。updateById 会跳过 null 字段，所以清空要在下面显式补一次。
        boolean driverCleared = false;
        if (driverChanged) {
            if (StringUtils.hasText(entity.getDriverName())) {
                entity.setDriverSource(DriverSource.MANUAL);
            } else {
                entity.setDriverSource(null);
                driverCleared = true;
            }
            entity.setDriverConfirmed(0);
        }
        entity.setOrigin(request.origin());
        entity.setDestination(request.destination());
        entity.setPlannedDepart(request.plannedDepart());
        entity.setPlannedArrive(request.plannedArrive());
        transportMapper.updateById(entity);
        if (driverCleared) {
            // updateById 忽略 null：清空司机姓名与来源必须用条件更新显式写一次
            transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                    .eq(ColdChainTransport::getId, entity.getId())
                    .set(ColdChainTransport::getDriverName, null)
                    .set(ColdChainTransport::getDriverSource, null));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTransport(Long id) {
        ColdChainTransport entity = require(transportMapper.selectById(id), "运输任务不存在");
        // 已产生门店签收的运单属于溯源链下游凭证，禁止删除
        Long receiptCount = receiptMapper.selectCount(Wrappers.<StoreReceipt>lambdaQuery()
                .eq(StoreReceipt::getTransportId, id));
        if (receiptCount > 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该运单已生成门店签收记录，无法删除");
        temperatureLogMapper.delete(Wrappers.<TemperatureLog>lambdaQuery().eq(TemperatureLog::getTransportId, id));
        // 由计划生成的运单被删 ⇒ 它拉走的那几批货退回「待排车」，计划状态按剩余量重算。
        // 必须在删除装载明细之前做：明细一删，就再也说不清这张运单到底拉走了哪几批货。
        releasePlanItems(id);
        // 装载明细随运单一起消失：这次发运没发生，货物自然回到「待发运」，
        // 否则那批货会永远卡在"发过又没发"的状态里
        transportItemMapper.delete(Wrappers.<TransportItem>lambdaQuery().eq(TransportItem::getTransportId, id));
        // 站点行程台账也是这张运单的从属数据：运单没了它就没有归属，留着只会造出孤儿站点
        transportStopMapper.delete(Wrappers.<TransportStop>lambdaQuery().eq(TransportStop::getTransportId, id));
        transportMapper.deleteById(id);
    }

    /**
     * 运单被删除时，把它装载的计划明细退回「待排车」。
     * <p>
     * 退回只认 {@code delivery_plan_item.transport_id = 本运单} 的行 —— 不能只按批次 id 去找：
     * 同一个批次可能还出现在一张已取消的旧计划里（那些行本来就没搭过车），顺手改动就是篡改台账。
     */
    private void releasePlanItems(Long transportId) {
        List<TransportItem> linked = transportItemMapper.selectList(Wrappers.<TransportItem>lambdaQuery()
                .eq(TransportItem::getTransportId, transportId));
        if (linked.isEmpty()) return;
        List<DeliveryPlanItem> items = deliveryPlanItemMapper.selectList(Wrappers.<DeliveryPlanItem>lambdaQuery()
                .in(DeliveryPlanItem::getSplitBatchId, linked.stream().map(TransportItem::getSplitBatchId).toList())
                .eq(DeliveryPlanItem::getTransportId, transportId));
        if (items.isEmpty()) return;
        // 必须用 lambdaUpdate 显式 set：MyBatis-Plus 的 updateById 默认忽略 null 字段，
        // 用实体写回会留下上一张运单的 id 和时刻，计划会一直显示一张已经不存在的运单。
        deliveryPlanItemMapper.update(null, Wrappers.<DeliveryPlanItem>lambdaUpdate()
                .eq(DeliveryPlanItem::getTransportId, transportId)
                .set(DeliveryPlanItem::getShipped, 0)
                .set(DeliveryPlanItem::getTransportId, null)
                .set(DeliveryPlanItem::getShippedTime, null));
        items.stream().map(DeliveryPlanItem::getPlanId).distinct().forEach(this::recomputePlanStatus);
    }

    /**
     * 按明细的实际发运情况重算计划状态（删运单后调用）。
     * <p>
     * 分车之后「计划状态」不再是二值，必须回算而不能写死：发走一部分是部分排车，
     * 全排完是已排车，一张运单都不剩了就退回待发货。已取消的计划不动 —— 取消是人的决定，
     * 不因为删了一张运单就复活。
     */
    private void recomputePlanStatus(Long planId) {
        DeliveryPlan plan = deliveryPlanMapper.selectById(planId);
        if (plan == null || Integer.valueOf(PLAN_CANCELLED).equals(plan.getStatus())) return;
        List<DeliveryPlanItem> items = planItemsOf(planId);
        List<DeliveryPlanItem> shippedItems = items.stream().filter(this::isShipped).toList();
        if (shippedItems.isEmpty()) {
            deliveryPlanMapper.update(null, Wrappers.<DeliveryPlan>lambdaUpdate()
                    .eq(DeliveryPlan::getId, planId)
                    .set(DeliveryPlan::getStatus, PLAN_PENDING)
                    .set(DeliveryPlan::getTransportId, null)
                    .set(DeliveryPlan::getTransportNo, null)
                    .set(DeliveryPlan::getTransportCount, 0)
                    .set(DeliveryPlan::getGeneratedTime, null));
            return;
        }
        // 还有货发出去过：最近一张运单按 id 最大取（运单号递增，近似"最后生成的那张"）
        List<Long> remainingIds = shippedItems.stream().map(DeliveryPlanItem::getTransportId)
                .filter(Objects::nonNull).distinct().toList();
        Long lastId = remainingIds.stream().max(Long::compare).orElse(null);
        ColdChainTransport last = lastId == null ? null : transportMapper.selectById(lastId);
        deliveryPlanMapper.update(null, Wrappers.<DeliveryPlan>lambdaUpdate()
                .eq(DeliveryPlan::getId, planId)
                .set(DeliveryPlan::getStatus,
                        shippedItems.size() == items.size() ? PLAN_GENERATED : PLAN_PARTIAL)
                .set(DeliveryPlan::getTransportId, last == null ? null : last.getId())
                .set(DeliveryPlan::getTransportNo, last == null ? null : last.getTransportNo())
                .set(DeliveryPlan::getTransportCount, remainingIds.size())
                .set(DeliveryPlan::getGeneratedTime, last == null ? null : last.getCreateTime()));
    }

    @Override
    public Page<ColdChainTransport> pageTransports(Integer status, String keyword, long pageNum, long pageSize) {
        Page<ColdChainTransport> page = transportMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<ColdChainTransport>lambdaQuery()
                        .eq(status != null, ColdChainTransport::getStatus, status)
                        .and(StringUtils.hasText(keyword), w -> w.like(ColdChainTransport::getTransportNo, keyword)
                                .or().like(ColdChainTransport::getVehicleNo, keyword)
                                .or().like(ColdChainTransport::getDriverName, keyword))
                        .orderByDesc(ColdChainTransport::getCreateTime));
        fillStopProgress(page.getRecords());
        return page;
    }

    /**
     * 给一页运单补「已到达 / 已签收站点数」。
     * <p>
     * <b>单站运单也要补</b>：站点级到达如果只在多站运单上才有落点，使用者就完全看不出
     * 「到达是按卸货门店逐站记的」——名单上每张单都写着「单站」，等于没说。
     * 所以列表的站点进度与详情信息条都读这两个值。
     * <p>
     * <b>一次分组查询取全页</b>：绝不按行查（一页 10 行就是 10 次 N+1）。
     */
    private void fillStopProgress(List<ColdChainTransport> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> ids = records.stream().map(ColdChainTransport::getId).toList();
        Map<Long, List<Integer>> byTransport = transportStopMapper.selectList(Wrappers.<TransportStop>lambdaQuery()
                        .in(TransportStop::getTransportId, ids))
                .stream().collect(Collectors.groupingBy(TransportStop::getTransportId,
                        Collectors.mapping(TransportStop::getStatus, Collectors.toList())));
        for (ColdChainTransport t : records) {
            List<Integer> statuses = byTransport.getOrDefault(t.getId(), List.of());
            t.setArrivedStopCount((int) statuses.stream()
                    .filter(s -> s != null && s >= TransportStop.STOP_ARRIVED).count());
            t.setReceivedStopCount((int) statuses.stream()
                    .filter(s -> s != null && s.equals(TransportStop.STOP_SIGNED)).count());
        }
    }

    @Override
    public ColdChainTransport getTransport(Long id) {
        return require(transportMapper.selectById(id), "运输任务不存在");
    }

    /**
     * 人工发车（设备故障 / 现场无网时的兜底，也是界面上的「立即发车」）。
     * <p>
     * <b>司机是硬前提</b>：货要走了，责任必须落到具体的人。现场只在上车那一刻刷一次卡，
     * 所以装车建单时司机常常是空的（装车口没有读卡器）—— 这里就是那颗闸门：
     * 没有司机不许发车，请先编辑运单补录司机，或让司机在车载终端刷卡
     * （{@code ingestDepart}：两条路的差别只在于后者同时把「本人确认」记下来）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void depart(Long id) {
        ColdChainTransport transport = require(transportMapper.selectById(id), "运输任务不存在");
        if (!Integer.valueOf(TRANSPORT_PENDING).equals(transport.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅待发车任务可以发车");
        }
        if (!StringUtils.hasText(transport.getDriverName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "运单 " + transport.getTransportNo() + " 还没有司机：货要走了，责任必须落到具体的人。"
                            + "请先编辑运单补录司机，或由司机在车载终端刷卡发车");
        }
        int changed = transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                .eq(ColdChainTransport::getId, id).eq(ColdChainTransport::getStatus, TRANSPORT_PENDING)
                .set(ColdChainTransport::getStatus, TRANSPORT_IN_TRANSIT)
                .set(ColdChainTransport::getDepartTime, LocalDateTime.now()));
        if (changed == 0) throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅待发车任务可以发车");
    }

    /* 人工补录温度方法已删除：冷链温度为设备独占环节，写入只走设备通道
       （DistributionDeviceIngestController /distribution/internal/ingest/temperature）。 */

    @Override
    public List<TemperatureLog> temperatureLogs(Long transportId) {
        getTransport(transportId);
        return temperatureLogMapper.selectList(Wrappers.<TemperatureLog>lambdaQuery()
                .eq(TemperatureLog::getTransportId, transportId).orderByAsc(TemperatureLog::getRecordTime));
    }

    /**
     * 人工确认到达：推进<b>当前第一个未到达的站点</b>。
     * <p>
     * 单站运单点一次就是「全部到达」→ 运单转 3 待签收，与改造前的行为完全一致（存量页面不用改）；
     * 多站运单则一次推进一站（一趟车途经 3 站要点 3 次），运单状态由站点汇总推导。
     * <p>
     * 人工点按钮天然不会跳站 —— 它总是推第一个未到达的站。跳站只可能来自设备上报，
     * 那条路上必须单独拦（见 {@code DistributionDeviceIngestService.ingestArrive}）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> arrive(Long id) {
        ColdChainTransport transport = getTransport(id);
        Integer st = transport.getStatus();
        if (st == null || st < TRANSPORT_IN_TRANSIT) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "运单尚未发车，不能确认到达");
        }
        if (st >= TRANSPORT_SIGNED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该运单已签收完成，无需再确认到达");
        }
        TransportStop next = nextUnarrivedStop(id);
        if (next == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "该运单的所有站点都已到达");
        }
        // 人工确认到达：人不带卡，所以确认人与来源一律留空（不拿运单司机顶替「谁确认的到达」）
        if (markStopArrived(next, null, SOURCE_MANUAL, null, null, null, null) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "站点状态刚被别的操作改过，请刷新后重试");
        }
        int status = recomputeTransportStatus(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transportId", id);
        result.put("stopNo", next.getStopNo());
        result.put("storeName", next.getStoreName());
        result.put("stopCount", stopsOf(id).size());
        result.put("transportStatus", status);
        return result;
    }

    /**
     * 标记某站到达（设备通道与人工共用同一处写入，避免两套口径）。
     *
     * @return 影响行数；0 = 该站刚被别的操作改过（调用方据此判并发）
     */
    private int markStopArrived(TransportStop stop, LocalDateTime arriveTime, String source,
                                String sourceRef, String rawPayload, String driverName, String driverSource) {
        return transportStopMapper.update(null, Wrappers.<TransportStop>lambdaUpdate()
                .eq(TransportStop::getId, stop.getId())
                // 条件更新：只有「未到达」才能推进，天然防并发重复确认
                .eq(TransportStop::getStatus, TransportStop.STOP_NOT_ARRIVED)
                .set(TransportStop::getStatus, TransportStop.STOP_ARRIVED)
                .set(TransportStop::getArriveTime, arriveTime == null ? LocalDateTime.now() : arriveTime)
                .set(TransportStop::getArriveSource, StringUtils.hasText(source) ? source : SOURCE_MANUAL)
                .set(TransportStop::getArriveSourceRef, sourceRef)
                // 谁确认的到达：司机刷卡换出的人（本人确认）；人工确认到达时为 null
                .set(TransportStop::getArriveDriverName, driverName)
                .set(TransportStop::getArriveDriverSource, driverSource)
                .set(TransportStop::getRawPayload, rawPayload));
    }

    /** 该运单按站点序号升序的全部站点 */
    private List<TransportStop> stopsOf(Long transportId) {
        return transportStopMapper.selectList(Wrappers.<TransportStop>lambdaQuery()
                .eq(TransportStop::getTransportId, transportId)
                .orderByAsc(TransportStop::getStopNo));
    }

    /** 当前第一个未到达的站点（= 这趟车下一站该去哪）；全部到了返回 null */
    private TransportStop nextUnarrivedStop(Long transportId) {
        return transportStopMapper.selectOne(Wrappers.<TransportStop>lambdaQuery()
                .eq(TransportStop::getTransportId, transportId)
                .lt(TransportStop::getStatus, TransportStop.STOP_ARRIVED)
                .orderByAsc(TransportStop::getStopNo).last("LIMIT 1"));
    }

    /**
     * 运单整体状态<b>由站点汇总推导</b> —— 不在运单主表另存一个会和站点打架的布尔值。
     * <p>
     * 全部站点签收 → 4 已签收；全部站点到达 → 3 待签收；否则 → 2 运输中。
     * <b>未发车（status=1）不推导</b>：那时站点本来就全是「未到达」，照推会把待发车误推成运输中。
     * 签收被删掉后调用它即可把运单退回来，不需要另写一套逆向逻辑。
     * <p>
     * 顺带把运单主表的 {@code arrive_time} 同步成<b>末站到达时刻</b> —— 该字段从此只作兼容展示，
     * 真相在站点表（多站时它装不下每一站的到达时间）。
     */
    private int recomputeTransportStatus(Long transportId) {
        ColdChainTransport transport = transportMapper.selectById(transportId);
        if (transport == null) return 0;
        Integer current = transport.getStatus();
        if (current == null || current < TRANSPORT_IN_TRANSIT) return current == null ? 0 : current;
        List<TransportStop> stops = stopsOf(transportId);
        if (stops.isEmpty()) return current;

        boolean allArrived = stops.stream().allMatch(s -> nz(s.getStatus()) >= TransportStop.STOP_ARRIVED);
        boolean allSigned = stops.stream().allMatch(s -> nz(s.getStatus()) >= TransportStop.STOP_SIGNED);
        int target = allSigned ? TRANSPORT_SIGNED : (allArrived ? TRANSPORT_ARRIVED : TRANSPORT_IN_TRANSIT);
        if (target != current) {
            transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                    .eq(ColdChainTransport::getId, transportId)
                    .set(ColdChainTransport::getStatus, target));
        }
        if (target >= TRANSPORT_ARRIVED) {
            LocalDateTime lastArrive = stops.stream().map(TransportStop::getArriveTime)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            if (lastArrive != null && !lastArrive.equals(transport.getArriveTime())) {
                transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                        .eq(ColdChainTransport::getId, transportId)
                        .set(ColdChainTransport::getArriveTime, lastArrive));
            }
        }
        return target;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 按「运单 + 门店」定位站点。三档匹配，逐档放宽：
     * <ol>
     *   <li>门店 id 相等（权威，新运单都走这档）；</li>
     *   <li>门店名相等（人工建单的老运单没有门店 id）；</li>
     *   <li>该运单只有一个站点（签收凭证没有别的去处 —— 存量种子数据里
     *       {@code transport_item.store_name} 与 {@code store_receipt.store_name} 本就对不上，
     *       单站时按唯一性配对不会认错人；多站绝不走这档）。</li>
     * </ol>
     * 都没匹配上返回 null，由调用方决定是否退回运单级兜底。
     */
    private TransportStop matchStop(Long transportId, Long storeId, String storeName) {
        List<TransportStop> stops = stopsOf(transportId);
        if (stops.isEmpty()) return null;
        if (storeId != null) {
            for (TransportStop stop : stops) {
                if (storeId.equals(stop.getStoreId())) return stop;
            }
        }
        if (StringUtils.hasText(storeName)) {
            for (TransportStop stop : stops) {
                if (storeName.equals(stop.getStoreName())) return stop;
            }
        }
        return stops.size() == 1 ? stops.get(0) : null;
    }

    @Override
    public List<TransportStop> stopsOfTransport(Long transportId) {
        return stopsOf(transportId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markStopArrived(Long stopId, LocalDateTime arriveTime, String source,
                               String sourceRef, String rawPayload, String driverName, String driverSource) {
        TransportStop stop = transportStopMapper.selectById(stopId);
        if (stop == null) return 0;
        markStopArrived(stop, arriveTime, source, sourceRef, rawPayload, driverName, driverSource);
        return recomputeTransportStatus(stop.getTransportId());
    }

    @Override
    public StoreReceipt getReceipt(Long id) {
        return require(receiptMapper.selectById(id), "签收记录不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreReceipt createReceipt(DistributionRequests.ReceiptCreate request) {
        ColdChainTransport transport = getTransport(request.transportId());
        // 前置校验**下沉到站点**：一车多站时，「车到了第 1 站」不能给第 3 站签收 ——
        // 只看运单级的 status=3 拦不住跨站抢签（车还在路上，第 3 站的货权不该转移）。
        TransportStop stop = matchStop(request.transportId(), request.storeId(), request.storeName());
        if (stop != null) {
            if (nz(stop.getStatus()) < TransportStop.STOP_ARRIVED) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "第 " + stop.getStopNo() + " 站（" + stop.getStoreName()
                                + "）的车还没到，无法签收，请先确认到达");
            }
        } else if (nz(transport.getStatus()) < TRANSPORT_ARRIVED) {
            // 兜底：站点台账缺失（异常情况）时退回运单级校验
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "运输任务尚未到达门店，无法签收");
        }
        // 幂等键是「运单 + 门店」：一个车次送四家店就是四次货权转移，各签各的；
        // 但同一家门店在同一车次上仍只能签一次 —— 放开的是站点数，不是签收次数。
        Long existing = receiptMapper.selectCount(Wrappers.<StoreReceipt>lambdaQuery()
                .eq(StoreReceipt::getTransportId, request.transportId())
                .eq(StoreReceipt::getStoreId, request.storeId()));
        if (existing > 0) throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS, "该运单在本门店已签收");
        StoreReceipt entity = new StoreReceipt();
        BeanUtils.copyProperties(request, entity);
        entity.setReceiptTime(LocalDateTime.now());
        entity.setQtyCheck(request.qtyCheck() == null ? 1 : request.qtyCheck());
        entity.setTempCheck(request.tempCheck() == null ? 1 : request.tempCheck());
        entity.setPackageIntact(request.packageIntact() == null ? 1 : request.packageIntact());
        entity.setCreateTime(LocalDateTime.now());
        entity.setContentHash(HashUtil.sha256Json(request));
        receiptMapper.insert(entity);

        // 站点行程台账：这一站标签收并挂上凭证（签收凭证是站点走到终态的证据）
        if (stop != null) {
            transportStopMapper.update(null, Wrappers.<TransportStop>lambdaUpdate()
                    .eq(TransportStop::getId, stop.getId())
                    .set(TransportStop::getStatus, TransportStop.STOP_SIGNED)
                    .set(TransportStop::getReceiptId, entity.getId())
                    // 异常顺序（先签后到）时补一个到达时刻，保证「已签收 ⇒ 有到达时间」这个不变量
                    .set(TransportStop::getArriveTime,
                            stop.getArriveTime() == null ? entity.getReceiptTime() : stop.getArriveTime()));
        }

        // 状态推进：**所有站点都签完**才算「已签收」。一车四店签了三家仍旧是「待签收」——
        // 剩下一家的货权还没转移，溯源链上不能提前闭环。
        if (!stopsOf(request.transportId()).isEmpty()) {
            // 站点台账齐全时：由站点汇总推导（口径只此一处）
            recomputeTransportStatus(request.transportId());
        } else {
            // 兜底：站点台账缺失时按装载明细里的门店去重数判断（原有口径）
            int stops = (int) Math.max(1, transportItemMapper.selectList(Wrappers.<TransportItem>lambdaQuery()
                            .eq(TransportItem::getTransportId, request.transportId())).stream()
                    .map(TransportItem::getStoreName).filter(StringUtils::hasText).distinct().count());
            long signed = receiptMapper.selectCount(Wrappers.<StoreReceipt>lambdaQuery()
                    .eq(StoreReceipt::getTransportId, request.transportId()));
            if (signed >= stops) {
                transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                        .eq(ColdChainTransport::getId, request.transportId())
                        .eq(ColdChainTransport::getStatus, TRANSPORT_ARRIVED)
                        .set(ColdChainTransport::getStatus, TRANSPORT_SIGNED));
            }
        }
        publish("STORE_RECEIPT", entity.getId(), transport.getTransportNo(), entity.getContentHash(), Map.of("transportNo", transport.getTransportNo()));
        return entity;
    }

    /**
     * 运单的站点清单（一车多目的地的展示口径）。
     * <p>
     * 数据来自站点行程台账 {@code transport_stop}（一站一行，带 未到达/已到达/已签收 与到达时刻）——
     * 那才是「这一站走到哪了」的落点；装载明细只说明装了哪些货，行程状态它装不下。
     * 签收人与签收时刻仍从 {@code store_receipt} 按 receipt_id 取（凭证在那边，两边各管各的）。
     * <p>
     * 站点台账为空（异常/未回填）时退回按装载明细即时聚合，不让页面凭空失去站点信息。
     */
    @Override
    public List<Map<String, Object>> transportStops(Long transportId) {
        getTransport(transportId);
        List<TransportStop> stops = stopsOf(transportId);
        if (stops.isEmpty()) return legacyTransportStops(transportId);

        // 批次明细按站点序号回填：站点号与 transport_item.seq 同源，配得上
        List<TransportItem> items = transportItemMapper.selectList(Wrappers.<TransportItem>lambdaQuery()
                .eq(TransportItem::getTransportId, transportId).orderByAsc(TransportItem::getId));
        Map<Integer, List<String>> batchNosBySeq = new LinkedHashMap<>();
        int maxSeq = items.stream().map(TransportItem::getSeq).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0);
        int next = maxSeq;
        for (TransportItem item : items) {
            int seq = item.getSeq() != null ? item.getSeq() : ++next;
            batchNosBySeq.computeIfAbsent(seq, k -> new ArrayList<>()).add(item.getBatchNo());
        }
        Map<Long, StoreReceipt> receipts = receiptMapper.selectList(Wrappers.<StoreReceipt>lambdaQuery()
                        .eq(StoreReceipt::getTransportId, transportId)).stream()
                .collect(Collectors.toMap(StoreReceipt::getId, r -> r, (a, b) -> a, LinkedHashMap::new));

        List<Map<String, Object>> result = new ArrayList<>(stops.size());
        for (TransportStop stop : stops) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stopNo", stop.getStopNo());
            row.put("seq", stop.getStopNo());
            row.put("storeId", stop.getStoreId());
            row.put("storeName", stop.getStoreName());
            row.put("batchCount", stop.getItemCount() == null ? 0 : stop.getItemCount());
            row.put("weightKg", stop.getWeightKg() == null ? BigDecimal.ZERO : stop.getWeightKg());
            row.put("batchNos", batchNosBySeq.getOrDefault(stop.getStopNo(), new ArrayList<String>()));
            row.put("status", nz(stop.getStatus()));
            row.put("arrived", nz(stop.getStatus()) >= TransportStop.STOP_ARRIVED);
            row.put("received", nz(stop.getStatus()) >= TransportStop.STOP_SIGNED);
            row.put("arriveTime", stop.getArriveTime());
            row.put("arriveSource", stop.getArriveSource());
            row.put("arriveSourceRef", stop.getArriveSourceRef());
            // 谁确认的到达（司机刷卡换出来的人）——一车多站时每站可以不是同一个人
            row.put("arriveDriverName", stop.getArriveDriverName());
            row.put("arriveDriverSource", stop.getArriveDriverSource());
            row.put("receiptId", stop.getReceiptId());
            StoreReceipt receipt = stop.getReceiptId() == null ? null : receipts.get(stop.getReceiptId());
            if (receipt != null) {
                // 顺带把签收信息带上，页面不必再拉一次签收列表
                row.put("receiptTime", receipt.getReceiptTime());
                row.put("receiver", receipt.getReceiver());
            }
            // 第一站就是运单的「主目的地」；多站时运单 destination 已经写成「途经 N 站」
            row.put("primary", result.isEmpty());
            result.add(row);
        }
        return result;
    }

    /**
     * 站点台账缺失时的兜底：按装载明细即时聚合。
     * <p>
     * 只服务「回填漏了 / 异常数据」这种边缘情况，正常路径一律走 {@link #transportStops}。
     * 它缺的是行程状态（未到达/已到达），到达时间只能借签收时间 —— 所以结果里
     * 只有 {@code received}，没有 {@code arrived}。
     */
    private List<Map<String, Object>> legacyTransportStops(Long transportId) {
        List<TransportItem> items = transportItemMapper.selectList(Wrappers.<TransportItem>lambdaQuery()
                .eq(TransportItem::getTransportId, transportId).orderByAsc(TransportItem::getId));
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (TransportItem item : items) {
            // 人工建单没有门店 id，只有名字；用名字兜底成一组，避免整条明细凭空消失
            String key = item.getStoreId() != null ? "id:" + item.getStoreId()
                    : (StringUtils.hasText(item.getStoreName()) ? "name:" + item.getStoreName() : "unknown:" + item.getId());
            Map<String, Object> stop = grouped.computeIfAbsent(key, k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("storeId", item.getStoreId());
                row.put("storeName", item.getStoreName());
                row.put("seq", item.getSeq());
                row.put("batchCount", 0);
                row.put("weightKg", BigDecimal.ZERO);
                row.put("batchNos", new ArrayList<String>());
                row.put("received", false);
                // 站点台账缺失的老运单：谈不上「谁确认到达」（那批数据当时还没有站点级到达）
                row.put("arrived", false);
                row.put("arriveDriverName", null);
                row.put("arriveDriverSource", null);
                return row;
            });
            stop.put("batchCount", ((Number) stop.get("batchCount")).intValue() + 1);
            stop.put("weightKg", ((BigDecimal) stop.get("weightKg")).add(
                    item.getWeightKg() == null ? BigDecimal.ZERO : item.getWeightKg()));
            @SuppressWarnings("unchecked") List<String> nos = (List<String>) stop.get("batchNos");
            nos.add(item.getBatchNo());
            // 站点序号取该站最小的（同一站的明细序号本就相同）
            Integer seq = (Integer) stop.get("seq");
            if (seq == null && item.getSeq() != null) stop.put("seq", item.getSeq());
        }
        // 到达 / 签收状态：按 (运单, 门店) 取签收记录，一站一条
        List<StoreReceipt> receipts = receiptMapper.selectList(Wrappers.<StoreReceipt>lambdaQuery()
                .eq(StoreReceipt::getTransportId, transportId));
        for (StoreReceipt receipt : receipts) {
            for (Map<String, Object> stop : grouped.values()) {
                Object sid = stop.get("storeId");
                boolean sameId = sid != null && Objects.equals(((Number) sid).longValue(), receipt.getStoreId());
                boolean sameName = Objects.equals(stop.get("storeName"), receipt.getStoreName());
                if (sameId || sameName) {
                    stop.put("received", true);
                    stop.put("receiptId", receipt.getId());
                    stop.put("receiptTime", receipt.getReceiptTime());
                    stop.put("receiver", receipt.getReceiver());
                }
            }
        }
        List<Map<String, Object>> stops = new ArrayList<>(grouped.values());
        stops.sort(Comparator.comparingInt(s -> (Integer) s.get("seq") == null ? Integer.MAX_VALUE : (Integer) s.get("seq")));
        for (Map<String, Object> stop : stops) {
            stop.put("primary", !stops.isEmpty() && stop == stops.get(0));
        }
        return stops;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReceipt(Long id) {
        StoreReceipt receipt = require(receiptMapper.selectById(id), "签收记录不存在");
        receiptMapper.deleteById(id);
        // 站点退回「已到达」并摘掉凭证：凭证是货权转移的唯一凭据，删了它这一站就不能再算「已签收」。
        // 再把运单状态按站点汇总退回来 —— 否则会出现「签收记录没了、运单还停在已签收」。
        transportStopMapper.update(null, Wrappers.<TransportStop>lambdaUpdate()
                .eq(TransportStop::getReceiptId, id)
                .set(TransportStop::getStatus, TransportStop.STOP_ARRIVED)
                .set(TransportStop::getReceiptId, null));
        recomputeTransportStatus(receipt.getTransportId());
    }

    @Override
    public Page<StoreReceipt> pageReceipts(Long storeId, String storeName, LocalDate startDate, LocalDate endDate,
                                           long pageNum, long pageSize) {
        return receiptMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<StoreReceipt>lambdaQuery()
                .eq(storeId != null, StoreReceipt::getStoreId, storeId)
                .like(StringUtils.hasText(storeName), StoreReceipt::getStoreName, storeName)
                // 签收日期范围：[开始日期 00:00:00, 结束日期次日 00:00:00)，结束日当天包含在内
                .ge(startDate != null, StoreReceipt::getReceiptTime,
                        startDate == null ? null : startDate.atStartOfDay())
                .lt(endDate != null, StoreReceipt::getReceiptTime,
                        endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                .orderByDesc(StoreReceipt::getReceiptTime));
    }

    @Override
    public List<Map<String, Object>> stores() {
        return receiptMapper.selectMaps(Wrappers.<StoreReceipt>query()
                .select("store_id", "MAX(store_name) AS store_name").groupBy("store_id").orderByAsc("store_id"));
    }

    /* ==================== 发货计划：运单的上游决策单 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPlan createPlan(DistributionRequests.PlanCreate request) {
        List<SplitBatch> batches = loadPlanBatches(request.splitBatchIds(), null);
        DeliveryPlan plan = new DeliveryPlan();
        plan.setPlanDate(request.planDate());
        plan.setStoreId(request.storeId());
        plan.setStoreName(request.storeName());
        plan.setVehicleNo(request.vehicleNo());
        plan.setVehicleType(request.vehicleType());
        plan.setRefrigeration(request.refrigeration());
        plan.setDriverName(request.driverName());
        plan.setDriverPhone(request.driverPhone());
        // 起运地优先取报文，没给就从货物所属胴体批次带出（免得每次都手填）
        plan.setOrigin(StringUtils.hasText(request.origin()) ? request.origin() : resolveOrigin(batches));
        plan.setPlannedDepart(request.plannedDepart());
        plan.setPlannedArrive(request.plannedArrive());
        plan.setRemark(request.remark());
        plan.setStatus(PLAN_PENDING);
        // 来源显式写死 MANUAL：补货/调拨/铺货没有订单，这条路径永久保留，也是 planSource 的默认口径
        plan.setPlanSource(PlanSource.MANUAL);
        plan.setOperator(sourceGuard.currentOperator().display());
        plan.setCreateTime(LocalDateTime.now());
        List<DeliveryPlanItem> items = newPlanItems(null, batches);
        plan.setItemCount(items.size());
        plan.setTotalWeightKg(sumWeight(items));
        insertPlanWithGeneratedNo(plan);
        items.forEach(item -> {
            item.setPlanId(plan.getId());
            deliveryPlanItemMapper.insert(item);
        });
        return plan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(Long id, DistributionRequests.PlanUpdate request) {
        DeliveryPlan plan = require(deliveryPlanMapper.selectById(id), "发货计划不存在");
        Integer status = plan.getStatus();
        boolean partial = Integer.valueOf(PLAN_PARTIAL).equals(status);
        if (!Integer.valueOf(PLAN_PENDING).equals(status) && !partial) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    Integer.valueOf(PLAN_GENERATED).equals(status)
                            ? "该计划已排车（最近一张运单 " + plan.getTransportNo() + "），不能再改"
                            : "已取消的计划不能再修改");
        }
        List<DeliveryPlanItem> existing = planItemsOf(id);
        List<DeliveryPlanItem> mutable = existing.stream().filter(item -> !isShipped(item)).toList();
        if (!partial && mutable.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少要勾选一个待发运的分割批次");
        }
        // 传了批次就整体替换「未排车」那部分；没传只改车/人/时间等属性。
        // 已排车的明细一律不动 —— 那是既成台账，改写它等于篡改事实。
        List<SplitBatch> batches = (request.splitBatchIds() == null || request.splitBatchIds().isEmpty())
                ? null : loadPlanBatches(request.splitBatchIds(), id);
        plan.setPlanDate(request.planDate());
        plan.setStoreId(request.storeId());
        plan.setStoreName(request.storeName());
        plan.setVehicleNo(request.vehicleNo());
        plan.setVehicleType(request.vehicleType());
        plan.setRefrigeration(request.refrigeration());
        plan.setDriverName(request.driverName());
        plan.setDriverPhone(request.driverPhone());
        plan.setOrigin(StringUtils.hasText(request.origin())
                ? request.origin()
                : (batches == null ? plan.getOrigin() : resolveOrigin(batches)));
        plan.setPlannedDepart(request.plannedDepart());
        plan.setPlannedArrive(request.plannedArrive());
        plan.setRemark(request.remark());
        if (batches != null) {
            deliveryPlanItemMapper.delete(Wrappers.<DeliveryPlanItem>lambdaQuery()
                    .eq(DeliveryPlanItem::getPlanId, id)
                    .ne(DeliveryPlanItem::getShipped, ITEM_SHIPPED));
            newPlanItems(id, batches).forEach(deliveryPlanItemMapper::insert);
        }
        // 计划规模一律按「全部明细」记（含已排车的）：这张计划一共安排了多少货，
        // 是它的固有属性；发了多少、还剩多少看明细的 shipped 标记。
        List<DeliveryPlanItem> all = planItemsOf(id);
        plan.setItemCount(all.size());
        plan.setTotalWeightKg(sumWeight(all));
        plan.setStatus(partial ? PLAN_PARTIAL : PLAN_PENDING);
        deliveryPlanMapper.updateById(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPlan(Long id, String reason) {
        DeliveryPlan plan = require(deliveryPlanMapper.selectById(id), "发货计划不存在");
        Integer status = plan.getStatus();
        if (!Integer.valueOf(PLAN_PENDING).equals(status) && !Integer.valueOf(PLAN_PARTIAL).equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    Integer.valueOf(PLAN_GENERATED).equals(status)
                            ? "该计划已全部排车，没有可取消的货物"
                            : "该计划已取消");
        }
        // 只置状态不删行：取消过的计划号不复用，且「发过什么计划」是管理轨迹。
        // 已排车的明细保持 shipped=1（那批货确实走了），未排车的明细因为计划不再「待发运/部分排车」
        // 而自动回到「待发运」列表 —— 释放靠的是状态口径，不需要逐个去清标记。
        plan.setStatus(PLAN_CANCELLED);
        if (StringUtils.hasText(reason)) {
            plan.setRemark(StringUtils.hasText(plan.getRemark()) ? "【已取消】" + reason + "｜" + plan.getRemark() : "【已取消】" + reason);
        }
        deliveryPlanMapper.updateById(plan);
    }

    /* ==================== 订单转入：外部选购事实 → 待排车计划 ==================== */

    /**
     * 订单转入（接入通道 {@code ORDER} 的落点）。
     * <p>
     * 现实里订单在 ERP/OMS，本系统只管溯源 —— 所以订单**不在本系统产生，但可以收进来**。
     * 它不是「本系统长出商流」，只是又多了一条事实源，与设备通道同一套机制。
     * <p>
     * <b>落的是「待排车的计划」，不是既成事实</b>：这批货哪天走、哪台车、谁开，
     * 仍然由排车决定（人工配车，或装车口扫批次自动倒查）。订单只提供
     * 「要什么货、发去哪家店、什么时候要」这三件，所以它只是计划的<b>来源之一</b>。
     * <p>
     * 幂等：同一 {@code order_no} 重复推送直接回既有计划。订单号来自外部系统，
     * 重推是常态（网络重试、对账重跑），不挡就会凭空多出一堆空计划。
     * <p>
     * <b>不做跨订单归并</b>（一张订单 = 一张计划）。同门店同一天的多张订单会生成多张计划，
     * 各自可以生成运单（分车）。「把它们并成一车」是排车决策，不是接入层的职责；
     * 真要支持「一车多单」，得先放开 {@code store_receipt} 的 uk_transport 唯一键，
     * 那是一个会动结构树的独立改造，不该先在接入层塞一个「逗号分隔的订单号」糊过去。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createPlanFromOrder(DistributionRequests.OrderIntake request) {
        // ① 幂等：同一订单号已经转入过，直接回既有计划
        DeliveryPlan existing = deliveryPlanMapper.selectOne(Wrappers.<DeliveryPlan>lambdaQuery()
                .eq(DeliveryPlan::getOrderNo, request.orderNo())
                .orderByAsc(DeliveryPlan::getId).last("LIMIT 1"));
        if (existing != null) {
            return ingestWritten(existing.getId(), "订单 " + request.orderNo() + " 已转入过发货计划 "
                    + existing.getPlanNo() + "（" + existing.getStoreName() + "），本次未重复建单");
        }

        // ② 目的地核对：门店必须来自机构树，且机构类型确实是门店（type=retail）。
        //    门店 ID 决定召回圈范围，查不到一律转人工 —— 绝不因为「查不到」就默认放行。
        Map<String, Object> org;
        try {
            org = orgClient.findOrg(request.storeId());
        } catch (BusinessException e) {
            // 机构服务不可用：同样转人工，不能默认放行（圈错门店范围比晚一点发货严重得多）。
            // 这里吞掉异常而不是往外抛，是为了让回执统一走 {written:false, retryable:true}，
            // 否则接入层会把它译成「拒绝」——外部系统据此删单，这张订单就永久丢了。
            return ingestManual("核对目的地门店时机构服务不可用（" + e.getMessage() + "），请稍后重推或人工核对");
        }
        if (org == null) {
            return ingestManual("订单 " + request.orderNo() + " 的目的地门店（id=" + request.storeId()
                    + "）在机构树里查不到，无法确定发去哪家店，请人工核对后再转入");
        }
        if (!STORE_TYPE.equals(String.valueOf(org.get("type")))) {
            return ingestManual("订单 " + request.orderNo() + " 的目的地「" + org.get("name")
                    + "」不是门店（机构类型 " + org.get("type") + "），订单只能发往门店，请人工核对");
        }
        // 门店名以机构树为准：外部系统传的名称可能与机构树不一致，两个都存就是两个真相
        String storeName = String.valueOf(org.get("name"));

        // ③ 部位 → 实物批次：订单要的是「部位 + 重量」，装车装的是「实物批次」，
        //    中间必须有一次分配。同一批货不能同时顶两条明细，所以用一个集合去重。
        Set<Long> picked = new LinkedHashSet<>();
        List<SplitBatch> batches = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (DistributionRequests.OrderItem item : request.items()) {
            MeatPart part = meatPartResolver.resolve(item.partCode(), item.partName());
            if (part == null) {
                return ingestManual("订单 " + request.orderNo() + " 的部位「"
                        + (StringUtils.hasText(item.partCode()) ? item.partCode() : item.partName())
                        + "」不在部位字典里，无法确定发什么货，请先补字典或人工核对");
            }
            BigDecimal need = item.weightKg();
            // 防御：负重量会让下面的「凑够」判断立刻成立，最后生成一张 0 明细的空计划。
            // 接入层与 DTO 校验都已经挡过一轮，这里再兜一次（服务层可能被别的调用方进来）。
            if (need == null || need.compareTo(BigDecimal.ZERO) <= 0) {
                return ingestManual("订单 " + request.orderNo() + " 的「" + part.getPartName()
                        + "」订购重量不合法（" + need + "），请人工核对");
            }
            BigDecimal got = BigDecimal.ZERO;
            int before = batches.size();
            for (SplitBatch batch : pendingBatchesOfPart(part.getPartCode())) {
                if (got.compareTo(need) >= 0) {
                    break;
                }
                if (!picked.add(batch.getId())) {
                    continue;
                }
                batches.add(batch);
                got = got.add(batch.getWeightKg() == null ? BigDecimal.ZERO : batch.getWeightKg());
            }
            if (got.compareTo(need) < 0) {
                // 凑不齐一律转人工：不猜、不拆零兜底。实物批次是已经切好的东西，
                // 「少发一点先出库」不是系统能替业务决定的事。
                return ingestManual("订单 " + request.orderNo() + " 要求的「" + part.getPartName()
                        + "」需要 " + need + "kg，当前可发运的只有 " + got + "kg（"
                        + (batches.size() - before) + " 个批次），备不齐货，请人工核对后再转入");
            }
            // 实物批次不可拆，按重量凑一定「凑够即止、通常略超」；超量不阻断，只留痕说明
            if (got.compareTo(need) > 0) {
                notes.add(part.getPartName() + " 订购 " + need + "kg、实配 " + got + "kg");
            }
        }

        // ④ 建计划：停在「待发货」= 待排车。车、司机、发车时间都还没定 —— 排车是人的决定。
        DeliveryPlan plan = new DeliveryPlan();
        plan.setPlanDate(request.planDate() != null ? request.planDate()
                : (request.expectArrive() == null ? LocalDate.now() : request.expectArrive().toLocalDate()));
        plan.setStoreId(request.storeId());
        plan.setStoreName(storeName);
        plan.setOrigin(resolveOrigin(batches));
        plan.setPlannedArrive(request.expectArrive());
        plan.setStatus(PLAN_PENDING);
        plan.setPlanSource(PlanSource.ORDER);
        plan.setOrderNo(request.orderNo());
        // 制单人**不写人名**：这张计划是外部系统推的，没有内部制单人。
        // 把终端号写进来源说明，既不伪造人名、又能回答「这是谁推过来的」。
        plan.setOperator(StringUtils.hasText(request.sourceRef())
                ? "订单转入（" + request.sourceRef() + "）" : "订单转入（外部系统）");
        plan.setRemark(orderRemark(request, notes));
        plan.setCreateTime(LocalDateTime.now());
        List<DeliveryPlanItem> items = newPlanItems(null, batches);
        plan.setItemCount(items.size());
        plan.setTotalWeightKg(sumWeight(items));
        insertPlanWithGeneratedNo(plan);
        items.forEach(item -> {
            item.setPlanId(plan.getId());
            deliveryPlanItemMapper.insert(item);
        });
        return ingestWritten(plan.getId(), "订单 " + request.orderNo() + " 已转入发货计划 "
                + plan.getPlanNo() + "：发往 " + storeName + "，" + items.size() + " 个批次共 "
                + plan.getTotalWeightKg() + "kg，状态待发货（待排车）");
    }

    /**
     * 某部位当前可发运的实物批次，先进先出（老货先出，冷链场景下这是硬要求）。
     * 排除条件与 {@link #pagePendingSplits} 共用同一份口径常量。
     */
    private List<SplitBatch> pendingBatchesOfPart(String partCode) {
        return splitBatchMapper.selectList(Wrappers.<SplitBatch>lambdaQuery()
                .eq(SplitBatch::getPartCode, partCode)
                .notInSql(SplitBatch::getId, EXCLUDE_SHIPPED_BY_TRANSPORT)
                .notInSql(SplitBatch::getId, EXCLUDE_TAKEN_BY_PENDING_PLAN)
                .orderByAsc(SplitBatch::getCreateTime));
    }

    /** 订单转入的备注：来源订单号 + 超量说明 + 订单自带备注，统一截断到列宽 */
    private String orderRemark(DistributionRequests.OrderIntake request, List<String> notes) {
        StringBuilder text = new StringBuilder("【订单转入】订单号 ").append(request.orderNo());
        if (!notes.isEmpty()) {
            text.append("；实物批次不可拆，实配略超订购：").append(String.join("；", notes));
        }
        if (StringUtils.hasText(request.remark())) {
            text.append("；订单备注：").append(request.remark());
        }
        return text.length() > PLAN_REMARK_MAX ? text.substring(0, PLAN_REMARK_MAX) : text.toString();
    }

    /** 接入回执：已入账 */
    private Map<String, Object> ingestWritten(Long id, String reason) {
        return ingestResult(true, id, reason, false);
    }

    /** 接入回执：待人工处理（条件可能被人工补齐，所以 retryable=true） */
    private Map<String, Object> ingestManual(String reason) {
        return ingestResult(false, null, reason, true);
    }

    /**
     * 与业务内部接口的统一回执：{@code {written, id, reason, retryable}}。
     * 接入层 {@code Handlers.toResult} 按这个结构翻成「已入账 / 待人工 / 拒绝」。
     */
    private Map<String, Object> ingestResult(boolean written, Long id, String reason, boolean retryable) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("written", written);
        body.put("id", id);
        body.put("reason", reason);
        body.put("retryable", retryable);
        return body;
    }

    @Override
    public Page<DeliveryPlan> pagePlans(Integer status, String keyword, LocalDate startDate, LocalDate endDate,
                                        long pageNum, long pageSize) {
        return deliveryPlanMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<DeliveryPlan>lambdaQuery()
                        .eq(status != null, DeliveryPlan::getStatus, status)
                        // 来源订单号也在搜索范围内：列表「来源」列明明展示着订单号，
                        // 拿着它却搜不出这张计划 —— 用户从设备接入看到「订单已转入」后，
                        // 第一反应就是拿订单号回来查，搜不到会被当成「上报了却没建计划」。
                        .and(StringUtils.hasText(keyword), w -> w.like(DeliveryPlan::getPlanNo, keyword)
                                .or().like(DeliveryPlan::getOrderNo, keyword)
                                .or().like(DeliveryPlan::getStoreName, keyword)
                                .or().like(DeliveryPlan::getVehicleNo, keyword))
                        .ge(startDate != null, DeliveryPlan::getPlanDate, startDate)
                        .le(endDate != null, DeliveryPlan::getPlanDate, endDate)
                        .orderByDesc(DeliveryPlan::getCreateTime));
    }

    @Override
    public Map<String, Object> getPlan(Long id) {
        DeliveryPlan plan = require(deliveryPlanMapper.selectById(id), "发货计划不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", plan);
        result.put("items", planItemsOf(id));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ColdChainTransport generateTransportFromPlan(Long id, List<Long> splitBatchIds) {
        return generateTransportFromPlan(id, splitBatchIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ColdChainTransport generateTransportFromPlan(Long id, List<Long> splitBatchIds,
                                                        DistributionRequests.LoadSite site) {
        // 单计划只是多计划的一个特例（一张计划 = 一家门店）。口径只写一份 ——
        // 让「单计划版」与「多计划版」各自演化，迟早出现同一个场景两个答案。
        return generateTransportFromPlans(List.of(id), splitBatchIds, site);
    }

    /**
     * 按计划生成运单：<b>一个车次一张运单，可含多张计划（= 多家门店）</b>。
     * <p>
     * 现实里一辆冷藏车装满后顺路送三四家门店，所以运单粒度是「一个车次」而不是「一次送货」。
     * 站点不另建表：装载明细（{@code transport_item}）带上卸货门店与站点序号，
     * 按 {@code store_id} 聚合本表就是站点清单。
     * <p>
     * <b>每一批货的门店归属都由它所属的计划定死，这里一次都不猜</b>：
     * 站点序号按本次勾选/扫码的先后分配（「按站装车」时就是卸货顺序）。
     * 同一批货挂在两张计划的未排车明细上属于台账冲突，转人工而不是擅自选一个。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ColdChainTransport generateTransportFromPlans(List<Long> planIds, List<Long> splitBatchIds,
                                                         DistributionRequests.LoadSite site) {
        List<Long> ids = planIds == null ? List.of()
                : planIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少要指定一张发货计划");
        }

        // ① 逐张校验计划状态：只有「待发货 / 部分排车」还能发货
        Map<Long, DeliveryPlan> plans = new LinkedHashMap<>();
        for (Long planId : ids) {
            DeliveryPlan loaded = require(deliveryPlanMapper.selectById(planId), "发货计划不存在");
            Integer st = loaded.getStatus();
            if (!Integer.valueOf(PLAN_PENDING).equals(st) && !Integer.valueOf(PLAN_PARTIAL).equals(st)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        Integer.valueOf(PLAN_GENERATED).equals(st)
                                ? "计划 " + loaded.getPlanNo() + " 已全部排车（最近一张运单 "
                                  + loaded.getTransportNo() + "），没有待发货物"
                                : "已取消的计划 " + loaded.getPlanNo() + " 不能生成运单");
            }
            plans.put(planId, loaded);
        }

        List<DeliveryPlanItem> pending = deliveryPlanItemMapper.selectList(Wrappers.<DeliveryPlanItem>lambdaQuery()
                .in(DeliveryPlanItem::getPlanId, ids)
                .eq(DeliveryPlanItem::getShipped, 0)
                .orderByAsc(DeliveryPlanItem::getId));
        if (pending.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "计划没有待发运的货物明细，无法生成运单");
        }

        // ② 本次发哪几批：不指定 = 全部剩余（整单发走）；指定 = 分车
        List<DeliveryPlanItem> target = pending;
        if (splitBatchIds != null && !splitBatchIds.isEmpty()) {
            Set<Long> want = new LinkedHashSet<>(splitBatchIds);
            Set<Long> allowed = pending.stream().map(DeliveryPlanItem::getSplitBatchId).collect(Collectors.toSet());
            List<Long> invalid = want.stream().filter(batchId -> !allowed.contains(batchId)).toList();
            if (!invalid.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "批次 " + invalid + " 不在这些计划的待发运明细里（可能已排车或不属于这些计划）");
            }
            target = pending.stream().filter(item -> want.contains(item.getSplitBatchId())).toList();
            // 再按**调用方给的顺序**重排（= 装车口扫码顺序 / 人工勾选顺序）：
            // 「按站装车」时这个先后就是卸货顺序，不能被数据库返回的明细 id 顺序冲掉。
            Map<Long, DeliveryPlanItem> byBatch = new LinkedHashMap<>();
            pending.forEach(it -> byBatch.putIfAbsent(it.getSplitBatchId(), it));
            target = want.stream().map(byBatch::get).filter(Objects::nonNull).toList();
        }

        // ③ 每批货只能归属一张计划。挂在两张计划的未排车明细上属于台账冲突
        //    （计划侧 loadPlanBatches 本该拦住），交人工，不擅自替它选一个。
        Map<Long, Long> batchOwner = new LinkedHashMap<>();
        for (DeliveryPlanItem item : target) {
            Long prev = batchOwner.put(item.getSplitBatchId(), item.getPlanId());
            if (prev != null && !prev.equals(item.getPlanId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "分割批次 " + item.getSplitBatchId() + " 同时挂在两张计划的未排车明细上，请人工核对");
            }
        }
        // 站点序号按 target 的顺序分配：target 保留的是勾选/扫码的先后，
        // 「按站装车」时这个顺序就是卸货顺序；同一计划的货自然聚成同一站。
        Map<Long, Integer> planSeq = new LinkedHashMap<>();
        for (DeliveryPlanItem item : target) {
            planSeq.computeIfAbsent(item.getPlanId(), k -> planSeq.size() + 1);
        }

        // ④ 车与人：先把多张计划合并成「计划侧的值」，再与现场值合并（规则见 resolveSiteValue）。
        //    两张计划安排了不同的车 / 不同的司机 ⇒ 一趟车挂两台车，直接拒绝。
        String plannedVehicle = mergePlanned("车牌号", plans.values().stream()
                .map(DeliveryPlan::getVehicleNo).filter(StringUtils::hasText).distinct().toList());
        String plannedDriver = mergePlanned("司机姓名", plans.values().stream()
                .map(DeliveryPlan::getDriverName).filter(StringUtils::hasText).distinct().toList());
        String vehicleNo = resolveSiteValue("车牌号", plannedVehicle, site == null ? null : site.vehicleNo());
        String driverName = resolveSiteValue("司机姓名", plannedDriver, site == null ? null : site.driverName());
        // 司机电话同理：计划填了用计划的，其次用现场的（电话不是必填，也不参与「本人确认」推导）
        String driverPhone = plans.values().stream().map(DeliveryPlan::getDriverPhone)
                .filter(StringUtils::hasText).findFirst()
                .orElse(site == null ? null : site.driverPhone());
        // 司机是怎么确认的必须落库：追责时「人名非空」不等于「本人确认过」。
        // 人工/计划路径（site == null）一律记人工录入、非本人确认 —— 那是调度填的，不是本人刷的。
        // 反过来，**没有司机就不该有来源**：写一个 MANUAL 等于假装调度录过一个名字，
        // 追责时反而误导（"有来源却没人"比"两个都空"更糟）。空名字 ⇒ 来源也留空。
        String driverSource = !StringUtils.hasText(driverName) ? null
                : (site == null ? DriverSource.MANUAL : DriverSource.normalize(site.driverSource()));
        // 车牌必须补齐才建单：它是后面发车（DEPART）与到达（ARRIVE）按车牌倒查的唯一凭据，
        // 少了它整条车的行程就断了，而且车是装车口相机必然读到的物理事实。
        if (!StringUtils.hasText(vehicleNo)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成运单前必须补全车牌号");
        }
        // 司机**允许留空**（2026-10-10 起）：现场只在上车那一刻刷一次卡，装车时司机还没上车，
        // 也没人能在装车口替他证明身份。所以这颗「责任落到具体人」的闸门挪到了发车 ——
        // `depart` 与 `ingestDepart` 都会拦住没有司机的运单，中间这段空档如实显示为「未记录」。

        // ⑤ 先算好装载明细行（带卸货门店与站点序号）—— 运单的 destination / stopCount 由它推导
        List<ShipLine> lines = new ArrayList<>(target.size());
        for (DeliveryPlanItem item : target) {
            SplitBatch batch = splitBatchMapper.selectById(item.getSplitBatchId());
            if (batch == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "分割批次 " + item.getSplitBatchId() + " 不存在或已删除，无法生成运单");
            }
            DeliveryPlan owner = plans.get(item.getPlanId());
            lines.add(new ShipLine(item.getSplitBatchId(), batch.getBatchNo(), batch.getWeightKg(),
                    owner.getStoreId(), owner.getStoreName(), planSeq.get(item.getPlanId())));
        }

        LocalDateTime now = LocalDateTime.now();

        // ⑥ 「同一趟车继续装」？装车口的货是人来一批、扫一批的：工人按车厢分区扫，
        //    或分属两套单据的货先后搬上车，都会出现「同一车牌短时间内再次扫码」。
        //    这时该做的是**把货追加到那张还没发车的运单上**，而不是再开一张 ——
        //    否则同一台车会裂出两张「待发车」运单，司机发车时按车牌倒查还会撞上
        //    「多张待发车」而转人工：一份现场作业变成两条待办。
        //    **已经发走的运单不在候选里**（status 变了）—— 「分车」正是靠这一点与并入区分开：
        //    第一车开走了，第二车才会另开一张。
        if (site != null && StringUtils.hasText(vehicleNo)) {
            ColdChainTransport open = findOpenLoadTransport(vehicleNo);
            if (open != null) {
                return appendToTransport(open, plans, target, lines, site,
                        driverName, driverPhone, driverSource, now);
            }
        }

        DeliveryPlan first = plans.get(ids.get(0));
        ColdChainTransport transport = new ColdChainTransport();
        transport.setTransportNo(BusinessNoGenerator.next("TR"));
        // 主批次取本次第一条：split_batch_id 是单值列，装载全貌以 transport_item 为准
        transport.setSplitBatchId(lines.get(0).splitBatchId());
        transport.setVehicleNo(vehicleNo);
        // 车型：计划填了就用计划的；没填则用「车牌 → 车辆备案」带出来的值补齐（车型不是安全红线，不拦不一致）
        transport.setVehicleType(plans.values().stream().map(DeliveryPlan::getVehicleType)
                .filter(StringUtils::hasText).findFirst()
                .orElse(site == null ? null : site.vehicleType()));
        transport.setRefrigeration(first.getRefrigeration());
        transport.setDriverName(driverName);
        transport.setDriverPhone(driverPhone);
        transport.setDriverSource(driverSource);
        transport.setDriverConfirmed(DriverSource.confirmedFlag(driverSource));
        transport.setOrigin(first.getOrigin());
        // destination 语义降级为「首站 / 主目的地」：单站就是门店名（存量 34 条即此，语义不变），
        // 多站写「途经 N 站」；真正的站点清单在 transport_item 里。
        transport.setDestination(stopsLabel(lines));
        transport.setStopCount(distinctStoreCount(lines));
        transport.setPlannedDepart(first.getPlannedDepart());
        transport.setPlannedArrive(first.getPlannedArrive());
        transport.setStatus(TRANSPORT_PENDING);
        transport.setSource(site == null ? TRANSPORT_SOURCE_PLAN : TRANSPORT_SOURCE_DEVICE);
        transport.setSourceRef(site == null ? null : site.sourceRef());
        transport.setRawPayload(site == null ? null : site.rawPayload());
        transport.setCreateTime(now);
        transportMapper.insert(transport);

        linkTransportItems(transport.getId(), lines);
        // 站点行程台账同时建起来：装载明细只说明「装了哪些货」，
        // 「这一站到了没、什么时候到的」必须有自己的落点（否则多站时到达无处可记）。
        createStops(transport.getId(), lines);

        // ⑦ 逐张计划推进：这趟车可能只装了其中一张计划的货，没发到货的计划完全不动
        advancePlans(plans, ids, target, pending, transport.getId(), transport.getTransportNo(),
                vehicleNo, driverName, driverPhone, now);
        return transport;
    }

    /**
     * 找这张车牌名下「还没发车」的装车口运单 —— 「同一趟车继续装」的并入候选。
     * <p>
     * 只看三件事：<b>同一车牌 + 装车口自动生成（source=DEVICE）+ 还没发车（status=1）</b>，
     * 并且落在装车作业窗口内（{@code ingest.load.merge-window-minutes}，默认 120 分钟）。
     * <ul>
     *   <li>已经发走的运单自然不在候选里 —— 这也正是「分车」与「并入」的分界：
     *       第一车开走了，第二车才会另开一张。</li>
     *   <li><b>人工 / 计划生成的待发车运单不并入</b>：站点结构是人排车时定下的决策，
     *       设备扫一批货进来就改写它等于替人改了排车方案 —— 这种情况一律交人工判断。</li>
     *   <li><b>窗口外挂着的老单同样不并</b>：隔了很久还没发车的「待发车」是异常滞留，
     *       硬并进去等于替人认定它是活单，还把今天的新货挂到了一张来路不明的单子上。</li>
     * </ul>
     * 候选超过一张 → 抛业务异常交人工（说不清是哪一趟，就绝不猜）。
     * 返回 {@code null} 表示没有可并入的运单，按新的一张运单建。
     */
    private ColdChainTransport findOpenLoadTransport(String vehicleNo) {
        List<ColdChainTransport> open = transportMapper.selectList(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getVehicleNo, vehicleNo)
                .eq(ColdChainTransport::getStatus, TRANSPORT_PENDING)
                .ge(ColdChainTransport::getCreateTime, LocalDateTime.now().minusMinutes(loadMergeWindowMinutes))
                .orderByAsc(ColdChainTransport::getId));
        if (open.isEmpty()) return null;
        if (open.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "车牌 " + vehicleNo + " 名下有多张还没发车的运单（"
                            + open.stream().map(ColdChainTransport::getTransportNo).collect(Collectors.joining("、"))
                            + "），无法确定这次装的是哪一趟车，请人工核对后先发车或删单");
        }
        ColdChainTransport only = open.get(0);
        if (!TRANSPORT_SOURCE_DEVICE.equals(only.getSource())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "车牌 " + vehicleNo + " 名下已有一张还没发车的人工运单（" + only.getTransportNo()
                            + "）：装车口不改写人工排好的单子，请人工确认是并入它，还是先把它发车或删除");
        }
        return only;
    }

    /**
     * 把本次扫到的货<b>追加</b>进一张还没发车的装车口运单 —— 同一趟车继续装。
     * <p>
     * 与新建运单的三点差别：
     * <ol>
     *   <li><b>站点按门店归并</b>：新扫的货与车上已有的货是同一家店的，并进那一站
     *       （一站一行，不会因为分两次搬上车就把一个店算成两站）；新门店接在最大站号之后。</li>
     *   <li><b>车与人只补齐、不覆盖</b>：运单上已经写好的司机沿用；两边都有且不是同一个人 → 交人工。</li>
     *   <li>目的地与站点数<b>按站点台账重算</b>，不手工拼串。</li>
     * </ol>
     * 运单号、来源设备、原始报文都保持第一次装车时的样子 —— 这张运单从头到尾就是同一趟车，
     * 不是两张拼起来的。
     */
    private ColdChainTransport appendToTransport(ColdChainTransport existing, Map<Long, DeliveryPlan> plans,
                                                 List<DeliveryPlanItem> target, List<ShipLine> lines,
                                                 DistributionRequests.LoadSite site, String driverName,
                                                 String driverPhone, String driverSource, LocalDateTime now) {
        Long transportId = existing.getId();

        // ① 车上已有哪些站：先按门店 id 认（权威），再按店名兜一层 ——
        //    人工建单的老数据可能没写 store_id，同一个店不该因为这点差别被算成两站
        List<TransportStop> stops = transportStopMapper.selectList(Wrappers.<TransportStop>lambdaQuery()
                .eq(TransportStop::getTransportId, transportId).orderByAsc(TransportStop::getStopNo));
        Map<Long, TransportStop> byStoreId = new LinkedHashMap<>();
        Map<String, TransportStop> byStoreName = new LinkedHashMap<>();
        for (TransportStop s : stops) {
            if (s.getStoreId() != null) byStoreId.putIfAbsent(s.getStoreId(), s);
            if (StringUtils.hasText(s.getStoreName())) byStoreName.putIfAbsent(s.getStoreName(), s);
        }
        int baseStopNo = stops.stream().map(TransportStop::getStopNo).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0);

        // ② 本次每一行货落到哪一站：命中已有站点就并进去，否则新起一站
        Map<Integer, List<ShipLine>> grouped = new LinkedHashMap<>();
        Map<Integer, TransportStop> intoStop = new LinkedHashMap<>();
        Map<String, Integer> newStopNo = new LinkedHashMap<>();
        for (ShipLine line : lines) {
            TransportStop hit = matchStop(byStoreId, byStoreName, line);
            int seq;
            if (hit != null) {
                seq = hit.getStopNo();
                intoStop.putIfAbsent(seq, hit);
            } else {
                String key = storeKey(line.storeId(), line.storeName());
                Integer assigned = newStopNo.get(key);
                if (assigned == null) {
                    assigned = baseStopNo + newStopNo.size() + 1;
                    newStopNo.put(key, assigned);
                }
                seq = assigned;
            }
            grouped.computeIfAbsent(seq, k -> new ArrayList<>()).add(line);
        }

        // ③ 装载明细与站点一起写：seq 与站点序号同源（装车扫码顺序 = 卸货顺序）
        for (Map.Entry<Integer, List<ShipLine>> entry : grouped.entrySet()) {
            Integer seq = entry.getKey();
            List<ShipLine> group = entry.getValue();
            for (ShipLine line : group) {
                TransportItem item = new TransportItem();
                item.setTransportId(transportId);
                item.setSplitBatchId(line.splitBatchId());
                item.setBatchNo(line.batchNo());
                item.setWeightKg(line.weightKg());
                item.setStoreId(line.storeId());
                item.setStoreName(line.storeName());
                item.setSeq(seq);
                item.setCreateTime(now);
                transportItemMapper.insert(item);
            }
            BigDecimal addWeight = group.stream().map(ShipLine::weightKg).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            TransportStop hit = intoStop.get(seq);
            if (hit != null) {
                BigDecimal oldWeight = hit.getWeightKg() == null ? BigDecimal.ZERO : hit.getWeightKg();
                transportStopMapper.update(null, Wrappers.<TransportStop>lambdaUpdate()
                        .eq(TransportStop::getId, hit.getId())
                        .set(TransportStop::getItemCount,
                                (hit.getItemCount() == null ? 0 : hit.getItemCount()) + group.size())
                        .set(TransportStop::getWeightKg, oldWeight.add(addWeight))
                        // 老数据没写门店 id、这次带着 id 来了 → 补全（是补全，不是改写）
                        .set(hit.getStoreId() == null && group.get(0).storeId() != null,
                                TransportStop::getStoreId, group.get(0).storeId()));
            } else {
                transportStopMapper.insert(buildStop(transportId, seq, group, now));
            }
        }

        // ④ 车与人：只补齐、不覆盖 —— 这张运单从头到尾是同一趟车、同一个责任人
        if (StringUtils.hasText(existing.getDriverName()) && StringUtils.hasText(driverName)
                && !existing.getDriverName().equals(driverName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "运单 " + existing.getTransportNo() + " 上已经写着司机「" + existing.getDriverName()
                            + "」，本次又报来「" + driverName + "」：同一次装车只能有一个司机，请人工核对");
        }
        if (!StringUtils.hasText(existing.getDriverName()) && StringUtils.hasText(driverName)) {
            existing.setDriverName(driverName);
            existing.setDriverSource(driverSource);
            existing.setDriverConfirmed(DriverSource.confirmedFlag(driverSource));
        }
        if (!StringUtils.hasText(existing.getDriverPhone()) && StringUtils.hasText(driverPhone)) {
            existing.setDriverPhone(driverPhone);
        }

        // ⑤ 目的地与站点数按站点台账重算：同门店已经并成一站，这里拿到的就是最终值
        List<TransportStop> merged = transportStopMapper.selectList(Wrappers.<TransportStop>lambdaQuery()
                .eq(TransportStop::getTransportId, transportId).orderByAsc(TransportStop::getStopNo));
        LinkedHashSet<String> storeNames = merged.stream().map(TransportStop::getStoreName)
                .filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new));
        existing.setStopCount(Math.max(1, merged.size()));
        existing.setDestination(storeNames.isEmpty() ? null
                : (storeNames.size() == 1 ? storeNames.iterator().next()
                   : "途经 " + storeNames.size() + " 站：" + String.join("、", storeNames)));
        transportMapper.updateById(existing);

        // ⑥ 计划推进：与新建走同一条路径（本次目标明细此刻还没标记，所以先查一次未排车明细算剩余量）
        List<Long> planIds = new ArrayList<>(plans.keySet());
        List<DeliveryPlanItem> pending = deliveryPlanItemMapper.selectList(Wrappers.<DeliveryPlanItem>lambdaQuery()
                .in(DeliveryPlanItem::getPlanId, planIds).eq(DeliveryPlanItem::getShipped, 0));
        advancePlans(plans, planIds, target, pending, transportId, existing.getTransportNo(),
                existing.getVehicleNo(), existing.getDriverName(), existing.getDriverPhone(), now);

        // 标记「这张运单是被追加过的」，让装车口的回执能如实说明去向（不是新开了一张）
        existing.setMergedIntoExisting(true);
        return existing;
    }

    /** 站点归并键：门店 id 优先（权威），人工建单只有店名时退化为店名 */
    private String storeKey(Long storeId, String storeName) {
        return storeId != null ? "id:" + storeId : "name:" + (storeName == null ? "" : storeName);
    }

    /** 这批货属于车上哪个已有的站：先按门店 id，认不到再按店名（老数据可能没写 id） */
    private TransportStop matchStop(Map<Long, TransportStop> byStoreId,
                                    Map<String, TransportStop> byStoreName, ShipLine line) {
        if (line.storeId() != null) {
            TransportStop byId = byStoreId.get(line.storeId());
            if (byId != null) return byId;
        }
        return StringUtils.hasText(line.storeName()) ? byStoreName.get(line.storeName()) : null;
    }

    /**
     * 逐张计划推进「这批货排进了哪张运单」。
     * <p>
     * 这趟车可能只装了某张计划的一部分货，没发到货的计划<b>完全不动</b>。
     * 分车时同一张计划的明细会指向不同运单，所以标记必须落在<b>明细</b>上；
     * 计划状态则按「这张计划还有没有没排进运单的明细」回算，不写死。
     *
     * @param pending 这些计划在本次标记<b>之前</b>的全部未排车明细 —— 用来算还剩多少没排上车
     */
    private void advancePlans(Map<Long, DeliveryPlan> plans, List<Long> planIds, List<DeliveryPlanItem> target,
                              List<DeliveryPlanItem> pending, Long transportId, String transportNo,
                              String vehicleNo, String driverName, String driverPhone, LocalDateTime now) {
        for (Long planId : planIds) {
            DeliveryPlan plan = plans.get(planId);
            List<Long> shippedIds = target.stream().filter(it -> planId.equals(it.getPlanId()))
                    .map(DeliveryPlanItem::getSplitBatchId).toList();
            if (shippedIds.isEmpty()) continue;

            // 明细逐条标记已排车：分车时同一计划的明细会指向不同运单，所以标记必须落在明细上而非计划主表
            deliveryPlanItemMapper.update(null, Wrappers.<DeliveryPlanItem>lambdaUpdate()
                    .eq(DeliveryPlanItem::getPlanId, planId)
                    .in(DeliveryPlanItem::getSplitBatchId, shippedIds)
                    .set(DeliveryPlanItem::getShipped, ITEM_SHIPPED)
                    .set(DeliveryPlanItem::getTransportId, transportId)
                    .set(DeliveryPlanItem::getShippedTime, now));

            // 现场读到的车与人在计划里原本空着 —— 回填，让计划单自己说清「这趟实际是谁开的哪辆车」
            plan.setVehicleNo(vehicleNo);
            plan.setDriverName(driverName);
            if (!StringUtils.hasText(plan.getDriverPhone()) && StringUtils.hasText(driverPhone)) {
                plan.setDriverPhone(driverPhone);
            }
            // 计划状态：还有货没排就是「部分排车」，全排完才是「已排车」
            long remaining = pending.stream().filter(it -> planId.equals(it.getPlanId())).count()
                    - shippedIds.size();
            plan.setStatus(remaining > 0 ? PLAN_PARTIAL : PLAN_GENERATED);
            plan.setTransportId(transportId);
            plan.setTransportNo(transportNo);
            plan.setTransportCount((plan.getTransportCount() == null ? 0 : plan.getTransportCount()) + 1);
            plan.setGeneratedTime(now);
            deliveryPlanMapper.updateById(plan);
        }
    }

    /**
     * 多张计划的同一字段合并成「计划侧的值」。
     * <p>
     * 留空是常态（车次没定）。多张计划都填了且<b>互不相同</b> ⇒ 直接拒绝：
     * 那意味着一趟车要挂两台车 / 两个责任人，最可能的解释是这些计划本来就不该拼一车。
     */
    private String mergePlanned(String field, List<String> values) {
        if (values.isEmpty()) return null;
        if (values.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "拼这一车的几张计划安排的" + field + "不一致（" + String.join("、", values)
                            + "），一趟车只能有一个" + field + "，请人工核对后重新排车");
        }
        return values.get(0);
    }

    /** 途经站点数：按卸货门店去重；门店缺失（人工建单没填门店）时按 1 站算 */
    private int distinctStoreCount(List<ShipLine> lines) {
        return Math.max(1, stops(lines).size());
    }

    /**
     * 运单目的地字段的展示值。
     * <p>
     * 单站显示门店名（存量 34 条运单就是这种，语义不变、无需迁移）；
     * 多站显示「途经 N 站：A、B」—— 一个 varchar 装不下也不该装下完整站点清单，
     * 真正的站点明细在 transport_item（按 store_id 聚合），前端也从那里取。
     */
    private String stopsLabel(List<ShipLine> lines) {
        LinkedHashSet<String> stores = stops(lines);
        if (stores.isEmpty()) return null;
        if (stores.size() == 1) return stores.iterator().next();
        return "途经 " + stores.size() + " 站：" + String.join("、", stores);
    }

    /** 按出现顺序去重的门店名 */
    private LinkedHashSet<String> stops(List<ShipLine> lines) {
        return lines.stream().map(ShipLine::storeName).filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 装载明细的写入要素：批次 + 卸货门店 + 站点序号 */
    private record ShipLine(Long splitBatchId, String batchNo, BigDecimal weightKg,
                            Long storeId, String storeName, Integer seq) {
    }

    /**
     * 现场值 vs 计划值的合并口径。
     * <p>
     * 计划为空 → 用现场值补齐（这正是自动化要的效果：车与人由设备带进来，人不用再填）。
     * 两边都有且不一致 → 抛出异常交人工核对：设备没有权限改写计划里已经安排的车辆与责任人，
     * 而"现场识别到的车 ≠ 计划安排的车"最可能的解释就是装错车了。
     */
    private String resolveSiteValue(String field, String planned, String onSite) {
        if (!StringUtils.hasText(onSite)) return planned;
        if (!StringUtils.hasText(planned)) return onSite;
        if (!planned.equals(onSite)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "装车口识别的" + field + "「" + onSite + "」与计划安排的「" + planned + "」不一致，请人工核对是否装错车");
        }
        return planned;
    }

    @Override
    public Page<SplitBatch> pagePendingSplits(String keyword, long pageNum, long pageSize) {
        // 「待发运」= 还没被安排发运的货，两道排除：
        //   ① 已被运单装载过（发都发走了）
        //   ② 已被「待发货 / 部分排车」计划的**未排车明细**占用（正在等装车，再勾一次会导致两张单据抢同一批货）
        // 不做持久化标记位：标记位会与运单删除、计划取消这些动作失同步，
        // 而「有没有运单/计划引用」永远是对的。已取消计划占用的批次、计划里已排车的批次，
        // 都会自动回到这个列表。
        return splitBatchMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<SplitBatch>lambdaQuery()
                        .and(StringUtils.hasText(keyword), w -> w.like(SplitBatch::getBatchNo, keyword)
                                .or().like(SplitBatch::getProductName, keyword))
                        .notInSql(SplitBatch::getId, EXCLUDE_SHIPPED_BY_TRANSPORT)
                        .notInSql(SplitBatch::getId, EXCLUDE_TAKEN_BY_PENDING_PLAN)
                        .orderByDesc(SplitBatch::getCreateTime));
    }

    /** 明细是否已随某张运单发运（null 视为未排车，历史行不会出现） */
    private boolean isShipped(DeliveryPlanItem item) {
        return Integer.valueOf(ITEM_SHIPPED).equals(item.getShipped());
    }

    /**
     * 校验并加载计划要挂的分割批次。
     * <p>
     * 两道拦截都是「挡在制单阶段」而不是等到装车口才发现：
     * ① 已由运单发运过的批次不能重复发货（重复发货是实质业务事故）；
     * ② 已被别的「待发货」计划占用的批次不能重复勾选（两张计划单抢同一批货，装车时必然打架）。
     * 已取消计划占用的批次自动释放，不会长期占用。
     *
     * @param excludePlanId 编辑计划时排除自己，避免把自己的旧明细当成"被别人占用"
     */
    private List<SplitBatch> loadPlanBatches(List<Long> splitBatchIds, Long excludePlanId) {
        List<Long> ids = splitBatchIds.stream().filter(Objects::nonNull).distinct().toList();
        List<SplitBatch> batches = new ArrayList<>();
        for (Long splitBatchId : ids) {
            SplitBatch batch = splitBatchMapper.selectById(splitBatchId);
            // selectById 已滤掉作废批次（@TableLogic）：作废的货不该再进发运计划
            if (batch == null) {
                throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "分割批次不存在或已作废：" + splitBatchId);
            }
            Long shipped = transportItemMapper.selectCount(Wrappers.<TransportItem>lambdaQuery()
                    .eq(TransportItem::getSplitBatchId, splitBatchId));
            if (shipped > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "分割批次 " + batch.getBatchNo() + " 已由运单发运过，不能重复发货");
            }
            batches.add(batch);
        }
        List<DeliveryPlanItem> taken = deliveryPlanItemMapper.selectList(Wrappers.<DeliveryPlanItem>lambdaQuery()
                .in(DeliveryPlanItem::getSplitBatchId, ids)
                .eq(DeliveryPlanItem::getShipped, 0));
        for (DeliveryPlanItem item : taken) {
            if (excludePlanId != null && excludePlanId.equals(item.getPlanId())) continue;
            DeliveryPlan other = deliveryPlanMapper.selectById(item.getPlanId());
            // 只有「还在等装车」的计划才算占用：已排车（2）、已取消（3）的计划不再挡货
            if (other != null && (Integer.valueOf(PLAN_PENDING).equals(other.getStatus())
                    || Integer.valueOf(PLAN_PARTIAL).equals(other.getStatus()))) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "分割批次已被待发货计划 " + other.getPlanNo() + "（" + other.getStoreName() + "）占用");
            }
        }
        return batches;
    }

    /** 按勾选的批次生成明细快照（只填货物字段，planId 由调用方补齐） */
    private List<DeliveryPlanItem> newPlanItems(Long planId, List<SplitBatch> batches) {
        List<DeliveryPlanItem> items = new ArrayList<>();
        for (SplitBatch batch : batches) {
            DeliveryPlanItem item = new DeliveryPlanItem();
            item.setPlanId(planId);
            item.setSplitBatchId(batch.getId());
            item.setBatchNo(batch.getBatchNo());
            item.setPartCode(batch.getPartCode());
            item.setProductName(batch.getProductName());
            item.setWeightKg(batch.getWeightKg());
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
        }
        return items;
    }

    private List<DeliveryPlanItem> planItemsOf(Long planId) {
        return deliveryPlanItemMapper.selectList(Wrappers.<DeliveryPlanItem>lambdaQuery()
                .eq(DeliveryPlanItem::getPlanId, planId).orderByAsc(DeliveryPlanItem::getId));
    }

    private BigDecimal sumWeight(List<DeliveryPlanItem> items) {
        return items.stream().map(DeliveryPlanItem::getWeightKg).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 起运地兜底：从货物所属胴体批次取屠宰场（同一场的冷库就是起运点） */
    private String resolveOrigin(List<SplitBatch> batches) {
        for (SplitBatch batch : batches) {
            if (batch.getParentBatchId() == null) continue;
            CarcassBatch parent = carcassBatchMapper.selectById(batch.getParentBatchId());
            if (parent != null && StringUtils.hasText(parent.getSlaughterhouse())) {
                return parent.getSlaughterhouse();
            }
        }
        return null;
    }

    /**
     * 记运单装载明细：运单的货物清单从此以 transport_item 为准，手工建的运单也走同一口径。
     * <p>
     * 这张表同时承载「一车多批」与「一车多店」两个维度：每条明细带上<b>卸货门店与站点序号</b>，
     * 按 {@code store_id} 聚合本表就是站点清单，所以不必另建 transport_stop。
     * 人工建单没有计划可倒查门店，只能沿用运单上手填的目的地（可能为空），站点序号记 1。
     */
    private void linkTransportItems(Long transportId, Long splitBatchId, String storeName) {
        SplitBatch batch = splitBatchMapper.selectById(splitBatchId);
        if (batch == null) return;
        linkTransportItems(transportId, List.of(new ShipLine(splitBatchId, batch.getBatchNo(),
                batch.getWeightKg(), null, storeName, 1)));
    }

    private void linkTransportItems(Long transportId, List<ShipLine> lines) {
        LocalDateTime now = LocalDateTime.now();
        for (ShipLine line : lines) {
            TransportItem item = new TransportItem();
            item.setTransportId(transportId);
            item.setSplitBatchId(line.splitBatchId());
            item.setBatchNo(line.batchNo());
            item.setWeightKg(line.weightKg());
            item.setStoreId(line.storeId());
            item.setStoreName(line.storeName());
            item.setSeq(line.seq());
            item.setCreateTime(now);
            transportItemMapper.insert(item);
        }
    }

    /**
     * 建站点行程台账：把本次装载明细按站点聚合，<b>一站一行</b>。
     * <p>
     * 站点序号直接取 {@code transport_item.seq}（装车扫码顺序 = 卸货顺序），与装载明细
     * <b>同源</b>、不另算一套；同一站的多批货合并成一行（记批次数与总重）。
     * 初始状态一律「未到达」—— 车还没出发，哪一站都没到。
     */
    private void createStops(Long transportId, List<ShipLine> lines) {
        int maxSeq = lines.stream().map(ShipLine::seq).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0);
        Map<Integer, List<ShipLine>> bySeq = new LinkedHashMap<>();
        int next = maxSeq;
        for (ShipLine line : lines) {
            // seq 为空的（混装未按站）排在已有序号之后，不占用别人的号
            int seq = line.seq() != null ? line.seq() : ++next;
            bySeq.computeIfAbsent(seq, k -> new ArrayList<>()).add(line);
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<Integer, List<ShipLine>> entry : bySeq.entrySet()) {
            transportStopMapper.insert(buildStop(transportId, entry.getKey(), entry.getValue(), now));
        }
    }

    /** 拼一条站点行：一站一行，状态一律「未到达」—— 车还没出发，哪一站都还没到 */
    private TransportStop buildStop(Long transportId, Integer stopNo, List<ShipLine> group, LocalDateTime now) {
        TransportStop stop = new TransportStop();
        stop.setTransportId(transportId);
        stop.setStopNo(stopNo);
        stop.setStoreId(group.get(0).storeId());
        stop.setStoreName(StringUtils.hasText(group.get(0).storeName())
                ? group.get(0).storeName() : "未记录门店");
        stop.setItemCount(group.size());
        stop.setWeightKg(group.stream().map(ShipLine::weightKg).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stop.setStatus(TransportStop.STOP_NOT_ARRIVED);
        stop.setCreateTime(now);
        return stop;
    }

    /** 计划号插入：并发下候选号可能撞键，换个序号重试；唯一索引才是最终权威 */
    private void insertPlanWithGeneratedNo(DeliveryPlan plan) {
        LocalDate day = plan.getPlanDate() == null ? LocalDate.now() : plan.getPlanDate();
        for (int skip = 0; skip < PLAN_NO_RETRY; skip++) {
            plan.setPlanNo(deliveryPlanNo.next(day, skip));
            try {
                deliveryPlanMapper.insert(plan);
                return;
            } catch (DuplicateKeyException e) {
                plan.setId(null);
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "发货计划号生成失败，请重试");
    }

    private <T> T require(T value, String message) {
        if (value == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, message);
        return value;
    }

    private Integer resolveSplitLevel(Long parentId) {
        if (carcassBatchMapper.selectById(parentId) != null) return 1;
        SplitBatch parent = splitBatchMapper.selectById(parentId);
        if (parent == null || parent.getSplitLevel() == null)
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "父批次不存在");
        int level = parent.getSplitLevel() + 1;
        if (level > 4) throw new BusinessException(ErrorCode.PARAM_ERROR, "分割层级不能超过4级");
        return level;
    }

    private void validateParent(Long parentId, Integer splitLevel) {
        if (splitLevel == 1) {
            require(carcassBatchMapper.selectById(parentId), "一级分割的胴体父批次不存在");
            return;
        }
        SplitBatch parent = require(splitBatchMapper.selectById(parentId), "上级分割批次不存在");
        if (parent.getSplitLevel() == null || parent.getSplitLevel() + 1 != splitLevel)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分割层级必须与父批次连续");
    }

    @SuppressWarnings("unchecked")
    private void collectSplitIds(Map<String, Object> node, List<Long> result) {
        if ("SPLIT".equals(node.get("type")) && node.get("data") instanceof SplitBatch split) {
            result.add(split.getId());
        }
        Object rawChildren = node.get("children");
        if (!(rawChildren instanceof Collection<?> children)) return;
        for (Object child : children) {
            if (child instanceof Map<?, ?> map) collectSplitIds((Map<String, Object>) map, result);
        }
    }

    private void publish(String type, Long id, String key, String hash, Map<String, Object> payload) {
        chainEvents.publish(ChainTxMessage.create(type, id, key, hash, payload));
    }
}
