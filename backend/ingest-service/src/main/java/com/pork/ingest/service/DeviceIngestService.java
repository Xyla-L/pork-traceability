package com.pork.ingest.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.PageResult;
import com.pork.ingest.dto.DeviceReportRequest;
import com.pork.ingest.entity.IngestDevice;
import com.pork.ingest.entity.IngestStaging;
import com.pork.ingest.mapper.IngestDeviceMapper;
import com.pork.ingest.mapper.IngestStagingMapper;
import com.pork.ingest.support.DispatchResult;
import com.pork.ingest.support.IngestChannel;
import com.pork.ingest.support.IngestStatus;
import com.pork.ingest.support.RejectException;
import com.pork.ingest.vo.IngestDeviceVO;
import com.pork.ingest.vo.IngestStagingVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 接入层编排：收 → 校验 → 暂存 → 分发。
 * <p>
 * 四条不可动摇的规则：
 * <ol>
 *   <li><b>先留档再处理</b>：任何上报先落 ingest_staging，处理失败也不丢原始报文。</li>
 *   <li><b>幂等优先</b>：device_no + biz_key 唯一，重发只认第一条，杜绝重复入账。</li>
 *   <li><b>采集 ≠ 生效</b>：只有校验通过才写业务表，错误数据自然上不了链。</li>
 *   <li><b>人工兜底不消失</b>：跑不通的数据进待人工处理队列，不会被静默吞掉。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceIngestService {

    /** 单条报文上限，防止设备端一次推整段历史把库打满 */
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    /** 设备时间允许的前置偏差，超出视为时钟错误 */
    private static final int MAX_FUTURE_MINUTES = 10;

    private final IngestDeviceMapper deviceMapper;
    private final IngestStagingMapper stagingMapper;
    private final ObjectMapper objectMapper;
    private final List<ChannelHandler> handlerList;

    private Map<IngestChannel, ChannelHandler> handlers;

    @PostConstruct
    void initHandlers() {
        handlers = handlerList.stream().collect(Collectors.toMap(ChannelHandler::channel, Function.identity(),
                (a, b) -> a, () -> new EnumMap<>(IngestChannel.class)));
    }

    // ------------------------------------------------------------------ 设备上报

    public Map<String, Object> receive(String deviceKey, DeviceReportRequest request) {
        IngestDevice device = authenticate(deviceKey);
        IngestChannel channel = resolveChannel(device, request.getChannel());
        ChannelHandler handler = handlers.get(channel);
        if (handler == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "通道未启用处理器: " + channel);
        }
        if (!StringUtils.hasText(request.getBizKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "bizKey(设备侧唯一号)不能为空");
        }

        String rawPayload = toJson(request.getData());
        LocalDateTime now = LocalDateTime.now();

        // 幂等：同一设备同一条数据重发，直接回既有结论，不重复入账
        IngestStaging exists = findStaging(device.getDeviceNo(), request.getBizKey());
        if (exists != null) {
            touchDevice(device, now);
            return toResponse(exists, true);
        }

        IngestStaging staging = new IngestStaging();
        staging.setChannel(channel.name());
        staging.setDeviceNo(device.getDeviceNo());
        staging.setBizKey(request.getBizKey());
        staging.setReportTime(request.getReportTime() == null ? now : request.getReportTime());
        staging.setReceiveTime(now);
        staging.setPayload(rawPayload);
        staging.setSourceRef(StringUtils.hasText(request.getSourceRef()) ? request.getSourceRef() : device.getDeviceNo());
        staging.setStatus(IngestStatus.PENDING);
        staging.setTargetTable(channel.targetTable());
        staging.setCreateTime(now);
        try {
            stagingMapper.insert(staging);
        } catch (DuplicateKeyException e) {
            // 并发重发：唯一键兜底，退回既有记录
            IngestStaging concurrent = findStaging(device.getDeviceNo(), request.getBizKey());
            if (concurrent == null) throw new BusinessException(ErrorCode.DATABASE_ERROR, "重复上报判定失败");
            return toResponse(concurrent, true);
        }

        IngestContext ctx = new IngestContext(device, request, rawPayload);
        DispatchResult result = process(ctx, handler, false);
        applyResult(staging, result);
        touchDevice(device, now);
        return toResponse(staging, false);
    }

    /** 校验 + 分发，把各类异常统一翻译成处理结论 */
    private DispatchResult process(IngestContext ctx, ChannelHandler handler, boolean force) {
        try {
            if (ctx.reportTime().isAfter(LocalDateTime.now().plusMinutes(MAX_FUTURE_MINUTES))) {
                throw new RejectException("数据时间晚于当前时间 " + MAX_FUTURE_MINUTES + " 分钟以上，疑似设备时钟错误");
            }
            List<String> errors = handler.validate(ctx);
            if (!errors.isEmpty()) {
                throw new RejectException(String.join("；", errors));
            }
            return handler.dispatch(ctx, force);
        } catch (RejectException e) {
            return DispatchResult.rejected(handler.channel().targetTable(), e.getMessage());
        } catch (BusinessException e) {
            if (ErrorCode.REMOTE_CALL_ERROR.getCode().equals(e.getCode())) {
                log.warn("ingest_downstream_unavailable channel={} device={} err={}",
                        handler.channel(), ctx.device().getDeviceNo(), e.getMessage());
                return DispatchResult.manual(handler.channel().targetTable(), e.getMessage());
            }
            return DispatchResult.rejected(handler.channel().targetTable(), e.getMessage());
        } catch (Exception e) {
            log.error("ingest_dispatch_failed channel={} device={} bizKey={}",
                    handler.channel(), ctx.device().getDeviceNo(), ctx.report().getBizKey(), e);
            return DispatchResult.manual(handler.channel().targetTable(), "接入层处理异常：" + e.getMessage());
        }
    }

    private void applyResult(IngestStaging staging, DispatchResult result) {
        staging.setStatus(result.status());
        staging.setTargetTable(result.targetTable());
        staging.setTargetId(result.targetId());
        staging.setErrorMsg(result.accepted() ? null : result.message());
        stagingMapper.updateById(staging);
        if (!result.accepted()) {
            log.info("ingest_not_written device={} bizKey={} status={} reason={}",
                    staging.getDeviceNo(), staging.getBizKey(), result.status(), result.message());
        }
    }

    private void touchDevice(IngestDevice device, LocalDateTime time) {
        deviceMapper.update(null, Wrappers.<IngestDevice>lambdaUpdate()
                .eq(IngestDevice::getId, device.getId())
                .set(IngestDevice::getLastSeenTime, time));
    }

    // ------------------------------------------------------------------ 待确认队列

    public PageResult<IngestStagingVO> pageStaging(String channel, Integer status, String deviceNo,
                                                   long pageNum, long pageSize) {
        Page<IngestStaging> page = stagingMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<IngestStaging>lambdaQuery()
                        .eq(StringUtils.hasText(channel), IngestStaging::getChannel, channel)
                        .eq(status != null, IngestStaging::getStatus, status)
                        .eq(StringUtils.hasText(deviceNo), IngestStaging::getDeviceNo, deviceNo)
                        // 待处理的排最前，其余按接收时间倒序
                        .orderByAsc(IngestStaging::getStatus)
                        .orderByDesc(IngestStaging::getReceiveTime));
        Map<String, String> deviceNames = deviceNameMap();
        List<IngestStagingVO> records = page.getRecords().stream()
                .map(row -> IngestStagingVO.from(row, deviceNames.get(row.getDeviceNo())))
                .toList();
        return PageResult.of(pageNum, pageSize, page.getTotal(), records);
    }

    public Map<String, Object> getStaging(Long id) {
        IngestStaging staging = requireStaging(id);
        return toResponse(staging, false);
    }

    /** 人工确认后强制入账：跳过依赖外部数据的软校验，硬校验（越界值、缺必填）仍然生效 */
    public Map<String, Object> approve(Long id, String handler, String remark) {
        IngestStaging staging = requireStaging(id);
        if (!Objects.equals(staging.getStatus(), IngestStatus.MANUAL)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅「待人工处理」的数据可以确认入账");
        }
        IngestChannel channel = IngestChannel.of(staging.getChannel());
        ChannelHandler channelHandler = handlers.get(channel);
        IngestDevice device = deviceMapper.selectOne(Wrappers.<IngestDevice>lambdaQuery()
                .eq(IngestDevice::getDeviceNo, staging.getDeviceNo()));
        if (device == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "上报设备不存在");

        DeviceReportRequest request = new DeviceReportRequest();
        request.setChannel(channel.name());
        request.setBizKey(staging.getBizKey());
        request.setReportTime(staging.getReportTime());
        request.setSourceRef(staging.getSourceRef());
        request.setData(toMap(staging.getPayload()));

        DispatchResult result = process(new IngestContext(device, request, staging.getPayload()), channelHandler, true);
        if (result.accepted()) {
            staging.setStatus(IngestStatus.ACCEPTED);
            staging.setTargetTable(result.targetTable());
            staging.setTargetId(result.targetId());
            staging.setErrorMsg(null);
            staging.setHandleTime(LocalDateTime.now());
            staging.setHandler(handler);
            staging.setRemark(remark);
            stagingMapper.updateById(staging);
        } else {
            // 强制入账再失败说明条件仍不满足（如耳标仍无档），保留待人工状态并回写原因
            staging.setErrorMsg(result.message());
            staging.setHandleTime(LocalDateTime.now());
            staging.setHandler(handler);
            stagingMapper.updateById(staging);
        }
        return toResponse(staging, false);
    }

    /** 人工拒绝：数据留档，但不入业务表 */
    public Map<String, Object> reject(Long id, String handler, String reason) {
        IngestStaging staging = requireStaging(id);
        if (Objects.equals(staging.getStatus(), IngestStatus.ACCEPTED)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "已入账的数据不能拒绝，请走业务更正流程");
        }
        staging.setStatus(IngestStatus.REJECTED);
        staging.setErrorMsg(StringUtils.hasText(reason) ? reason : "人工判定为无效数据");
        staging.setHandleTime(LocalDateTime.now());
        staging.setHandler(handler);
        stagingMapper.updateById(staging);
        return toResponse(staging, false);
    }

    /** 设备台账（全量，设备数量在千级以内，不做分页）。返回 VO：密钥不下发到管理端 */
    public List<IngestDeviceVO> listDevices(String channel, Integer status) {
        return deviceMapper.selectList(Wrappers.<IngestDevice>lambdaQuery()
                        .eq(StringUtils.hasText(channel), IngestDevice::getChannel, channel)
                        .eq(status != null, IngestDevice::getStatus, status)
                        .orderByAsc(IngestDevice::getDeviceNo))
                .stream()
                .map(IngestDeviceVO::from)
                .toList();
    }

    /**
     * 演示/联调用：由管理端代设备发一次上报。
     * <p>
     * 存在的意义是让接入链路在页面上可见——没有真机时，评审/演示无法看到
     * 「设备上报 → 待确认 → 入账 → 业务表标记 DEVICE」这个过程。
     * <p>
     * 实现上不绕过任何一步：先用 deviceNo 反查设备，再用该设备自己的密钥调用
     * {@link #receive}，因此鉴权、幂等、校验、降采样、派发的行为与真机完全一致。
     * 密钥始终留在服务端，不下发给浏览器。
     */
    public Map<String, Object> demoReport(String deviceNo, String bizKey, LocalDateTime reportTime,
                                          String sourceRef, Map<String, Object> data) {
        IngestDevice device = deviceMapper.selectOne(Wrappers.<IngestDevice>lambdaQuery()
                .eq(IngestDevice::getDeviceNo, deviceNo));
        if (device == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "设备不存在：" + deviceNo);
        }
        DeviceReportRequest request = new DeviceReportRequest();
        request.setChannel(device.getChannel());
        // 演示场景每次默认新唯一号，保证能反复触发；需要验证幂等时显式传入同一个 bizKey
        request.setBizKey(StringUtils.hasText(bizKey) ? bizKey : "DEMO-" + System.currentTimeMillis());
        request.setReportTime(reportTime == null ? LocalDateTime.now() : reportTime);
        request.setSourceRef(StringUtils.hasText(sourceRef) ? sourceRef : device.getDeviceNo());
        request.setData(data == null ? Map.of() : data);
        return receive(device.getSecretKey(), request);
    }

    /** 设备自检：只验密钥与登记通道，不写任何数据 */
    public Map<String, Object> ping(String deviceKey) {
        IngestDevice device = authenticate(deviceKey);
        IngestChannel channel = IngestChannel.of(device.getChannel());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deviceNo", device.getDeviceNo());
        data.put("deviceName", device.getDeviceName());
        data.put("channel", channel.name());
        data.put("targetTable", channel.targetTable());
        data.put("location", device.getLocation());
        data.put("status", device.getStatus());
        data.put("serverTime", LocalDateTime.now().toString());
        return data;
    }

    // ------------------------------------------------------------------ 内部工具

    private IngestDevice authenticate(String deviceKey) {
        if (!StringUtils.hasText(deviceKey)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少设备密钥请求头 X-Device-Key");
        }
        IngestDevice device = deviceMapper.selectOne(Wrappers.<IngestDevice>lambdaQuery()
                .eq(IngestDevice::getSecretKey, deviceKey));
        if (device == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "设备密钥无效，请确认设备是否已登记");
        }
        if (!Integer.valueOf(1).equals(device.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "设备已停用，拒绝接收上报");
        }
        return device;
    }

    private IngestChannel resolveChannel(IngestDevice device, String requested) {
        IngestChannel deviceChannel = IngestChannel.of(device.getChannel());
        if (!StringUtils.hasText(requested)) return deviceChannel;
        IngestChannel channel = IngestChannel.of(requested);
        if (channel != deviceChannel) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "设备 " + device.getDeviceNo() + " 登记通道为 " + deviceChannel + "，与上报通道 " + channel + " 不一致");
        }
        return channel;
    }

    private IngestStaging findStaging(String deviceNo, String bizKey) {
        return stagingMapper.selectOne(Wrappers.<IngestStaging>lambdaQuery()
                .eq(IngestStaging::getDeviceNo, deviceNo)
                .eq(IngestStaging::getBizKey, bizKey)
                .last("LIMIT 1"));
    }

    private IngestStaging requireStaging(Long id) {
        IngestStaging staging = stagingMapper.selectById(id);
        if (staging == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "上报记录不存在");
        return staging;
    }

    private Map<String, String> deviceNameMap() {
        return deviceMapper.selectList(null).stream()
                .filter(device -> device.getDeviceNo() != null)
                .collect(Collectors.toMap(IngestDevice::getDeviceNo,
                        device -> device.getDeviceName() == null ? device.getDeviceNo() : device.getDeviceName(),
                        (a, b) -> a));
    }

    private String toJson(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data == null ? Map.of() : data);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                // 报文超限发生在落档之前，无法进待确认队列，直接以参数错误回给设备端
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "报文超过 " + (MAX_PAYLOAD_BYTES / 1024) + "KB 上限，请分批上报");
            }
            return json;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报文不是合法 JSON");
        }
    }

    private Map<String, Object> toMap(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "历史报文解析失败");
        }
    }

    /** 设备回执：明确告知收没收下、入没入账、没入账的原因 */
    private Map<String, Object> toResponse(IngestStaging staging, boolean duplicate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stagingId", staging.getId());
        response.put("channel", staging.getChannel());
        response.put("bizKey", staging.getBizKey());
        response.put("status", staging.getStatus());
        response.put("statusLabel", IngestStatus.label(staging.getStatus()));
        response.put("accepted", Objects.equals(staging.getStatus(), IngestStatus.ACCEPTED));
        response.put("targetTable", staging.getTargetTable());
        response.put("targetId", staging.getTargetId());
        response.put("message", staging.getErrorMsg() == null ? IngestStatus.label(staging.getStatus()) : staging.getErrorMsg());
        response.put("duplicate", duplicate);
        return response;
    }
}
