package com.pork.trace.controller;

import com.pork.core.result.Result;
import com.pork.trace.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    /**
     * 工作台聚合数据。
     * <p>
     * 角色由网关从 JWT 解出后透传（{@code X-User-Role}），本接口据此返回该角色关心的指标与图表。
     * 前端不再自己按角色挑选数据 —— 否则接口把全平台数据都发下去、页面只是不显示，
     * 属于「假隔离」，换个调用方就能拿到全部。
     * <p>
     * 机构（{@code X-User-Org}）同理：零售商的「销量」只能是<b>他自己那家店</b>的，
     * 这个值由网关从令牌里取，请求头里带的机构在网关那层已被丢弃。
     * <p>
     * 未带角色头（直连服务、未认证调用）按监管视角返回：只给合规相关，不含经营明细。
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Org", required = false) String org) {
        return Result.success(service.overview(role, org));
    }
}
