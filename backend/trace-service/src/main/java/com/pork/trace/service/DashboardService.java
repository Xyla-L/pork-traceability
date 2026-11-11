package com.pork.trace.service;

import java.util.Map;

public interface DashboardService {
    /**
     * 按角色（并按其所属机构）返回工作台数据。
     *
     * @param role 网关从 JWT 解出后透传的角色码（ADMIN / FARMER / SLAUGHTER_OP / DISTRIBUTOR / RETAILER / SUPERVISOR）；
     *             为空或无法识别时按监管视角返回
     * @param org  网关从 JWT 解出后透传的机构ID（{@code sys_user.org_id}）。
     *             <b>零售商的口径边界就是它</b>：销量、临期都只统计这家门店；
     *             其他角色当前不使用该参数（他们的边界不是门店）。
     */
    Map<String, Object> overview(String role, String org);
}
