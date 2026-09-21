package com.pork.sales.controller;

import com.pork.core.result.Result;
import com.pork.sales.dto.DeviceIngestRequests;
import com.pork.sales.service.SalesDeviceIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 门店收银 POS 内部写入接口（仅接入层调用，不经过网关） */
@RestController
@RequestMapping("/sales/internal/ingest")
@RequiredArgsConstructor
public class SalesDeviceIngestController {

    private final SalesDeviceIngestService ingestService;

    /** 扫码激活 + 售出（一次请求完成，重复扫码幂等） */
    @PostMapping("/retail-sale")
    public Result<Map<String, Object>> retailSale(@Valid @RequestBody DeviceIngestRequests.DeviceSale request) {
        return Result.success(ingestService.ingestRetailSale(request.getQrCode(), request.getSellPrice(),
                request.getSellWeightKg(), request.getSourceRef(), request.getRawPayload()));
    }
}
