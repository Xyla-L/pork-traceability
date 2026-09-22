package com.pork.slaughter.controller;

import com.pork.core.result.Result;
import com.pork.slaughter.dto.DeviceIngestRequests;
import com.pork.slaughter.service.SlaughterDeviceIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备通道内部写入接口（仅接入层调用，不经过网关、不对外开放）。
 * <p>
 * 统一回执格式 {@code {written, id, reason, retryable}}：
 * 接入层据此决定是「已入账 / 待人工处理 / 拒绝」，业务判断留在本服务，接入层不重复实现。
 */
@RestController
@RequestMapping("/slaughter/internal/ingest")
@RequiredArgsConstructor
public class SlaughterDeviceIngestController {

    private final SlaughterDeviceIngestService ingestService;

    /** 入场查验：门禁地磅 + 耳标识读 + 检疫证核验 */
    @PostMapping("/entry")
    public Result<Map<String, Object>> entry(@Valid @RequestBody DeviceIngestRequests.DeviceEntry request) {
        return Result.success(ingestService.ingestEntry(request));
    }

    /** 瘦肉精快速检测：胶体金读数仪出结果后上报 */
    @PostMapping("/ractopamine")
    public Result<Map<String, Object>> ractopamine(@Valid @RequestBody DeviceIngestRequests.DeviceRactopamine request) {
        return Result.success(ingestService.ingestRactopamine(request));
    }

    /** 屠宰检验：工位终端（兽医判定 + 终端录入） */
    @PostMapping("/inspection")
    public Result<Map<String, Object>> inspection(@Valid @RequestBody DeviceIngestRequests.DeviceInspection request) {
        return Result.success(ingestService.ingestInspection(request));
    }

    /** 胴体盖章：自动盖章机（硬校验已有合格检验，人工确认也绕不过） */
    @PostMapping("/stamp")
    public Result<Map<String, Object>> stamp(@Valid @RequestBody DeviceIngestRequests.DeviceStamp request) {
        return Result.success(ingestService.ingestStamp(request));
    }
}
