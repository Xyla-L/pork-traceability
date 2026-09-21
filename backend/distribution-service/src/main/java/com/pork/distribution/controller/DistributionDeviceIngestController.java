package com.pork.distribution.controller;

import com.pork.core.result.Result;
import com.pork.distribution.dto.DeviceIngestRequests;
import com.pork.distribution.service.DistributionDeviceIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备通道内部写入接口（仅接入层调用，不经过网关）。
 * <p>
 * 回执 {{@code written, id, reason, retryable}} 是接入层判断「入账 / 待人工 / 拒绝」的唯一依据。
 */
@RestController
@RequestMapping("/distribution/internal/ingest")
@RequiredArgsConstructor
public class DistributionDeviceIngestController {

    private final DistributionDeviceIngestService ingestService;

    /** 冷链温度采集（车载探头 / 冷库探头） */
    @PostMapping("/temperature")
    public Result<Map<String, Object>> temperature(@Valid @RequestBody DeviceIngestRequests.DeviceTemperature request) {
        return Result.success(ingestService.ingestTemperature(request));
    }

    /** 门店签收（PDA 扫码 + 冷柜测温） */
    @PostMapping("/store-receipt")
    public Result<Map<String, Object>> storeReceipt(@Valid @RequestBody DeviceIngestRequests.DeviceReceipt request) {
        return Result.success(ingestService.ingestReceipt(request));
    }
}
