package com.pork.breeding.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.entity.SlaughterApply;
import com.pork.breeding.mapper.SlaughterApplyMapper;
import com.pork.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 出栏审批状态内部查询：供下游（屠宰服务）在入场查验前核对「这头猪到底批没批」。
 * <p>
 * 为什么要有这个接口：入场查验的硬前提是出栏审批已通过。未经审批就进厂，等于把出栏审批
 * 这道人的责任关卡绕过去了。判断口径必须只有一份（待审/通过/驳回 的中文说法也只有一份），
 * 所以放在养殖服务里出，各消费方直接引用，避免「A 服务拦了 B 服务没拦」。
 */
@RestController
@RequestMapping("/breeding/internal/applies")
@RequiredArgsConstructor
public class SlaughterApplyInternalController {

    /** 审批状态：待审 / 通过 / 驳回（与 SlaughterApplyServiceImpl 一致） */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    private final SlaughterApplyMapper applyMapper;

    /**
     * 按生猪查最新一条出栏申报的审批状态。
     * <p>
     * 一头猪在库上只允许一条申报（uk_pig），取最新一条即为当前有效口径。
     * 查不到不报错：返回 exists=false —— 「从未申报」本身就是一个必须能表达的业务事实，
     * 报异常会让调用方把「没申报」误当成「服务故障」而转人工排队。
     */
    @GetMapping("/by-pig")
    public Result<Map<String, Object>> byPig(@RequestParam Long pigId) {
        SlaughterApply apply = pigId == null ? null : applyMapper.selectOne(
                Wrappers.<SlaughterApply>lambdaQuery()
                        .eq(SlaughterApply::getPigId, pigId)
                        .orderByDesc(SlaughterApply::getId)
                        .last("LIMIT 1"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pigId", pigId);
        body.put("exists", apply != null);
        body.put("approved", apply != null && Integer.valueOf(STATUS_APPROVED).equals(apply.getApprovalStatus()));
        body.put("approvalStatus", apply == null ? null : apply.getApprovalStatus());
        body.put("applyNo", apply == null ? null : apply.getApplyNo());
        body.put("approver", apply == null ? null : apply.getApprover());
        body.put("targetSlaughterhouse", apply == null ? null : apply.getTargetSlaughterhouse());
        // 未通过时给一句可直接展示的中文原因，消费方不必再各自翻译一遍状态码
        body.put("reason", denyReason(apply));
        return Result.success(body);
    }

    /** 审批未通过时的原因（已通过返回 null） */
    private String denyReason(SlaughterApply apply) {
        if (apply == null) return "没有出栏申报记录";
        Integer status = apply.getApprovalStatus();
        if (status == null) return "出栏申报状态异常";
        return switch (status) {
            case STATUS_PENDING -> "出栏申报仍在等待审批";
            case STATUS_REJECTED -> "出栏申报已被驳回";
            case STATUS_APPROVED -> null;
            default -> "出栏申报状态异常";
        };
    }
}
