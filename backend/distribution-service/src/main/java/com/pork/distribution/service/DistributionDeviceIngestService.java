package com.pork.distribution.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.util.HashUtil;
import com.pork.distribution.client.BreedingClient;
import com.pork.distribution.client.FileClient;
import com.pork.distribution.dto.DeviceIngestRequests;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.entity.CarcassBatch;
import com.pork.distribution.entity.CarcassBatchPig;
import com.pork.distribution.entity.ColdChainTransport;
import com.pork.distribution.entity.DeliveryPlan;
import com.pork.distribution.entity.DeliveryPlanItem;
import com.pork.distribution.entity.Driver;
import com.pork.distribution.entity.DriverCard;
import com.pork.distribution.entity.DriverVehicle;
import com.pork.distribution.entity.MeatPart;
import com.pork.distribution.entity.SplitBatch;
import com.pork.distribution.entity.SplitJob;
import com.pork.distribution.entity.StoreReceipt;
import com.pork.distribution.entity.TemperatureLog;
import com.pork.distribution.entity.TransportItem;
import com.pork.distribution.entity.TransportStop;
import com.pork.distribution.entity.Vehicle;
import com.pork.distribution.entity.WorkshopSnapshot;
import com.pork.distribution.mapper.CarcassBatchMapper;
import com.pork.distribution.mapper.CarcassBatchPigMapper;
import com.pork.distribution.mapper.ColdChainTransportMapper;
import com.pork.distribution.mapper.DeliveryPlanItemMapper;
import com.pork.distribution.mapper.DeliveryPlanMapper;
import com.pork.distribution.mapper.DriverCardMapper;
import com.pork.distribution.mapper.DriverMapper;
import com.pork.distribution.mapper.DriverVehicleMapper;
import com.pork.distribution.mapper.SplitBatchMapper;
import com.pork.distribution.mapper.SplitJobMapper;
import com.pork.distribution.mapper.StoreReceiptMapper;
import com.pork.distribution.mapper.TemperatureLogMapper;
import com.pork.distribution.mapper.TransportItemMapper;
import com.pork.distribution.mapper.VehicleMapper;
import com.pork.distribution.mapper.WorkshopSnapshotMapper;
import com.pork.distribution.support.BatchGuard;
import com.pork.distribution.support.CarcassBatchNo;
import com.pork.distribution.support.DriverSource;
import com.pork.distribution.support.MeatPartResolver;
import com.pork.distribution.support.SplitJobNo;
import com.pork.distribution.support.SplitYieldCheck;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备通道写入（冷链温度 / 门店签收 / 分割批次 / 胴体批次归集 / 装车发运）。
 * <p>
 * 与人工入口的差别只在「谁触发」：状态机约束完全一致——
 * 运输中才能记温度、到达后才能签收、一单只能签收一次、分割批次必须挂在已存在的胴体批次下、
 * 胴体批次以「已盖章 + 瘦肉精无阳性 + 未被占用」为前提，装车发运则必须有对应的待发货计划。
 * 设备绕过这些约束等于给溯源台账开后门，所以这里一律复用同一套判断。
 * <p>
 * 装车发运（{@link #ingestLoad}）有一个别的通道没有的特点：<b>它不产生业务判定</b>。
 * 车牌、司机、扫到的批次都是现场事实，目的地门店是人在发货计划里早就定死的——
 * 设备的作用是把「哪些货真的上了这辆车」记下来，并据此倒查计划自动生成运单。
 * 匹配不上一律转人工，设备无权替人决定货发去哪家店。
 */
@Slf4j
@Service
public class DistributionDeviceIngestService {

    private static final String SOURCE_DEVICE = "DEVICE";
    /** 运输任务状态：1待发车 2运输中 3已到达 4已签收 */
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_IN_TRANSIT = 2;
    private static final int STATUS_ARRIVED = 3;
    /** 胴体批次归集状态：1归集中（当日同场后续白条会并入） */
    private static final int BATCH_OPEN = 1;
    /** 发货计划状态：1=待发货 2=已排车 3=已取消 4=部分排车；只有 1 和 4 还能继续发货 */
    private static final int PLAN_PENDING = 1;
    private static final int PLAN_PARTIAL = 4;
    /**
     * 「还能发货」的计划 id 子查询，供装车口判定货物归属时使用。
     * <p>
     * 已取消（3）与已排车（2）的计划不再持有货权：它们的未排车明细即使 {@code shipped=0}
     * 也不该参与「这批货属于哪张计划」的判定 —— 批次早已按规则回到待发运池。
     * 口径与 {@code DistributionServiceImpl.EXCLUDE_TAKEN_BY_PENDING_PLAN}、人工建单的占用判定保持一致。
     */
    private static final String PLAN_ACTIVE_SQL =
            "SELECT id FROM delivery_plan WHERE status IN (" + PLAN_PENDING + ", " + PLAN_PARTIAL + ")";
    /** 同年同月同日的批号重试次数：并发下两个请求可能算出同一个序号，撞唯一键就换下一个 */
    private static final int BATCH_NO_RETRY = 5;
    /** 分割作业号同为「当日流水」，重试口径与批号一致 */
    private static final int JOB_NO_RETRY = 5;
    /** 对账基准来源：按耳标精确 / 批次单头均重 / 无基准 */
    private static final String BASIS_PIG = "PIG";
    private static final String BASIS_BATCH_AVG = "BATCH_AVG";
    private static final String BASIS_NONE = "NONE";
    /** 车间监控快照每次淘汰时最多清理的条数：一次上报通常只超出一两张，给足余量兜住历史堆积 */
    private static final int SNAPSHOT_PRUNE_BATCH = 200;

    private final ColdChainTransportMapper transportMapper;
    private final TemperatureLogMapper temperatureLogMapper;
    private final StoreReceiptMapper receiptMapper;
    private final CarcassBatchMapper carcassBatchMapper;
    private final CarcassBatchPigMapper carcassBatchPigMapper;
    private final SplitBatchMapper splitBatchMapper;
    private final SplitJobMapper splitJobMapper;
    private final MeatPartResolver meatPartResolver;
    private final DeliveryPlanMapper deliveryPlanMapper;
    private final DeliveryPlanItemMapper deliveryPlanItemMapper;
    private final TransportItemMapper transportItemMapper;
    /** 装车口要查的主数据：卡↔司机、车牌↔司机、车牌↔车型 */
    private final DriverCardMapper driverCardMapper;
    private final DriverMapper driverMapper;
    private final DriverVehicleMapper driverVehicleMapper;
    private final VehicleMapper vehicleMapper;
    private final DistributionService distributionService;
    private final ChainEventPublisher chainEvents;
    private final BatchGuard batchGuard;
    private final CarcassBatchNo carcassBatchNo;
    private final SplitJobNo splitJobNo;
    private final SplitYieldCheck splitYieldCheck;
    private final BreedingClient breedingClient;
    /** 车间监控快照的落点；淘汰旧快照时用它把文件也删掉 */
    private final WorkshopSnapshotMapper snapshotMapper;
    private final FileClient fileClient;
    private final BigDecimal defaultRangeMin;
    private final BigDecimal defaultRangeMax;
    /** 单个胴体批次的头数上限，达到即自动封批（默认 200，接近「一个待宰圈 / 一车」的典型规模） */
    private final int maxPigsPerBatch;
    /**
     * 温度断档判定的默认基准间隔（秒），设备台账没登记 {@code sample_interval_seconds} 时用它。
     * 「多久采一次」本该由探头自己声明；这里只是兜底，不是假设。
     */
    private final int defaultSampleIntervalSeconds;
    /** 断档容差倍数：间隔 &gt; 额定 × 本值才算断档。给足余量，免得降采样与网络抖动被误判 */
    private final int gapToleranceMultiplier;
    /**
     * 每台监控相机保留的快照张数上限（滚动窗口）。
     * <p>
     * 相机按分钟级周期上报，本地盘经不起无限增长 —— 超过这个数就把最老的连同文件一起淘汰。
     * 页面展示的是最近 10 张，保留值是它的两倍，给「一次分割作业看全过程」留出余量。
     */
    private final int snapshotRetention;

    public DistributionDeviceIngestService(ColdChainTransportMapper transportMapper,
                                           TemperatureLogMapper temperatureLogMapper,
                                           StoreReceiptMapper receiptMapper,
                                           CarcassBatchMapper carcassBatchMapper,
                                           CarcassBatchPigMapper carcassBatchPigMapper,
                                           SplitBatchMapper splitBatchMapper,
                                           SplitJobMapper splitJobMapper,
                                           MeatPartResolver meatPartResolver,
                                           DeliveryPlanMapper deliveryPlanMapper,
                                           DeliveryPlanItemMapper deliveryPlanItemMapper,
                                           TransportItemMapper transportItemMapper,
                                           DriverCardMapper driverCardMapper,
                                           DriverMapper driverMapper,
                                           DriverVehicleMapper driverVehicleMapper,
                                           VehicleMapper vehicleMapper,
                                           DistributionService distributionService,
                                           ChainEventPublisher chainEvents,
                                           BatchGuard batchGuard,
                                           CarcassBatchNo carcassBatchNo,
                                           SplitJobNo splitJobNo,
                                           SplitYieldCheck splitYieldCheck,
                                           BreedingClient breedingClient,
                                           WorkshopSnapshotMapper snapshotMapper,
                                           FileClient fileClient,
                                           @Value("${ingest.temperature.range-min:-18.0}") BigDecimal defaultRangeMin,
                                           @Value("${ingest.temperature.range-max:0.0}") BigDecimal defaultRangeMax,
                                           @Value("${ingest.carcass-batch.max-pigs:200}") int maxPigsPerBatch,
                                           @Value("${ingest.temperature.sample-interval-seconds:300}") int defaultSampleIntervalSeconds,
                                           @Value("${ingest.temperature.gap-tolerance-multiplier:3}") int gapToleranceMultiplier,
                                           @Value("${ingest.split-snapshot.retention:20}") int snapshotRetention) {
        this.transportMapper = transportMapper;
        this.temperatureLogMapper = temperatureLogMapper;
        this.receiptMapper = receiptMapper;
        this.carcassBatchMapper = carcassBatchMapper;
        this.carcassBatchPigMapper = carcassBatchPigMapper;
        this.splitBatchMapper = splitBatchMapper;
        this.splitJobMapper = splitJobMapper;
        this.meatPartResolver = meatPartResolver;
        this.deliveryPlanMapper = deliveryPlanMapper;
        this.deliveryPlanItemMapper = deliveryPlanItemMapper;
        this.transportItemMapper = transportItemMapper;
        this.driverCardMapper = driverCardMapper;
        this.driverMapper = driverMapper;
        this.driverVehicleMapper = driverVehicleMapper;
        this.vehicleMapper = vehicleMapper;
        this.distributionService = distributionService;
        this.chainEvents = chainEvents;
        this.batchGuard = batchGuard;
        this.carcassBatchNo = carcassBatchNo;
        this.splitJobNo = splitJobNo;
        this.splitYieldCheck = splitYieldCheck;
        this.breedingClient = breedingClient;
        this.snapshotMapper = snapshotMapper;
        this.fileClient = fileClient;
        this.defaultRangeMin = defaultRangeMin;
        this.defaultRangeMax = defaultRangeMax;
        this.maxPigsPerBatch = maxPigsPerBatch;
        this.defaultSampleIntervalSeconds = defaultSampleIntervalSeconds;
        this.gapToleranceMultiplier = gapToleranceMultiplier;
        this.snapshotRetention = snapshotRetention;
    }

    /** 冷链温度：运输中的运单才能记温度；异常值照常入账并标记 is_abnormal=1 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestTemperature(DeviceIngestRequests.DeviceTemperature request) {
        ColdChainTransport transport = resolveTransport(request.getTransportId(), request.getTransportNo());
        if (transport == null) {
            return manual("运输单号在冷链运单中不存在（" + request.getTransportNo() + "），请人工核对");
        }
        if (!Integer.valueOf(STATUS_IN_TRANSIT).equals(transport.getStatus())) {
            // 未发车 / 已到达：设备时间可能早于状态变化，交给人工判断是否补录
            return manual("运单 " + transport.getTransportNo() + " 当前状态不允许记录温度（须为运输中）");
        }
        if (request.getTemperature() == null) {
            return rejected("温度值缺失，禁止入库");
        }

        BigDecimal min = request.getTempRangeMin() == null ? defaultRangeMin : request.getTempRangeMin();
        BigDecimal max = request.getTempRangeMax() == null ? defaultRangeMax : request.getTempRangeMax();
        if (min.compareTo(max) > 0) {
            return rejected("温度下限不能高于上限");
        }

        TemperatureLog entity = new TemperatureLog();
        entity.setTransportId(transport.getId());
        entity.setTemperature(request.getTemperature());
        // 设备补传历史数据时按设备时间落库，避免补传整段被记成"现在"
        entity.setRecordTime(request.getRecordTime() == null ? LocalDateTime.now() : request.getRecordTime());
        entity.setTempRangeMin(min);
        entity.setTempRangeMax(max);
        entity.setIsAbnormal(request.getTemperature().compareTo(min) < 0
                || request.getTemperature().compareTo(max) > 0 ? 1 : 0);
        entity.setRecorder(StringUtils.hasText(request.getRecorder()) ? request.getRecorder() : "设备自动采集");
        entity.setRecordMethod("DEVICE");
        entity.setSource(SOURCE_DEVICE);
        entity.setSourceRef(request.getSourceRef());
        entity.setRawPayload(request.getRawPayload());
        entity.setCreateTime(LocalDateTime.now());

        // 断档检测：只判单点越界（is_abnormal）是不够的 —— 15:00 报一条、17:30 才报下一条，
        // 中间两个半小时是空白，而系统会显示「温度正常」。**空白比超标更危险**：
        // 超了至少看得见，空白是假装有数据。
        // 基准 = 探头自己声明的额定间隔（接入层从设备台账带上来），没声明才用服务默认值；
        // 容差给足倍数，免得降采样（最小入库间隔 60 秒）与网络抖动被误判成断档。
        int interval = request.getSampleIntervalSeconds() != null && request.getSampleIntervalSeconds() > 0
                ? request.getSampleIntervalSeconds() : defaultSampleIntervalSeconds;
        TemperatureLog last = temperatureLogMapper.selectOne(Wrappers.<TemperatureLog>lambdaQuery()
                .eq(TemperatureLog::getTransportId, transport.getId())
                .orderByDesc(TemperatureLog::getRecordTime)
                .orderByDesc(TemperatureLog::getId)
                .last("LIMIT 1"));
        Integer gapSeconds = null;
        boolean gap = false;
        if (last != null && last.getRecordTime() != null) {
            long seconds = Duration.between(last.getRecordTime(), entity.getRecordTime()).getSeconds();
            gapSeconds = (int) seconds;
            // 补传的历史数据（时间戳早于上一条）不判断档，否则整段补传会被标成一片红
            gap = seconds > (long) interval * gapToleranceMultiplier;
        }
        entity.setSampleIntervalSeconds(interval);
        entity.setGapSeconds(gapSeconds);
        entity.setIsGap(gap ? 1 : 0);
        temperatureLogMapper.insert(entity);

        StringBuilder reason = new StringBuilder();
        if (Integer.valueOf(1).equals(entity.getIsAbnormal())) {
            reason.append("温度超出允许区间[").append(min).append(",").append(max).append("]，已入账并标记异常");
        } else {
            reason.append("温度已入账");
        }
        if (gap) {
            reason.append("；本条距上一条已 ").append(gapSeconds).append(" 秒（额定 ").append(interval)
                    .append(" 秒），中间未采集到数据，已标记断档");
        }
        return written(entity.getId(), reason.toString());
    }

    /** 门店签收：到达后才能签收，一单只签一次；签收动作本身照常上链 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestReceipt(DeviceIngestRequests.DeviceReceipt request) {
        ColdChainTransport transport = resolveTransport(request.getTransportId(), request.getTransportNo());
        if (transport == null) {
            return manual("运输单号在冷链运单中不存在（" + request.getTransportNo() + "），请人工核对");
        }
        if (!StringUtils.hasText(request.getReceiver())) {
            return manual("缺少签收人，责任转移必须落具体人，请人工确认后补录");
        }
        // 幂等键是「运单 + 门店」：一个车次送四家店就是四次货权转移，各签各的；
        // 同一家店在同一车次上仍只能签一次 —— 放开的是站点数，不是签收次数。
        // storeId 缺失（老 PDA 只报店名）时按店名兜底：不能写成「store_id = null」，
        // 那个条件在 SQL 里永不命中，幂等会静默失效、同一家店能被签两次。
        List<StoreReceipt> signedList = receiptMapper.selectList(Wrappers.<StoreReceipt>lambdaQuery()
                .eq(StoreReceipt::getTransportId, transport.getId()));
        boolean alreadySigned = signedList.stream().anyMatch(r -> request.getStoreId() != null
                ? Objects.equals(r.getStoreId(), request.getStoreId())
                : Objects.equals(r.getStoreName(), request.getStoreName()));
        if (alreadySigned) {
            // 确定性错误：不该重推，重推也永远不会成功
            return rejected("运单 " + transport.getTransportNo() + " 在门店「" + request.getStoreName()
                    + "」已签收，重复签收已拒绝");
        }
        if (!Integer.valueOf(STATUS_ARRIVED).equals(transport.getStatus())) {
            return manual("运单 " + transport.getTransportNo() + " 尚未到达门店，暂不能签收");
        }

        StoreReceipt entity = new StoreReceipt();
        entity.setTransportId(transport.getId());
        entity.setStoreId(request.getStoreId());
        entity.setStoreName(request.getStoreName());
        entity.setReceiver(request.getReceiver());
        entity.setReceiverPhone(request.getReceiverPhone());
        entity.setReceiptTime(LocalDateTime.now());
        entity.setQtyCheck(request.getQtyCheck() == null ? 1 : request.getQtyCheck());
        entity.setQtyDiffNote(request.getQtyDiffNote());
        entity.setTempCheck(request.getTempCheck() == null ? 1 : request.getTempCheck());
        entity.setTempValue(request.getTempValue());
        entity.setPackageIntact(request.getPackageIntact() == null ? 1 : request.getPackageIntact());
        entity.setESignature(request.getSignature());
        entity.setSource(SOURCE_DEVICE);
        entity.setSourceRef(request.getSourceRef());
        entity.setRawPayload(request.getRawPayload());
        entity.setCreateTime(LocalDateTime.now());
        entity.setContentHash(HashUtil.sha256Json(request));
        receiptMapper.insert(entity);

        // 状态推进：**所有站点都签完**才算「已签收」。一车四店签了三家仍是「待签收」——
        // 剩下一家的货权还没转移，溯源链上不能提前闭环。
        // 站点数以装载明细里出现过的门店为准（它才是权威；stop_count 只是展示用快照）。
        long stops = Math.max(1, transportItemMapper.selectList(Wrappers.<TransportItem>lambdaQuery()
                        .eq(TransportItem::getTransportId, transport.getId())).stream()
                .map(TransportItem::getStoreName).filter(StringUtils::hasText).distinct().count());
        if (signedList.size() + 1 >= stops) {
            transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                    .eq(ColdChainTransport::getId, transport.getId())
                    .eq(ColdChainTransport::getStatus, STATUS_ARRIVED)
                    .set(ColdChainTransport::getStatus, 4));
        }
        // 签收动作照常上链；带上 source 便于链上区分人工签收与 PDA 自动签收
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transportNo", transport.getTransportNo());
        payload.put("source", SOURCE_DEVICE);
        if (StringUtils.hasText(request.getSourceRef())) payload.put("sourceRef", request.getSourceRef());
        chainEvents.publish(ChainTxMessage.create("STORE_RECEIPT", entity.getId(), transport.getTransportNo(),
                entity.getContentHash(), payload));
        return written(entity.getId(), "门店签收已入账");
    }

    /**
     * 胴体批次归集（屠宰线胴体称重分拣台）：设备只报单头白条，批次边界由系统算。
     * <p>
     * 归集键是<b>「屠宰场 + 屠宰日」</b>，但只有「归集中」的批次会被并入：
     * 当天同场第一头白条落下来时开一个新批次（CB+日期-当日流水，系统生成），
     * 之后同一场同一日的白条一律并进去，直到<b>达到头数上限自动封批</b>（或换班/换圈时人工封批），
     * 此后的白条另开一批。次日自然开新批，现场零额外操作。
     * <p>
     * <b>为什么要上限</b>：真实屠宰场日屠宰量几百到几千头，不设上限就是「一天一场一个巨型批次」——
     * 出了问题只知道在那几千头里，召回无从下手。批次必须有边界，否则它就不是批次。
     * <p>
     * 写入前三道校验全在 {@link BatchGuard}：瘦肉精 → 盖章 → 未被占用。
     * 其中「已被占用」不算错误——那是设备重推同一头白条，直接回既有批次；
     * 让现场看到一堆「该猪已归批」的红字、却什么也不用做，只会让人下次直接无视告警。
     * <p>
     * 归集只在设备来源的批次之间进行：人工建的批次是人的分组，设备归集不去动它，
     * 否则「系统按屠宰场 + 屠宰日归集」这件事会被人的历史分组搅乱。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestBatch(DeviceIngestRequests.DeviceBatch request) {
        Long pigId = request.getPigId();
        if (pigId == null) {
            return manual("耳标未匹配到生猪档案，无法自动归集，请人工核对");
        }
        if (!StringUtils.hasText(request.getSlaughterhouse())) {
            return manual("未提供屠宰场，无法确定归集批次（归集口径是「屠宰场 + 屠宰日」）");
        }
        if (request.getCarcassWeightKg() == null || request.getCarcassWeightKg().signum() <= 0) {
            return rejected("分拣台未输出有效胴体重量，禁止入库");
        }

        // ① 瘦肉精（阳性 / 同批阳性 / 未检）：与人工入口同一口径，判定在屠宰服务
        String blocked = batchGuard.ractopamineBlockedSummary(List.of(pigId));
        if (StringUtils.hasText(blocked)) {
            return rejected("该猪不得进入胴体批次——" + blocked
                    + "（瘦肉精阳性须隔离处理并按规定上报；没做过检测的须先补做批批检）");
        }
        // ② 胴体盖章：章的放行凭证意义见 StampGuard
        String unstamped = batchGuard.unstampedSummary(List.of(pigId));
        if (StringUtils.hasText(unstamped)) {
            return rejected("该猪不得进入胴体批次——" + unstamped
                    + "（盖章发生在宰后同步检疫之后，须先完成宰后检验并判合格）");
        }
        // ③ 已被占用：设备重推同一头白条，回既有批次，不新建也不重复追加
        CarcassBatch occupied = batchGuard.findOccupied(pigId, null);
        if (occupied != null) {
            return written(occupied.getId(), "该猪已归入胴体批次 " + occupied.getBatchNo() + "，本次未重复入批");
        }

        LocalDate today = LocalDate.now();
        CarcassBatch batch = findOpenBatch(request.getSlaughterhouse(), today);
        boolean created;
        if (batch != null && carcassBatchMapper.appendPig(batch.getId(), pigId, request.getCarcassWeightKg()) > 0) {
            created = false;
        } else {
            // 没有归集中的批次；或刚好被并发封批（appendPig 影响 0 行），都走新建
            batch = createBatch(request, pigId, today);
            created = true;
        }
        linkPig(batch.getId(), pigId, request.getCarcassWeightKg());

        // 达到头数上限 → 自动封批。与追加同一事务：要么「加了猪且封了批」，要么都不发生。
        carcassBatchMapper.sealIfFull(batch.getId(), maxPigsPerBatch);

        // 白条成型即生猪已完成屠宰（失败不阻塞业务，与人工建批一致）
        breedingClient.advanceStatus(pigId, 3);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchNo", batch.getBatchNo());
        payload.put("pigId", pigId);
        payload.put("carcassWeightKg", request.getCarcassWeightKg());
        payload.put("action", created ? "AUTO_GROUP_NEW" : "AUTO_GROUP_APPEND");
        payload.put("source", SOURCE_DEVICE);
        if (StringUtils.hasText(request.getSourceRef())) payload.put("sourceRef", request.getSourceRef());
        publish("CARCASS_BATCH", batch.getId(), batch.getBatchNo() + ":" + payload.get("action") + ":" + pigId,
                HashUtil.sha256Json(payload), payload);

        return written(batch.getId(), created
                ? "已自动建立胴体批次 " + batch.getBatchNo() + "（同日同场后续白条将并入该批次）"
                : "已归入胴体批次 " + batch.getBatchNo());
    }

    /** 当日同场、仍在归集中的设备批次（有且最多一条：满了或被封就另开） */
    private CarcassBatch findOpenBatch(String slaughterhouse, LocalDate day) {
        return carcassBatchMapper.selectOne(Wrappers.<CarcassBatch>lambdaQuery()
                .eq(CarcassBatch::getSlaughterhouse, slaughterhouse)
                .eq(CarcassBatch::getSource, SOURCE_DEVICE)
                .eq(CarcassBatch::getStatus, BATCH_OPEN)
                .ge(CarcassBatch::getCreateTime, day.atStartOfDay())
                .lt(CarcassBatch::getCreateTime, day.plusDays(1).atStartOfDay())
                .orderByDesc(CarcassBatch::getId)
                .last("LIMIT 1"));
    }

    /**
     * 开一个新批次，首头白条直接写进去。
     * 批号撞唯一键说明并发下另一个请求抢了同一个序号，换个序号重试即可。
     */
    private CarcassBatch createBatch(DeviceIngestRequests.DeviceBatch request, Long pigId, LocalDate day) {
        for (int skip = 0; skip < BATCH_NO_RETRY; skip++) {
            CarcassBatch batch = new CarcassBatch();
            batch.setBatchNo(carcassBatchNo.next(day, skip));
            batch.setPigIds(new ArrayList<>(List.of(pigId)));
            batch.setPigCount(1);
            batch.setStatus(BATCH_OPEN);
            batch.setTotalWeightKg(request.getCarcassWeightKg());
            batch.setSlaughterhouse(request.getSlaughterhouse());
            batch.setSource(SOURCE_DEVICE);
            batch.setSourceRef(request.getSourceRef());
            batch.setRawPayload(request.getRawPayload());
            batch.setNote("分拣台按「屠宰场 + 屠宰日」自动归集；总重量为胴体过磅合计，随白条下线逐头累加；"
                    + "达到 " + maxPigsPerBatch + " 头自动封批，换班或换圈可由人工提前封批");
            batch.setCreateTime(LocalDateTime.now());
            try {
                carcassBatchMapper.insert(batch);
                return batch;
            } catch (DuplicateKeyException e) {
                log.warn("胴体批次号 {} 已被占用，换下一个序号重试", batch.getBatchNo());
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "生成胴体批次号失败，请稍后重试");
    }

    /**
     * 写关联行——「一头猪只能归属一个批次」的最终防线是这条记录上的唯一键。
     * <p>
     * 前面的 {@code findOccupied} 只是快速路径：两条分拣线同时上报同一头白条时，
     * 两边都可能通过检查，真正把第二次挡下来的是这里。撞键必须让整个事务回滚
     * （否则会留下「数组里加了、但关联表没有」的批次），重推一次就会命中「已归入既有批次」。
     */
    private void linkPig(Long batchId, Long pigId, BigDecimal weightKg) {
        CarcassBatchPig link = new CarcassBatchPig();
        link.setBatchId(batchId);
        link.setPigId(pigId);
        link.setWeightKg(weightKg);
        link.setCreateTime(LocalDateTime.now());
        try {
            carcassBatchPigMapper.insert(link);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "同一头白条被并发上报（生猪ID" + pigId + "），本次未入批，请重推——重推会回到它已归属的批次");
        }
    }

    /**
     * 分割作业（分割线扫码称重台）：<b>一次上报 = 一头白条的整份产出清单</b>。
     * <p>
     * 与原来的差别在于「作业」这一层被显式建模了：作业是主、部位是明细。作业号由系统生成
     * （SV + 当日流水），部位必须落在 {@code meat_part} 字典里——设备的职责是「切了什么、多重」，
     * 不是「这批叫什么、编号多少」；部位编码是人手编不对、也不该由一台秤决定的东西。
     * <p>
     * 作业一落库就做出肉率对账（必出项 / 总重上限 / 总重下限），但结论只影响
     * {@code check_status}（转人工复核），<b>不影响是否入账</b>：分割事实已经发生，
     * 不入账等于把现场数据弄丢——与瘦肉精阳性记录照常入账留证同一个口径。
     * <p>
     * 兼容：{@code items} 为空时退化为单条（读顶层的 partCode/productName/weightKg），
     * 老设备报文与演示表单无需改造即可继续工作。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestSplit(DeviceIngestRequests.DeviceSplit request) {
        CarcassBatch parent = carcassBatchMapper.selectOne(Wrappers.<CarcassBatch>lambdaQuery()
                .eq(CarcassBatch::getBatchNo, request.getParentBatchNo()).last("LIMIT 1"));
        if (parent == null) {
            return manual("胴体批次「" + request.getParentBatchNo() + "」不存在，分割作业必须挂在已存在的胴体批次下");
        }

        // ① 归一化产出清单：items 优先；为空则把顶层单条字段包装成一条
        List<DeviceIngestRequests.SplitItem> raw = request.getItems();
        if (raw == null || raw.isEmpty()) {
            DeviceIngestRequests.SplitItem single = new DeviceIngestRequests.SplitItem();
            single.setPartCode(request.getPartCode());
            single.setPartName(request.getProductName());
            single.setWeightKg(request.getWeightKg());
            single.setPackageCount(request.getPackageCount());
            single.setPackageType(request.getPackageType());
            raw = List.of(single);
        }

        // ② 逐项定型：部位必须落到字典，重量必须为正，同一作业内不得重复同一部位
        List<SplitYieldCheck.Line> lines = new ArrayList<>(raw.size());
        for (DeviceIngestRequests.SplitItem item : raw) {
            MeatPart part = meatPartResolver.resolve(item.getPartCode(), item.getPartName());
            if (part == null) {
                String label = StringUtils.hasText(item.getPartCode()) ? item.getPartCode() : item.getPartName();
                return manual("部位「" + (label == null ? "（未填）" : label)
                        + "」不在部位字典内，请核对部位编码后重报——分割部位必须取自标准部位清单");
            }
            if (item.getWeightKg() == null || item.getWeightKg().signum() <= 0) {
                return rejected("部位「" + part.getPartName() + "」称重台未输出有效重量，禁止入库");
            }
            if (lines.stream().anyMatch(l -> l.partCode().equals(part.getPartCode()))) {
                return rejected("本次作业中部位「" + part.getPartName() + "」出现多次，请合并后重报");
            }
            lines.add(new SplitYieldCheck.Line(part.getPartCode(), part.getPartName(), item.getWeightKg()));
        }

        // ③ 对账基准：带耳标就按那头白条的精确过磅值，否则退到批次单头均重；都没有则不做重量对账
        String basisSource = BASIS_NONE;
        BigDecimal parentWeight = null;
        if (request.getPigId() != null) {
            CarcassBatchPig link = carcassBatchPigMapper.selectOne(Wrappers.<CarcassBatchPig>lambdaQuery()
                    .eq(CarcassBatchPig::getPigId, request.getPigId()).last("LIMIT 1"));
            if (link == null || !parent.getId().equals(link.getBatchId())) {
                return manual("该白条不在胴体批次「" + parent.getBatchNo() + "」内，请核对分割的是哪一批后重报");
            }
            if (link.getWeightKg() != null) {
                basisSource = BASIS_PIG;
                parentWeight = link.getWeightKg();
            }
        }
        if (parentWeight == null) {
            Integer pigCount = parent.getPigCount();
            if (parent.getTotalWeightKg() != null && pigCount != null && pigCount > 0) {
                basisSource = BASIS_BATCH_AVG;
                parentWeight = parent.getTotalWeightKg()
                        .divide(BigDecimal.valueOf(pigCount), 2, RoundingMode.HALF_UP);
            }
        }

        LocalDateTime now = LocalDateTime.now();

        // ④ 作业主记录（作业号当日流水，撞唯一键换下一个序号）
        SplitJob job = new SplitJob();
        job.setParentBatchId(parent.getId());
        job.setParentBatchNo(parent.getBatchNo());
        job.setPigId(request.getPigId());
        job.setEarTagNo(request.getEarTagNo());
        // 分割操作员是人（现场操作分割线终端），拿不到就如实记「未记录」，不得用设备名顶替
        job.setOperator(StringUtils.hasText(request.getOperator()) ? request.getOperator() : "未记录");
        job.setWorkshop(request.getWorkshop());
        job.setLineNo(request.getLineNo());
        job.setWorkshopTemp(request.getWorkshopTemp());
        job.setItemCount(lines.size());
        job.setParentWeightKg(parentWeight);
        job.setWeightBasis(basisSource);
        job.setSplitTime(now);
        job.setSource(SOURCE_DEVICE);
        job.setSourceRef(request.getSourceRef());
        job.setRawPayload(request.getRawPayload());
        job.setContentHash(HashUtil.sha256Json(request));
        job.setCheckStatus(0);
        job.setCreateTime(now);
        insertJobWithGeneratedNo(job, now);

        // ⑤ 展开为逐条部位记录：部位号挂在作业号下（SP20261009-001-01），一眼看出属于哪份作业
        String itemNoPrefix = job.getJobNo().replaceFirst("^SV", "SP");
        for (int i = 0; i < lines.size(); i++) {
            SplitYieldCheck.Line line = lines.get(i);
            DeviceIngestRequests.SplitItem item = raw.get(i);
            SplitBatch entity = new SplitBatch();
            entity.setBatchNo(itemNoPrefix + String.format("-%02d", i + 1));
            entity.setJobId(job.getId());
            entity.setParentBatchId(parent.getId());
            entity.setSplitLevel(1);
            entity.setPartCode(line.partCode());
            entity.setProductName(line.partName());
            entity.setWeightKg(line.weightKg());
            entity.setPackageCount(item.getPackageCount() == null ? 1 : item.getPackageCount());
            entity.setPackageType(item.getPackageType());
            entity.setSplitTime(now);
            entity.setWorkshop(request.getWorkshop());
            entity.setWorkshopTemp(request.getWorkshopTemp());
            entity.setOperator(job.getOperator());
            entity.setSource(SOURCE_DEVICE);
            entity.setSourceRef(request.getSourceRef());
            entity.setRawPayload(request.getRawPayload());
            entity.setContentHash(HashUtil.sha256Json(item));
            entity.setCreateTime(now);
            splitBatchMapper.insert(entity);
            publish("SPLIT_BATCH", entity.getId(), entity.getBatchNo(), entity.getContentHash(),
                    Map.of("batchNo", entity.getBatchNo(), "jobNo", job.getJobNo(), "partCode", line.partCode()));
        }

        // ⑥ 出肉率对账：结论只写进作业，不改变「是否入账」
        SplitYieldCheck.Conclusion conclusion = splitYieldCheck.check(lines, parentWeight);
        SplitJob patch = new SplitJob();
        patch.setId(job.getId());
        patch.setTotalWeightKg(conclusion.totalWeightKg());
        patch.setTotalYieldRate(conclusion.totalYieldRate());
        patch.setLeanYieldRate(conclusion.leanYieldRate());
        patch.setCheckStatus(conclusion.status());
        patch.setCheckNote(conclusion.note());
        splitJobMapper.updateById(patch);

        // ⑦ 作业上链（部位记录各自也上链，保持逐条可校验）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobNo", job.getJobNo());
        payload.put("parentBatchNo", parent.getBatchNo());
        payload.put("itemCount", lines.size());
        payload.put("items", lines.stream().map(l -> l.partCode() + "=" + l.weightKg()).toList());
        payload.put("totalWeightKg", conclusion.totalWeightKg());
        payload.put("parentWeightKg", parentWeight);
        payload.put("weightBasis", basisSource);
        payload.put("checkStatus", conclusion.status());
        publish("SPLIT_JOB", job.getId(), job.getJobNo(), job.getContentHash(), payload);

        StringBuilder reason = new StringBuilder("分割作业 ").append(job.getJobNo())
                .append(" 已创建，本次切出 ").append(lines.size()).append(" 个部位");
        if (conclusion.status() == SplitYieldCheck.MANUAL) {
            reason.append("；出肉率对账待人工复核：").append(conclusion.note());
        }
        return written(job.getId(), reason.toString());
    }

    /**
     * 装车发运：1 号装车位上的三件套（车牌识别相机 + 司机刷卡终端 + 扫码枪）共同构成这一环。
     * <p>
     * <b>为什么不让设备直接建单</b>：运单的目的地门店是经营决策，设备无从采集；
     * 而车牌、司机、装了几批货是现场客观事实。所以这里的做法是「拿事实倒查计划」——
     * 扫到的批次属于哪张待发货计划，就发到那张计划的目的地去。扫码是<b>确认</b>不是<b>选择</b>。
     * <p>
     * <b>司机在装车口允许留空</b>（2026-10-10 起）：现场只在上车那一刻刷一次卡，
     * 司机本人这时还没上车，装车口也没有读卡器（报了卡号由接入层按结构性拒绝挡下）。
     * 因此这里最多能拿到明文姓名（人工预填 / 调度代填），来源只能是非本人确认；
     * 「本人确认」全部发生在发车那端（{@link #ingestDepart} 按卡号换人）。
     * 缺司机<b>不转人工</b>、照常建单 —— 中间这段空档是如实记录，责任分界点挪到了发车。
     * <p>
     * <b>同一趟车继续装货要并成一张运单</b>（2026-10-10 起）：现场的货是人来一批、扫一批的，
     * 同一车牌短时间内再次扫码不是新的一趟车 —— 系统把这批货<b>追加到那张还没发车的运单上</b>，
     * 站点按门店归并（同一个店不会因为分两次搬上车就被算成两站）。已经发走的运单不在候选里，
     * 所以「第一车开走了、第二车再来」的分车语义不受影响；窗口之外挂着的老单也不并（异常滞留交人工）。
     * <p>
     * 一路上的判断顺序：批次存在 → 没被别的运单发运过 → 属于<b>唯一一张</b>可发货计划 →
     * 车牌补齐或校验一致 → 复用人工那条 {@code generateTransportFromPlan} 生成运单；
     * 若这台车<b>还有一张没发车的装车口运单</b>，本批货追加进那一张（同一趟车继续装），
     * 不再开新单 —— 见 {@code DistributionServiceImpl#findOpenLoadTransport} 与
     * {@link #ingestDepart} 上方的口径说明。
     * 任何一步说不清（不属于任何计划、分属多张计划、车牌与计划安排不一致）
     * 都返回「待人工」而不是拒绝：可能是计划还没建、或者真装错车了，都要留给人处理，
     * 但绝不能默认正常放过去 —— 猜错目的地就是货发错店。
     * <p>
     * <b>这里刻意不加 @Transactional</b>：本方法自身不写任何业务表，写入全部发生在
     * {@code generateTransportFromPlan}（它自己有事务）。如果套一层事务再 catch 内层抛出的业务异常，
     * Spring 已经把外层事务标记成 rollback-only，方法返回后提交时会抛 UnexpectedRollbackException，
     * 精心翻译好的「转人工」就被吞成了「系统内部异常」——回归里真实踩到过。
     */
    public Map<String, Object> ingestLoad(DeviceIngestRequests.DeviceLoad request) {
        List<String> batchNos = request.getBatchNos().stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (batchNos.isEmpty()) {
            return rejected("装车口未扫到任何分割批次号，无法确认装的是什么货");
        }
        // 装车口没有读卡器：报了卡号说明设备接错点位（把驾驶室里的读卡器算到了装车位），
        // 不解析、不入账。接入层（LoadChannelHandler.validate）已按结构性拒绝挡过一道，
        // 这里是直接调内部接口那条路径的兜底。
        if (StringUtils.hasText(request.getCardNo())) {
            return rejected("装车口没有读卡器，报文不应携带司机卡号："
                    + "司机身份由车载终端在发车时刷卡确认");
        }

        // ① 扫到的标签必须都对得上真实批次：扫错标签、扫到已作废批次都在这里拦下
        List<SplitBatch> batches = new ArrayList<>(batchNos.size());
        for (String batchNo : batchNos) {
            SplitBatch batch = splitBatchMapper.selectOne(Wrappers.<SplitBatch>lambdaQuery()
                    .eq(SplitBatch::getBatchNo, batchNo).last("LIMIT 1"));
            if (batch == null) {
                return manual("分割批次「" + batchNo + "」不存在或已作废，无法确认装的是什么货，请人工核对");
            }
            batches.add(batch);
        }
        List<Long> batchIds = batches.stream().map(SplitBatch::getId).toList();

        // ② 已经由运单发运过的批次：禁止重复装车。同一批货装两次是实质业务事故，
        //    和「同一份报文重复上报」不是一回事 —— 后者在前面按设备+报文就挡掉了。
        List<TransportItem> shipped = transportItemMapper.selectList(Wrappers.<TransportItem>lambdaQuery()
                .in(TransportItem::getSplitBatchId, batchIds));
        if (!shipped.isEmpty()) {
            return rejected("分割批次 " + shipped.get(0).getBatchNo()
                    + " 已由运单发运过，禁止重复装车（如确需重发请人工核对后处理）");
        }

        // ③ 司机：装车口**允许留空**，也不再有「刷卡」这条路。
        //    现场只在上车那一刻刷一次卡（车载终端 DEPART），司机本人在装车时还没上车，
        //    装车口物理上也没有读卡器（报了卡号在接入层就按结构性拒绝挡下）。
        //    所以这里能拿到的只有明文姓名（人工预填 / 调度代填），来源只能是非本人确认。
        //    <b>「货要走了，责任必须落到具体人」这条红线没有破，它挪到了发车</b>：
        //    司机上车刷卡才是本人确认的唯一来源，`depart` 也会拦住没有司机的运单 ——
        //    中间这段空档是如实记录，不是放过。
        String driverName = request.getDriverName();
        // 装车口能自报的只有「人脸」（将来若在装卸位加了实名终端）；其余一律保守记
        // 「人工录入、非本人确认」—— CARD 在这里已经不可能出现，PLATE_INFERRED 只由发车端产生。
        // **没名字就别报来源**：司机留空是这里的常态，配上 MANUAL 等于假装有人录过。
        String driverSource = !StringUtils.hasText(driverName) ? null
                : (DriverSource.FACE.equals(request.getDriverSource())
                        ? DriverSource.FACE : DriverSource.MANUAL);

        // ④ 倒查计划：扫到的货按「所属计划」分组 —— 一组 = 一家门店 = 一个站点。
        //    以前「分属多张计划」直接转人工（一车只能发一张计划）；一车多目的地之后，
        //    分属多张计划恰恰是**正常情况**：这就是一辆车顺路送几家店。
        //    **红线没有破**：每一批货的门店归属仍然由它所属的计划定死，系统一次都没有猜目的地。
        //    <b>只认「还在等装车」的计划</b>（待发货 1 / 部分排车 4）—— 与「可发运批次」的排除口径
        //    （DistributionServiceImpl.EXCLUDE_TAKEN_BY_PENDING_PLAN）和人工建单的占用判定同一份语义。
        //    漏掉这一道会出真事：计划被取消后，它的未排车明细（shipped 仍为 0）留在表里，
        //    批次已按规则释放回待发运池；重新排一张计划再扫码时，同一批次就被算成「挂在两张计划上」
        //    → 永久转人工，这批货再也发不出去。已取消的计划不该有「货权」，它的死明细不参与判定。
        List<DeliveryPlanItem> items = deliveryPlanItemMapper.selectList(Wrappers.<DeliveryPlanItem>lambdaQuery()
                .in(DeliveryPlanItem::getSplitBatchId, batchIds)
                .eq(DeliveryPlanItem::getShipped, 0)
                .inSql(DeliveryPlanItem::getPlanId, PLAN_ACTIVE_SQL));
        if (items.isEmpty()) {
            return manual("扫到的货物（" + String.join("、", batchNos) + "）不属于任何待发货计划，"
                    + "无法确定该发去哪家店。装车口不猜目的地，请先建发货计划后重新扫码，或走人工建单");
        }
        // 同一批次挂在两张计划的未排车明细上属于台账冲突（计划侧本该拦住），交人工，不擅自选一个
        Map<Long, Long> batchOwner = new LinkedHashMap<>();
        for (DeliveryPlanItem item : items) {
            Long prev = batchOwner.put(item.getSplitBatchId(), item.getPlanId());
            if (prev != null && !prev.equals(item.getPlanId())) {
                return manual("分割批次 " + item.getSplitBatchId() + " 同时挂在两张计划的未排车明细上，请人工核对");
            }
        }
        // 站点顺序 = 扫码顺序：按 batchNos 的先后，取每个批次所属计划第一次出现的位置。
        // 「按站装车」时扫码顺序就是卸货顺序，所以站点序号由它直接决定，设备不必知道目的地。
        Map<Long, Integer> planSeq = new LinkedHashMap<>();
        for (SplitBatch batch : batches) {
            Long planId = batchOwner.get(batch.getId());
            if (planId == null) {
                return manual("分割批次「" + batch.getBatchNo() + "」没有挂任何待发货计划，无法确定发去哪家店");
            }
            planSeq.computeIfAbsent(planId, k -> planSeq.size() + 1);
        }
        Map<Long, DeliveryPlan> plans = new LinkedHashMap<>();
        for (Long planId : planSeq.keySet()) {
            DeliveryPlan p = deliveryPlanMapper.selectById(planId);
            if (p == null) {
                return manual("扫到的货物挂着一条已不存在的发货计划，请人工核对后重新建计划");
            }
            if (!Integer.valueOf(PLAN_PENDING).equals(p.getStatus())
                    && !Integer.valueOf(PLAN_PARTIAL).equals(p.getStatus())) {
                return manual("发货计划 " + p.getPlanNo() + " 当前状态不允许发货，请人工核对");
            }
            plans.put(planId, p);
        }

        // ⑤ 车型与载重：取「车牌 → 车辆备案」。备案不是准入门槛（新车、外调车天天有），
        //    查不到就只影响车型展示与载重判断，一样放行。
        String vehicleType = plans.values().stream().map(DeliveryPlan::getVehicleType)
                .filter(StringUtils::hasText).findFirst().orElse(null);
        Vehicle vehicle = StringUtils.hasText(request.getVehicleNo())
                ? vehicleMapper.selectOne(Wrappers.<Vehicle>lambdaQuery()
                        .eq(Vehicle::getVehicleNo, request.getVehicleNo().trim())
                        .eq(Vehicle::getStatus, 1).last("LIMIT 1"))
                : null;
        if (!StringUtils.hasText(vehicleType) && vehicle != null) {
            vehicleType = vehicle.getVehicleType();
        }

        // ⑥ 超载：超载是**既成事实的违规**（同瘦肉精那种硬判据，不属于"可以优化的负载率"），
        //    所以超了就转人工，既不自动凑满一车、也不放行。载重备案查不到就判不了，不拦。
        if (vehicle != null && vehicle.getLoadCapacityKg() != null && vehicle.getLoadCapacityKg().signum() > 0) {
            BigDecimal loadTotal = batches.stream().map(SplitBatch::getWeightKg)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (loadTotal.compareTo(vehicle.getLoadCapacityKg()) > 0) {
                return manual("本车装载 " + loadTotal + "kg 已超过核定载重 " + vehicle.getLoadCapacityKg()
                        + "kg（" + request.getVehicleNo() + "），超载不得放行，请卸货或换车后重新扫码");
            }
        }

        // ⑦ 车与人是现场读到的：计划为空则补齐，与计划安排不一致会抛业务异常 → 转人工核对
        try {
            ColdChainTransport transport = distributionService.generateTransportFromPlans(
                    new ArrayList<>(planSeq.keySet()), batchIds,
                    new DistributionRequests.LoadSite(request.getVehicleNo(), vehicleType, driverName,
                            request.getDriverPhone(), driverSource, request.getSourceRef(), request.getRawPayload()));
            String destinationText = plans.size() == 1
                    ? plans.values().iterator().next().getStoreName()
                    : "途经 " + plans.size() + " 站（" + String.join("、",
                            plans.values().stream().map(DeliveryPlan::getStoreName).toList()) + "）";
            String driverText = StringUtils.hasText(transport.getDriverName())
                    ? transport.getDriverName() + "（" + DriverSource.labelOf(transport.getDriverSource()) + "）"
                    : "未记录（司机上车刷卡后确认）";
            boolean merged = Boolean.TRUE.equals(transport.getMergedIntoExisting());
            return written(transport.getId(), (merged ? "装车口把这批货并入运单 " : "装车口自动生成运单 ")
                    + transport.getTransportNo()
                    + "：本次装车 " + batches.size() + " 批货发往 " + destinationText
                    + "，车牌 " + transport.getVehicleNo() + "，司机 " + driverText
                    + (merged ? "（该车还没发车，继续装的是同一趟）" : ""));
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) throw e;
            // 车牌/司机与计划不一致、计划已无待发货物等，都交人工判断，不硬塞也不丢弃
            return manual("装车口自动建单未通过：" + e.getMessage());
        } catch (Exception e) {
            log.warn("装车口自动建单异常 batches={} err={}", String.join("、", batchNos), e.getMessage());
            return manual("装车口自动建单异常：" + e.getMessage() + "，请转人工处理");
        }
    }

    /**
     * 发车确认（车载终端刷卡）。
     * <p>
     * 装车口已经能自动建单，但建完停在「待发车」，发车还要人去点按钮 —— 自动化断在最后一步。
     * 这条补的就是这一步：<b>司机上车刷卡 → 系统按车牌倒查该车名下「待发车」的运单 → 推进为「运输中」</b>。
     * 与装车口同一套设计：设备只报「我（某车）发车了」，运单号由系统倒查（刷卡是「确认」不是「选择」）。
     * <p>
     * 发车适合设备化，因为它是<b>物理事实</b>（道闸抬杆 / 地磅出场 / 点火都能作证，人反而会忘了点），
     * 而且<b>不产生任何业务判定</b>：哪辆车发、发给谁，早在运单里定死了。
     * <p>
     * <b>三条不猜的红线</b>：① 该车牌名下没有待发车运单 → 转人工（不能替人建单、更不能发一趟没装的货）；
     * ② 同时有<b>多张</b>待发车运单 → 转人工（不敢猜是哪一趟）；③ 现场刷卡的人与运单上安排的人不一致
     * → 转人工（最可能的解释是上错车）。司机必须由卡号换出具体的人，换不出人一律转人工（「设备 ≠ 人」）。
     * <p>
     * 发车<b>不单独上链</b>：人工发车（{@code depart}）也没有上链，两条路径保持一致 ——
     * 运单本身已经在装车与签收两个节点上链了。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestDepart(DeviceIngestRequests.DeviceDepart request) {
        if (!StringUtils.hasText(request.getVehicleNo())) {
            return rejected("缺少车牌号，无法确定是哪一趟车");
        }
        String vehicleNo = request.getVehicleNo().trim();

        // ① 司机落到人：与装车口 ingestLoad 同一口径（卡号 → 备案；否则用报文姓名；
        //    两样都没有则车牌反查推定，但只是推定，仍旧转人工 —— 车牌号不能顶替责任人）
        String driverName = request.getDriverName();
        String driverSource = DriverSource.FACE.equals(request.getDriverSource())
                ? DriverSource.FACE : DriverSource.MANUAL;
        if (StringUtils.hasText(request.getCardNo())) {
            DriverCard card = driverCardMapper.selectOne(Wrappers.<DriverCard>lambdaQuery()
                    .eq(DriverCard::getCardNo, request.getCardNo().trim())
                    .eq(DriverCard::getStatus, 1).last("LIMIT 1"));
            if (card == null) {
                return manual("司机卡「" + request.getCardNo() + "」没有登记在司机备案里，无法确认持卡人是谁，"
                        + "请先在司机备案中登记该卡，或由司机改用姓名登记后重报");
            }
            Driver driver = driverMapper.selectById(card.getDriverId());
            if (driver == null || !Integer.valueOf(1).equals(driver.getStatus())) {
                return manual("司机卡「" + request.getCardNo() + "」绑定的司机已停用或不存在，请人工核对");
            }
            driverName = driver.getDriverName();
            driverSource = DriverSource.CARD;
        } else if (!StringUtils.hasText(driverName)) {
            String inferred = inferDriverByVehicle(vehicleNo);
            return manual(inferred == null
                    ? "现场没读到司机（未刷卡、也没有姓名）：车牌 " + vehicleNo
                      + " 在车辆备案里也查不到常用司机，请司机刷卡后重报或人工补录"
                    : "现场没读到司机。按车牌 " + vehicleNo + " 反查备案的常用司机是「" + inferred
                      + "」—— 这只是推定、未经本人确认，请司机刷卡确认，或人工核对后补录");
        }

        // ② 倒查该车牌名下的「待发车」运单 —— 设备不需要知道运单号
        List<ColdChainTransport> pendings = transportMapper.selectList(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getVehicleNo, vehicleNo)
                .eq(ColdChainTransport::getStatus, STATUS_PENDING)
                .orderByDesc(ColdChainTransport::getId));
        if (pendings.isEmpty()) {
            return manual("车牌 " + vehicleNo + " 名下没有待发车的运单，发不了这一趟。"
                    + "请先在装车口完成装车建单，或人工核对是否上错车");
        }
        if (pendings.size() > 1) {
            return manual("车牌 " + vehicleNo + " 名下同时有 " + pendings.size() + " 张待发车运单（"
                    + pendings.stream().map(ColdChainTransport::getTransportNo)
                            .collect(Collectors.joining("、"))
                    + "），无法确定这次发的是哪一趟，请人工核对");
        }
        ColdChainTransport transport = pendings.get(0);

        // ③ 现场刷卡的人必须与运单安排的人一致：不一致最可能的解释就是上错车了
        if (StringUtils.hasText(transport.getDriverName()) && !transport.getDriverName().equals(driverName)) {
            return manual("现场刷卡确认的司机「" + driverName + "」与运单 " + transport.getTransportNo()
                    + " 安排的「" + transport.getDriverName() + "」不一致，请人工核对是否上错车");
        }

        LocalDateTime departTime = request.getDepartTime() == null ? LocalDateTime.now() : request.getDepartTime();
        int changed = transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                .eq(ColdChainTransport::getId, transport.getId())
                .eq(ColdChainTransport::getStatus, STATUS_PENDING)
                .set(ColdChainTransport::getStatus, STATUS_IN_TRANSIT)
                .set(ColdChainTransport::getDepartTime, departTime)
                // 现场读到的人写回运单：原来是空的就是补齐；原来一致则把确认强度提上来
                //（从「调度代填」升到「本人刷卡」——这件事只有刷卡才能证明，事后追责看的就是它）
                .set(ColdChainTransport::getDriverName, driverName)
                .set(ColdChainTransport::getDriverSource, driverSource)
                .set(ColdChainTransport::getDriverConfirmed, DriverSource.confirmedFlag(driverSource)));
        if (changed == 0) {
            return manual("运单 " + transport.getTransportNo() + " 当前已不是「待发车」（可能刚被发走），请人工核对");
        }
        return written(transport.getId(), "车载终端刷卡确认发车：运单 " + transport.getTransportNo()
                + "，车牌 " + vehicleNo + "，司机 " + driverName + "（" + DriverSource.labelOf(driverSource)
                + "），发车时刻 " + departTime);
    }

    /**
     * 到达确认（车载终端，司机到站刷卡）。
     * <p>
     * 与发车（{@link #ingestDepart}）同一套设计：<b>设备只报现场事实 —— 哪台车、司机刷的是哪张卡、
     * 什么时刻</b>，运单由系统按车牌<b>倒查</b>，站点由系统按顺序<b>推导</b>。
     * <p>
     * <b>报文里没有「卸货门店」（2026-10-11 去掉）</b>：车载终端不是门店收货区的固定设备 ——
     * 车上装的是 T-Box、读卡头、显示屏，读得到车牌/卡号/时刻/GPS，<b>读不到门店编码</b>
     * （门店是 ERP/OMS 的主数据，不会下发到每台车上）。而这件事本来也不该由设备回答：
     * 车牌已经把运单锁定到唯一一张「未结束」的单子，站点清单由装车扫码顺序
     * （{@code transport_item.seq} → {@code transport_stop.stop_no}）决定，
     * 所以「当前第一个还没到达的站」在服务端是<b>唯一确定</b>的。
     * 报文既不需要报站，也就报不错站 —— 原来那两条靠门店自报的拦截（走错店、跳站）随之退场。
     * <p>
     * <b>代价要说清楚</b>：「车开到了清单外的店」不再有人拦。这个能力的正解是定位
     * （GPS / 电子围栏 —— 终端报经纬度、门店补坐标），不是让司机在驾驶室屏上敲门店编码。
     * <p>
     * <b>两条「条件没到位」的红线，一律 {@code manual} 而不是 {@code rejected}</b>：
     * ① 该车牌名下没有未结束的运单 → 转人工（还没发车就到了，或上错车）；
     * ② 同时有多张未结束的运单 → 转人工（不敢猜是哪一趟）。
     * 另外两种「无处可记」也一律转人工：站点台账为空、或所有站点都已到达（重复刷卡）。
     * 这些情况的共同点是「条件没到位」而不是「数据不成立」，拒绝会让外部系统据此删单
     * （与 {@link #ingestDepart} 同一条理由）。
     * <p>
     * <b>身份这三条分成两档判（2026-10-11）</b>，因为「该不该由人来收拾」不一样：
     * <ul>
     *   <li><b>没有卡号 / 卡换不出人（没备案、司机已停用）→ 转人工</b>：现场刷了卡，
     *       但系统这边缺备案或人已离职，这是<b>配置待补</b>，得有人去登记 —— 司机重刷也过不去；</li>
     *   <li><b>刷出来的人 ≠ 运单安排的司机 → 已拒绝</b>：从现场看就是「刷错卡 / 上错车」，
     *       <b>司机重刷即可自愈</b>，不需要调度去核对。原先判转人工时，这条堆在队列里的唯一出路
     *       也只有「拒绝」（放行等于承认刷错卡的人确认了到达），那一步不如由系统直接判掉；
     *       回执 {@code retryable=false} 也正好告诉车载终端「这条别重推、请重新刷卡」。</li>
     * </ul>
     * 运单没记司机时不判（无从比对）—— 发车刷卡会把司机写进运单，人工发车则可能为空。
     * <p>
     * ①②（认哪一趟）先判，再判卡号（谁确认的），最后才落到站点：先认出这次到的是哪一趟，
     * 才有「谁把它送到这一站」与「这是第几站」可言。
     * <p>
     * <b>刻意不加 @Transactional</b>：本方法自己不写业务表，写入全部发生在
     * {@code distributionService.markStopArrived}（它自己有事务），与 {@code ingestLoad} 同一个理由 ——
     * 套一层外层事务再处理内层结果，会在提交时抛 UnexpectedRollbackException，
     * 把精心翻译好的「转人工」吞成「系统内部异常」。
     */
    public Map<String, Object> ingestArrive(DeviceIngestRequests.DeviceArrive request) {
        if (!StringUtils.hasText(request.getVehicleNo())) {
            return rejected("缺少车牌号，无法确定是哪一趟车到了");
        }
        String vehicleNo = request.getVehicleNo().trim();

        // ① 倒查该车牌名下「还没结束」的运单（在途 2 / 待签收 3）—— 设备不需要知道运单号
        //    （刷卡发车已经把它推成 2；站点全部到齐后会转 3，那张单仍在跑，只是货都卸完了）
        //
        //    为什么不能只认 2：三站到齐后运单转「待签收」(3)，此时设备再报一次到达
        //    （外部重推，或司机多刷一下），若只查 2 就会答「名下没有运输中的运单」——
        //    这话会把人往「是不是还没发车」上带，而真实原因是「本趟已经全部到过了」。
        //    认「未结束」之后，「所有站点都已到达」这个分支才是可达的（否则是死代码）。
        List<ColdChainTransport> running = transportMapper.selectList(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getVehicleNo, vehicleNo)
                .in(ColdChainTransport::getStatus, STATUS_IN_TRANSIT, STATUS_ARRIVED)
                .orderByDesc(ColdChainTransport::getId));
        if (running.isEmpty()) {
            return manual("车牌 " + vehicleNo + " 名下没有在途的运单（车可能还没刷卡发车，或本趟已全部签收），"
                    + "认不出这次到的是哪一趟，请人工核对");
        }
        if (running.size() > 1) {
            return manual("车牌 " + vehicleNo + " 名下同时有 " + running.size() + " 张未结束的运单（"
                    + running.stream().map(ColdChainTransport::getTransportNo).collect(Collectors.joining("、"))
                    + "），无法确定这次到的是哪一趟，请人工核对");
        }
        ColdChainTransport transport = running.get(0);

        // ② 到的是哪一站，由<b>服务端推导</b>：就是「当前第一个还没到达的站」。
        //    报文不报门店，所以这里没有比对、也没有跳站一说 —— 站点顺序在服务端是排定好的
        //    （装车扫码顺序 → transport_stop.stop_no），设备只能顺着往下推，推不快也推不乱。
        List<TransportStop> stops = distributionService.stopsOfTransport(transport.getId());
        if (stops.isEmpty()) {
            return manual("运单 " + transport.getTransportNo() + " 没有站点记录，推不出车到了哪一站，请人工核对");
        }
        TransportStop next = null;
        for (TransportStop stop : stops) {   // stopsOfTransport 已按站点序号升序
            if (stop.getStatus() == null || stop.getStatus() < TransportStop.STOP_ARRIVED) {
                next = stop;
                break;
            }
        }
        if (next == null) {
            return manual("运单 " + transport.getTransportNo() + " 的 " + stops.size()
                    + " 个站点都已到达，本次刷卡无处可记（是不是重复刷了），请人工核对");
        }

        // ③ 到达必须由司机本人刷卡确认：卡号 → 备案 → 持卡人。
        //    「设备 ≠ 人」在这条通道上的落点 —— 车牌只说明是哪一趟车，说明不了是谁把货送到的；
        //    卡在司机手上、由车载终端读出，所以这一刷才是责任人。
        //    车载终端一定读得到卡，所以「没读到卡」就是司机没刷，不拿车牌或人名顶替。
        if (!StringUtils.hasText(request.getCardNo())) {
            return manual("到达必须由司机在车载终端刷卡确认：本次上报没有司机卡号，无法确认是谁把货送到并确认到站的。"
                    + "请司机刷卡后重报，或由门店在页面上人工确认到达");
        }
        DriverCard arriveCard = driverCardMapper.selectOne(Wrappers.<DriverCard>lambdaQuery()
                .eq(DriverCard::getCardNo, request.getCardNo().trim())
                .eq(DriverCard::getStatus, 1).last("LIMIT 1"));
        if (arriveCard == null) {
            return manual("司机卡「" + request.getCardNo() + "」没有登记在司机备案里，无法确认持卡人是谁。"
                    + "请先在司机备案中登记该卡，或由门店在页面上人工确认到达");
        }
        Driver arriveDriver = driverMapper.selectById(arriveCard.getDriverId());
        if (arriveDriver == null || !Integer.valueOf(1).equals(arriveDriver.getStatus())) {
            return manual("司机卡「" + request.getCardNo() + "」绑定的司机已停用或不存在，请人工核对");
        }
        String confirmDriver = arriveDriver.getDriverName();
        // 现场刷卡的人必须与运单安排的人一致：不一致最可能的解释就是上错车（与发车同一条口径）。
        // 这一条判「拒绝」而不是「转人工」（2026-10-11）：现场刷错卡/上错车是**司机重刷就能自愈**的事，
        // 不需要调度决策；而转人工堆着的每一条，人工的出路也只有「拒绝」（放行等于承认刷错卡的人
        // 确认了到达），那一步不如由系统直接判掉。回执里 retryable=false，正好告诉车载终端
        // 「这条别重推，请重新刷卡」—— 比起「等下再推」，这才是这条现场事实该得的答复。
        if (StringUtils.hasText(transport.getDriverName()) && !transport.getDriverName().equals(confirmDriver)) {
            return rejected("现场刷卡确认的司机「" + confirmDriver + "」不是运单 " + transport.getTransportNo()
                    + " 安排的「" + transport.getDriverName() + "」—— 这一刷不认，请核对是否上错车。"
                    + "由本趟安排的司机重新刷卡；若运单上的司机确实填错了，请先在运单上改正，"
                    + "或由门店在页面上人工确认到达");
        }

        LocalDateTime arriveTime = request.getArriveTime() == null ? LocalDateTime.now() : request.getArriveTime();
        int status = distributionService.markStopArrived(next.getId(), arriveTime, SOURCE_DEVICE,
                request.getSourceRef(), request.getRawPayload(), confirmDriver, DriverSource.CARD);
        if (status == 0) {
            return manual("运单已不存在，无法记录到达，请人工核对");
        }
        // 写的是「站点」不是「运单」：本通道的目标表是 transport_stop，回执里的记录号应当指得准
        return written(next.getId(), "车载终端刷卡确认到达：运单 " + transport.getTransportNo()
                + "，车牌 " + vehicleNo + "，第 " + next.getStopNo() + " 站（" + next.getStoreName()
                + "）到达时刻 " + arriveTime + "，司机 " + confirmDriver
                + "（" + DriverSource.labelOf(DriverSource.CARD) + "）"
                + (status >= STATUS_ARRIVED ? "；本趟车全部站点已到达" : "；后面还有站点没到"));
    }

    /**
     * 车牌反查「常用主驾」：只在「没有卡号、也没有姓名」时兜底，返回的只是<b>推定</b>。
     * <p>
     * 取的是 {@code driver_vehicle} 里 {@code is_primary=1} 且司机在职的那条；查不到返回 null。
     * 调用方拿到结果**不会直接放行**，而是写进「转人工」的回执里替操作员省一道查询 ——
     * 车牌号不能顶替责任人，这是项目红线。
     */
    private String inferDriverByVehicle(String vehicleNo) {
        if (!StringUtils.hasText(vehicleNo)) {
            return null;
        }
        DriverVehicle link = driverVehicleMapper.selectOne(Wrappers.<DriverVehicle>lambdaQuery()
                .eq(DriverVehicle::getVehicleNo, vehicleNo.trim())
                .eq(DriverVehicle::getIsPrimary, 1)
                .eq(DriverVehicle::getStatus, 1)
                .orderByAsc(DriverVehicle::getId).last("LIMIT 1"));
        if (link == null) {
            return null;
        }
        Driver driver = driverMapper.selectById(link.getDriverId());
        return driver == null || !Integer.valueOf(1).equals(driver.getStatus()) ? null : driver.getDriverName();
    }

    /** 作业号按当日流水生成：并发下可能算出同一个序号，撞唯一键就换下一个序号重试 */
    private void insertJobWithGeneratedNo(SplitJob job, LocalDateTime now) {
        LocalDate day = now.toLocalDate();
        for (int skip = 0; skip <= JOB_NO_RETRY; skip++) {
            job.setJobNo(splitJobNo.next(day, skip));
            try {
                splitJobMapper.insert(job);
                return;
            } catch (DuplicateKeyException e) {
                job.setId(null);
                log.warn("分割作业号 {} 已被占用，换下一个序号重试", job.getJobNo());
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "分割作业号生成失败（当日流水冲突），请稍后重试");
    }

    private void publish(String type, Long id, String bizNo, String hash, Map<String, Object> payload) {
        chainEvents.publish(ChainTxMessage.create(type, id, bizNo, hash, payload));
    }

    private ColdChainTransport resolveTransport(Long transportId, String transportNo) {        if (transportId != null) {
            ColdChainTransport byId = transportMapper.selectById(transportId);
            if (byId != null) return byId;
        }
        if (!StringUtils.hasText(transportNo)) return null;
        return transportMapper.selectOne(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getTransportNo, transportNo).last("LIMIT 1"));
    }

    /**
     * 分割车间监控快照：只落一张带时间戳的现场影像，不做任何业务判定。
     * <p>
     * <b>为什么这里几乎没有校验</b>：照片回答的是「此刻车间是什么样」，
     * 不回答「这头猪切出了什么部位」—— 它不挂在任何部位下、不参与溯源链、不进任何前置门槛。
     * 传入的 {@code fileId} 已由接入层调文件服务核对过真实存在，这里不再重复核验。
     * <p>
     * <b>唯一的校验是「绑定关系」</b>：报文若带了 {@code batchNo}（工位终端告知的「当前正在分割的
     * 白条属于哪个批次」），该批次就必须真实存在 —— 报一个系统里没有的批次号，等于编造一条
     * 「这张照片属于这次分割」的绑定，比不绑定更坏（页面会把它当某次分割的现场照片展示出来），
     * 直接判<b>已拒绝</b>。没带 batchNo 就不绑定（{@code batch_no} 留 NULL）。
     * <p>
     * 绑定解析到 {@code job_id}：一次分割上报 = 一头白条 = 一个分割作业，取该批次<b>最近一次</b>
     * 作业。作业号由系统生成、设备本就不该知道，所以相机只报批次号，作业由服务端认。
     * <p>
     * <b>滚动窗口</b>：落库后立刻淘汰该相机超出的最老快照（连同文件一起删）。
     * 淘汰放在同一事务里：要么「新快照进 + 老快照出」一起成立，要么都不发生，
     * 不会出现「新的一张没进来、老的却被删了」的空窗。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestSplitSnapshot(DeviceIngestRequests.DeviceSplitSnapshot request) {
        // 拍摄时刻优先取报文里的设备侧时间；缺省才退回入库时刻（接入层已兜底一次，这里再兜一层）
        LocalDateTime captured = request.getCapturedTime() == null ? LocalDateTime.now() : request.getCapturedTime();

        // 绑定：声称属于某批次，该批次就必须存在（编造绑定 → 已拒绝，不进人工队列：见通道注释）
        String batchNo = null;
        Long jobId = null;
        if (StringUtils.hasText(request.getBatchNo())) {
            CarcassBatch batch = carcassBatchMapper.selectOne(Wrappers.<CarcassBatch>lambdaQuery()
                    .eq(CarcassBatch::getBatchNo, request.getBatchNo().trim())
                    .last("LIMIT 1"));
            if (batch == null) {
                return rejected("照片声称属于批次「" + request.getBatchNo().trim()
                        + "」，但系统里查不到该批次（疑似接错点位或编造绑定），禁止入库");
            }
            // 用库里的规范批次号，不要回写设备传来的原文（大小写/空格差异会让同一批次出现两种写法）
            batchNo = batch.getBatchNo();
            SplitJob latestJob = splitJobMapper.selectOne(Wrappers.<SplitJob>lambdaQuery()
                    .eq(SplitJob::getParentBatchId, batch.getId())
                    .orderByDesc(SplitJob::getId)
                    .last("LIMIT 1"));
            jobId = latestJob == null ? null : latestJob.getId();
        }

        WorkshopSnapshot snapshot = new WorkshopSnapshot();
        snapshot.setDeviceNo(request.getDeviceNo());
        snapshot.setWorkshop(request.getWorkshop());
        snapshot.setBatchNo(batchNo);
        snapshot.setJobId(jobId);
        snapshot.setFileId(request.getFileId());
        snapshot.setCapturedTime(captured);
        snapshot.setSource(SOURCE_DEVICE);
        snapshot.setSourceRef(request.getSourceRef());
        snapshot.setRawPayload(request.getRawPayload());
        snapshot.setCreateTime(LocalDateTime.now());
        snapshotMapper.insert(snapshot);

        pruneOldSnapshots(request.getDeviceNo());

        return written(snapshot.getId(), batchNo == null
                ? "车间快照已入账"
                : "车间快照已入账（已绑定批次 " + batchNo + "）");
    }

    /**
     * 淘汰某台相机超出滚动窗口的最老快照（连同文件一起删）。
     * <p>
     * 按自增主键倒序取「从第 {@code snapshotRetention} 条之后」的记录 ——
     * 主键递增就代表写入先后，不用比时间戳（设备时间可能回退，主键不会）。
     * <p>
     * <b>删文件前先确认「没有别的快照还指向它」</b>：正常情况下一张照片一个 fileId、
     * 一对一，不必多查；但只要出现「多张快照共用一份文件」，无条件删文件就会把别人
     * 还在用的照片提前删掉 —— 症状是后续上报被接入层的凭证核对判成「照片不存在（疑似伪造）」。
     * 这个守卫代价是一次计数查询，换掉一类「数据看着好好的、链路却突然开始拒绝」的隐疾。
     */
    private void pruneOldSnapshots(String deviceNo) {
        if (snapshotRetention <= 0 || !StringUtils.hasText(deviceNo)) return;
        List<WorkshopSnapshot> stale = snapshotMapper.selectList(Wrappers.<WorkshopSnapshot>lambdaQuery()
                .select(WorkshopSnapshot::getId, WorkshopSnapshot::getFileId)
                .eq(WorkshopSnapshot::getDeviceNo, deviceNo)
                .orderByDesc(WorkshopSnapshot::getId)
                .last("LIMIT " + SNAPSHOT_PRUNE_BATCH + " OFFSET " + snapshotRetention));
        if (stale.isEmpty()) return;
        List<String> candidates = new ArrayList<>();
        for (WorkshopSnapshot old : stale) {
            snapshotMapper.deleteById(old.getId());
            if (StringUtils.hasText(old.getFileId())) candidates.add(old.getFileId());
        }
        for (String fileId : candidates.stream().distinct().toList()) {
            Long remaining = snapshotMapper.selectCount(Wrappers.<WorkshopSnapshot>lambdaQuery()
                    .eq(WorkshopSnapshot::getFileId, fileId));
            if (remaining == null || remaining == 0) {
                // 文件删除失败只记日志（FileClient 内部吞异常）：孤儿文件不破坏一致性，不值得回滚业务
                fileClient.delete(fileId);
            }
        }
        log.info("分割车间快照滚动淘汰 deviceNo={} 清理 {} 张", deviceNo, stale.size());
    }

    private Map<String, Object> written(Long id, String reason) {
        return result(true, id, reason, false);
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
