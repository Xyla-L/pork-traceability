package com.pork.ingest.controller;

import com.pork.core.result.Result;
import com.pork.ingest.dto.DeviceReportRequest;
import com.pork.ingest.service.DeviceIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备上报入口（唯一入口）。
 * <p>
 * 设备不走 JWT，用 {@code X-Device-Key} 做设备级鉴权；所有通道共用一个报文信封，
 * 通道差异放在 {@code data} 里，设备侧只需要实现一套上报代码。
 * <p>
 * 回执语义（设备必须按此判断能否删除本地缓存，否则断网补传会重复入账）：
 * <ul>
 *   <li>{@code accepted=true} —— 已进业务表，可以删除本地缓存</li>
 *   <li>{@code status=1} —— 已收到但需人工处理，可以删除本地缓存（不要重推）</li>
 *   <li>{@code status=3} —— 数据本身不成立被拒绝，不要重推，需人工修正后重录</li>
 *   <li>{@code status=4} —— 非异常值降采样丢弃，可以删除本地缓存</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ingest/device")
@RequiredArgsConstructor
public class DeviceIngestController {

    private final DeviceIngestService ingestService;

    /** 设备上报（温度 / 入场查验 / 瘦肉精 / 收银 / 签收共用一个入口） */
    @PostMapping("/report")
    public Result<Map<String, Object>> report(@RequestHeader(value = "X-Device-Key", required = false) String deviceKey,
                                              @Valid @RequestBody DeviceReportRequest request) {
        return Result.success(ingestService.receive(deviceKey, request));
    }

    /** 设备自检：只验密钥与通道，不写任何数据 */
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping(@RequestHeader(value = "X-Device-Key", required = false) String deviceKey) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serverTime", LocalDateTime.now().toString());
        data.put("device", ingestService.ping(deviceKey));
        return Result.success(data);
    }
}
