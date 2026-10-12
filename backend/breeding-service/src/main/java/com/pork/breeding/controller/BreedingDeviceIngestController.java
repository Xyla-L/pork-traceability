package com.pork.breeding.controller;

import com.pork.breeding.dto.BreedingDeviceIngestRequests;
import com.pork.breeding.service.BreedingDeviceIngestService;
import com.pork.core.result.Result;
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
 * 接入层据此决定是「已入账 / 待人工处理 / 拒绝」，业务判断留在本服务。
 */
@RestController
@RequestMapping("/breeding/internal/ingest")
@RequiredArgsConstructor
public class BreedingDeviceIngestController {

    private final BreedingDeviceIngestService ingestService;

    /** 养殖建档：耳标读写器，佩戴即建档 */
    @PostMapping("/tag")
    public Result<Map<String, Object>> tag(@Valid @RequestBody BreedingDeviceIngestRequests.DeviceTag request) {
        return Result.success(ingestService.ingestTag(request));
    }

    /** 免疫注射：智能连续注射器 */
    @PostMapping("/vaccine")
    public Result<Map<String, Object>> vaccine(@Valid @RequestBody BreedingDeviceIngestRequests.DeviceVaccine request) {
        return Result.success(ingestService.ingestVaccine(request));
    }

    /** 产地检疫出证：检疫证扫码登记终端（扫证面二维码登记，非人工出证） */
    @PostMapping("/quarantine-cert")
    public Result<Map<String, Object>> quarantineCert(
            @Valid @RequestBody BreedingDeviceIngestRequests.DeviceQuarantineCert request) {
        return Result.success(ingestService.ingestQuarantineCert(request));
    }

    /** 申请出栏：出栏口地磅（耳标识读 + 称重），缺去向时回执 retryable=true 由接入层转待人工确认 */
    @PostMapping("/exit-apply")
    public Result<Map<String, Object>> exitApply(
            @Valid @RequestBody BreedingDeviceIngestRequests.DeviceExitApply request) {
        return Result.success(ingestService.ingestExitApply(request));
    }
}
