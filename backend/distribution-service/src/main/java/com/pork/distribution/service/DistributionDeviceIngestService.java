package com.pork.distribution.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.util.HashUtil;
import com.pork.distribution.dto.DeviceIngestRequests;
import com.pork.distribution.entity.ColdChainTransport;
import com.pork.distribution.entity.StoreReceipt;
import com.pork.distribution.entity.TemperatureLog;
import com.pork.distribution.mapper.ColdChainTransportMapper;
import com.pork.distribution.mapper.StoreReceiptMapper;
import com.pork.distribution.mapper.TemperatureLogMapper;
import com.pork.mq.ChainEventPublisher;
import com.pork.mq.ChainTxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备通道写入（冷链温度 / 门店签收）。
 * <p>
 * 与人工入口的差别只在「谁触发」：状态机约束完全一致——
 * 运输中才能记温度、到达后才能签收、一单只能签收一次。
 * 设备绕过这些约束等于给冷链台账开后门，所以这里一律复用同一套判断。
 */
@Slf4j
@Service
public class DistributionDeviceIngestService {

    private static final String SOURCE_DEVICE = "DEVICE";
    /** 运输任务状态：1待发车 2运输中 3已到达 4已签收 */
    private static final int STATUS_IN_TRANSIT = 2;
    private static final int STATUS_ARRIVED = 3;

    private final ColdChainTransportMapper transportMapper;
    private final TemperatureLogMapper temperatureLogMapper;
    private final StoreReceiptMapper receiptMapper;
    private final ChainEventPublisher chainEvents;
    private final BigDecimal defaultRangeMin;
    private final BigDecimal defaultRangeMax;

    public DistributionDeviceIngestService(ColdChainTransportMapper transportMapper,
                                           TemperatureLogMapper temperatureLogMapper,
                                           StoreReceiptMapper receiptMapper,
                                           ChainEventPublisher chainEvents,
                                           @Value("${ingest.temperature.range-min:-18.0}") BigDecimal defaultRangeMin,
                                           @Value("${ingest.temperature.range-max:0.0}") BigDecimal defaultRangeMax) {
        this.transportMapper = transportMapper;
        this.temperatureLogMapper = temperatureLogMapper;
        this.receiptMapper = receiptMapper;
        this.chainEvents = chainEvents;
        this.defaultRangeMin = defaultRangeMin;
        this.defaultRangeMax = defaultRangeMax;
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
        temperatureLogMapper.insert(entity);

        String reason = Integer.valueOf(1).equals(entity.getIsAbnormal())
                ? "温度超出允许区间[" + min + "," + max + "]，已入账并标记异常"
                : "温度已入账";
        return written(entity.getId(), reason);
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
        Long existing = receiptMapper.selectCount(Wrappers.<StoreReceipt>lambdaQuery()
                .eq(StoreReceipt::getTransportId, transport.getId()));
        if (existing != null && existing > 0) {
            // 确定性错误：不该重推，重推也永远不会成功
            return rejected("运单 " + transport.getTransportNo() + " 已签收，重复签收已拒绝");
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

        transportMapper.update(null, Wrappers.<ColdChainTransport>lambdaUpdate()
                .eq(ColdChainTransport::getId, transport.getId())
                .eq(ColdChainTransport::getStatus, STATUS_ARRIVED)
                .set(ColdChainTransport::getStatus, 4));
        // 签收动作照常上链；带上 source 便于链上区分人工签收与 PDA 自动签收
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transportNo", transport.getTransportNo());
        payload.put("source", SOURCE_DEVICE);
        if (StringUtils.hasText(request.getSourceRef())) payload.put("sourceRef", request.getSourceRef());
        chainEvents.publish(ChainTxMessage.create("STORE_RECEIPT", entity.getId(), transport.getTransportNo(),
                entity.getContentHash(), payload));
        return written(entity.getId(), "门店签收已入账");
    }

    private ColdChainTransport resolveTransport(Long transportId, String transportNo) {
        if (transportId != null) {
            ColdChainTransport byId = transportMapper.selectById(transportId);
            if (byId != null) return byId;
        }
        if (!StringUtils.hasText(transportNo)) return null;
        return transportMapper.selectOne(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getTransportNo, transportNo).last("LIMIT 1"));
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
