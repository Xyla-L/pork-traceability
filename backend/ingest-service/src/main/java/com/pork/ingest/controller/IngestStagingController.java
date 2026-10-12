package com.pork.ingest.controller;

import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.core.source.OperatorHeader;
import com.pork.ingest.dto.DemoReportRequest;
import com.pork.ingest.dto.StagingHandleRequest;
import com.pork.ingest.service.DeviceIngestService;
import com.pork.ingest.vo.IngestDeviceVO;
import com.pork.ingest.vo.IngestStagingVO;
import com.pork.web.source.SourceGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待确认队列（管理端，需要登录）。
 * <p>
 * 这是「自动采集 ≠ 自动生效」的兑现方式：设备上报但没通过校验的数据不会消失，
 * 而是留在这里等人工确认；同时设备台账也在这里查，便于判断某台设备是否在正常工作。
 */
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestStagingController {

    private final DeviceIngestService ingestService;
    private final SourceGuard sourceGuard;

    /** 待确认队列分页（status=1 即"待人工处理"，是需要人盯的列表） */
    @GetMapping("/staging")
    public Result<PageResult<IngestStagingVO>> pageStaging(@RequestParam(required = false) String channel,
                                                           @RequestParam(required = false) Integer status,
                                                           @RequestParam(required = false) String deviceNo,
                                                           @RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(ingestService.pageStaging(channel, status, deviceNo, pageNum, pageSize));
    }

    /** 单条详情：带出原始报文，便于人工比对 */
    @GetMapping("/staging/{id}")
    public Result<Map<String, Object>> getStaging(@PathVariable Long id) {
        return Result.success(ingestService.getStaging(id));
    }

    /** 人工确认入账：补齐设备拿不到的条件后放行 */
    @PostMapping("/staging/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable Long id,
                                               @RequestHeader(value = "X-User-Id", required = false) String userId,
                                               @RequestHeader(value = "X-User-Name", required = false) String userName,
                                               @RequestBody(required = false) StagingHandleRequest request) {
        return Result.success(ingestService.approve(id, handler(userId, userName, request),
                request == null ? null : request.getRemark(),
                request == null ? null : request.getPayload()));
    }

    /** 人工拒绝：保留原始报文留档，不入业务表 */
    @PostMapping("/staging/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id,
                                              @RequestHeader(value = "X-User-Id", required = false) String userId,
                                              @RequestHeader(value = "X-User-Name", required = false) String userName,
                                              @RequestBody(required = false) StagingHandleRequest request) {
        return Result.success(ingestService.reject(id, handler(userId, userName, request),
                request == null ? null : request.reason()));
    }

    /** 设备台账：排查"某台设备一直没数据"时先看这里。返回 VO，不含设备密钥 */
    @GetMapping("/devices")
    public Result<List<IngestDeviceVO>> listDevices(@RequestParam(required = false) String channel,
                                                    @RequestParam(required = false) Integer status) {
        return Result.success(ingestService.listDevices(channel, status));
    }

    /**
     * 来源管控策略：哪个环节必须用设备、哪个环节人工可录。
     * <p>
     * 管理端据此决定关键环节页面是否展示人工新增入口——策略只在服务端定义，
     * 前端按钮只是表现形式，即便有人绕过页面直接调接口，业务侧照样会拦。
     */
    @GetMapping("/source-policy")
    public Result<List<Map<String, Object>>> sourcePolicy() {
        List<Map<String, Object>> rows = sourceGuard.policies().stream().map(policy -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", policy.code());
            row.put("label", policy.label());
            row.put("channel", policy.channel());
            row.put("targetTable", policy.targetTable());
            row.put("policy", policy.policy().name());
            row.put("policyLabel", policy.policyLabel());
            row.put("policyDescription", policy.policyDescription());
            row.put("deviceCapable", policy.deviceCapable());
            row.put("manualBlocked", policy.manualBlocked());
            row.put("overridden", policy.overridden());
            row.put("note", policy.note());
            return row;
        }).toList();
        return Result.success(rows);
    }

    /**
     * 模拟一次设备上报（演示/联调用，需登录）。
     * <p>
     * 没有真机时，接入链路在页面上是不可见的。这个入口让管理端可以代设备发一条报文，
     * 走完整的鉴权→校验→幂等→派发流程，从而直观看到「设备上报 → 待确认 → 入账 →
     * 业务表来源标记 DEVICE」这条链路。
     * <p>
     * 它不新增任何业务能力，只是把真机要做的事搬到浏览器里触发一次；密钥由服务端保管。
     */
    @PostMapping("/demo/report")
    public Result<Map<String, Object>> demoReport(@Valid @RequestBody DemoReportRequest request) {
        return Result.success(ingestService.demoReport(request.getDeviceNo(), request.getBizKey(),
                request.getReportTime(), request.getSourceRef(), request.getChannel(), request.getData()));
    }

    /**
     * 处理人取值：优先网关透传的登录身份，格式「用户名(用户ID)」——
     * 用户名便于人看，用户ID便于机器对账，两者都留着，审计时不用再反查。
     */
    private String handler(String userId, String userName, StagingHandleRequest request) {
        // 姓名在头里是 URL 编码的（中文名不能裸放在 HTTP 头里，会被编成 '?'），这里解回来
        String name = OperatorHeader.decode(userName);
        if (name != null && !name.isBlank() && !name.equals(userId)) return name;
        if (userId != null && !userId.isBlank()) return userId;
        return request == null || request.getHandler() == null ? "unknown" : request.getHandler();
    }
}
