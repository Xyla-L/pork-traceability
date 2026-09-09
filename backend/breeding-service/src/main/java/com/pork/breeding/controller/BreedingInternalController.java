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
}
