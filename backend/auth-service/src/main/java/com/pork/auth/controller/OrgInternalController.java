package com.pork.auth.controller;

import com.pork.auth.dto.SystemRequests;
import com.pork.auth.entity.SysOrg;
import com.pork.auth.service.SystemAdminService;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机构登记的内部接口：供业务服务（如 breeding-service 的养殖场档案）在档案增改删后同步机构表。
 * <p>
 * 为什么需要它：机构存在两套登记——{@code sys_org}（机构管理 / 各页面下拉的数据源）与
 * 业务档案表（如 {@code farm}）。两者若不联动，机构管理就会「缺机构」。
 * 本接口由业务服务**容器内直连**调用（不经过网关），因此网关侧显式屏蔽了
 * {@code /api/v1/auth/internal/**}，避免外部越权登记机构。
 * </p>
 */
@RestController
@RequestMapping("/auth/internal/orgs")
@Tag(name = "机构登记（内部）", description = "业务档案变更时同步机构表，仅供服务间调用")
@RequiredArgsConstructor
public class OrgInternalController {

    private final SystemAdminService service;

    @PostMapping("/sync")
    @Operation(summary = "同步登记机构（按 type+name 判重，幂等）")
    public Result<Long> sync(@Valid @RequestBody SystemRequests.SyncOrg request) {
        return Result.success(service.syncOrg(request));
    }

    /**
     * 按 ID 查机构（含 type / name）。
     * <p>
     * 订单/计划的目的地门店必须来自机构树，业务服务拿它核对「这个门店真的存在吗」。
     * <b>查不到返回 null，由调用方转人工</b> —— 不能因为查不到就默认当作正常门店放行：
     * 门店 ID 是召回圈范围的关键字段，猜错等于圈错范围。
     */
    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查机构（供业务服务核对门店是否存在，查不到返回 null）")
    public Result<SysOrg> org(@PathVariable Long id) {
        return Result.success(service.org(id));
    }

    @DeleteMapping
    @Operation(summary = "按 type+name 移除机构（有下级或已关联用户时保留）")
    public Result<Boolean> remove(@RequestParam String type, @RequestParam String name) {
        return Result.success(service.removeOrgByName(type, name));
    }

    /**
     * 按角色 / 机构查活跃用户的收件人清单（业务服务发通知用）。
     * <p>
     * 通知是「谁的事」写死在收件人上：召回要通知涉事门店与冷链中心，调用方只能拿到机构 id
     * （销售记录里只有 store_id），user_id 只有 auth 服务知道 —— 所以这一步放在这里，
     * 而不是让每个业务服务各自读一份 sys_user。
     */
    @GetMapping("/users")
    @Operation(summary = "按角色/机构查活跃用户（供业务服务发通知，返回 userId/orgId/orgName）")
    public Result<java.util.List<java.util.Map<String, Object>>> users(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String orgIds) {
        java.util.List<Long> ids = orgIds == null || orgIds.isBlank() ? java.util.List.of()
                : java.util.Arrays.stream(orgIds.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(Long::valueOf).toList();
        return Result.success(service.internalRecipients(role, ids));
    }
}
