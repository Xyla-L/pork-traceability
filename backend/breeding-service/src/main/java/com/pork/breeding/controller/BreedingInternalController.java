package com.pork.breeding.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.vo.PigIndividualVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/breeding/internal/pigs")
@RequiredArgsConstructor
public class BreedingInternalController {
    private final PigIndividualService service;
    private final PigIndividualMapper pigIndividualMapper;

    @GetMapping("/by-ear-tag")
    public Result<PigIndividualVO> byEarTag(@RequestParam String earTagNo) {
        PigIndividual pig = service.getOne(Wrappers.<PigIndividual>lambdaQuery()
                .eq(PigIndividual::getEarTagNo, earTagNo));
        if (pig == null) {
            // 逻辑删除会滤掉作废档案，这里补一句说明，避免把「已作废」误当成「从未建档」
            PigIndividual voided = pigIndividualMapper.selectVoidedByEarTag(earTagNo);
            if (voided != null) {
                throw new BusinessException(ErrorCode.RECORD_NOT_FOUND,
                        "该耳标对应的生猪档案已被作废，不可再参与业务。作废理由：" + voided.getVoidReason());
            }
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "耳标对应的生猪档案不存在");
        }
        return Result.success(service.getDetail(pig.getId()));
    }

    /**
     * 按 id 批量取生猪档案，供追溯链使用。
     * <p>
     * 只返回当前有效档案的业务数据；档案若已被作废，返回一条仅带 id/耳标号与作废标注的空壳，
     * 由上游决定怎么展示——追溯里「已作废」是一个必须能看见的状态，不能等同于「没有这头猪」。
     */
    @GetMapping
    public Result<List<PigIndividualVO>> byIds(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Result.success(List.of());
        if (ids.size() > 200) throw new BusinessException(ErrorCode.PARAM_ERROR, "单次最多查询200头生猪");
        List<PigIndividualVO> result = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            PigIndividualVO detail = service.getTraceDetail(id);
            if (detail != null) result.add(detail);
        }
        return Result.success(result);
    }

    /**
     * 生猪当前状态（供下游做「这头猪还能不能再进入下一个环节」的判断）。
     * <p>
     * 只读、按 id 查。档案不存在时直接 404，**不能**给调用方一个「查不到就默认正常」的返回——
     * 下游据此放行的话，等于把「不知道」当成「可以」。调用方拿到异常应转人工核对。
     */
    @GetMapping("/{id}/state")
    public Result<PigIndividualVO> state(@PathVariable Long id) {
        return Result.success(service.getDetail(id));
    }

    /**
     * 内部状态推进（仅供下游服务同步）：1在养 2已出栏 3已屠宰 4异常
     * 只允许向前推进，不允许回退，已屠宰/异常为终态
     */
    @PutMapping("/{id}/status")
    public Result<Void> advanceStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status == null || status < 2 || status > 4)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法的生猪状态");
        PigIndividual pig = service.getById(id);
        if (pig == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "生猪档案不存在");
        Integer current = pig.getStatus();
        if (current != null && current >= status) return Result.success(); // 已到达或超过目标状态，幂等返回
        PigIndividual update = new PigIndividual();
        update.setId(id);
        update.setStatus(status);
        service.updateById(update);
        return Result.success();
    }
}
