package com.pork.breeding.controller;

import com.pork.breeding.dto.FarmBatchIngestRequest;
import com.pork.breeding.service.FarmBatchIngestService;
import com.pork.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 外部养殖系统同步的内部写入接口（仅接入层调用，不经过网关、不对外开放）。
 *
 * <p>为什么不让外部 ERP 直接调这里：接入层负责留原始报文、判重、状态机与人工兜底队列。
 * 少了那一层，外部系统一旦重推或改数，档案里就会长出重复批次，而且没人能回到原始报文
 * 去证明「它当初到底推了什么」。
 *
 * <p>回执与设备通道同构：{@code {written, id, reason, retryable}}。
 */
@RestController
@RequestMapping("/breeding/internal/ingest")
@RequiredArgsConstructor
public class FarmErpInternalController {

    private final FarmBatchIngestService farmBatchIngestService;

    /** 外部养殖系统推送批次档案：UPSERT=新建/更新，REVOKE=源撤销 */
    @PostMapping("/farm-batch")
    public Result<Map<String, Object>> farmBatch(@RequestBody FarmBatchIngestRequest request) {
        return Result.success(farmBatchIngestService.ingest(request));
    }
}
