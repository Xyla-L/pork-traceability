package com.pork.breeding.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.breeding.entity.PigIndividual;
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

import java.util.List;

@RestController
@RequestMapping("/breeding/internal/pigs")
@RequiredArgsConstructor
public class BreedingInternalController {
    private final PigIndividualService service;

    @GetMapping("/by-ear-tag")
    public Result<PigIndividualVO> byEarTag(@RequestParam String earTagNo) {
        PigIndividual pig = service.getOne(Wrappers.<PigIndividual>lambdaQuery()
                .eq(PigIndividual::getEarTagNo, earTagNo));
        if (pig == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "耳标对应的生猪档案不存在");
        return Result.success(service.getDetail(pig.getId()));
    }

    @GetMapping
    public Result<List<PigIndividualVO>> byIds(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Result.success(List.of());
        if (ids.size() > 200) throw new BusinessException(ErrorCode.PARAM_ERROR, "单次最多查询200头生猪");
        return Result.success(ids.stream().distinct().map(service::getDetail).toList());
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
