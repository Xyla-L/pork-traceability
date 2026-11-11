package com.pork.distribution.controller;

import com.pork.core.result.Result;
import com.pork.distribution.dto.DistributionRequests;
import com.pork.distribution.service.DistributionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务间内部接口（仅业务服务调用，不经过网关）。
 * <p>
 * 与 {@link DistributionDeviceIngestController} 的区别：那个是「设备上报事实」，
 * 这个是「别的业务服务交代一件事」—— 目前只有销售服务的召回登记。
 */
@RestController
@RequestMapping("/distribution/internal")
@RequiredArgsConstructor
public class DistributionInternalController {

    private final DistributionService distributionService;

    /**
     * 召回登记：把召回单涉及的「门店 × 批次」写进待收运池。
     * <p>
     * 召回发起方是销售服务（它才知道哪些销售记录被召回），但「货怎么收走」是配送的事 ——
     * 所以这里只接收事实（门店、批次、货重），不接收车牌/司机，也不负责通知。
     */
    @PostMapping("/recall-collect")
    public Result<Map<String, Object>> registerRecallCollect(
            @Valid @RequestBody DistributionRequests.RecallCollectRegister request) {
        return Result.success(distributionService.registerRecallCollect(request));
    }
}
