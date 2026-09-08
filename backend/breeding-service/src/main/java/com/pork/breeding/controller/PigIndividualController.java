package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.PigIndividualDTO;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.vo.PigIndividualVO;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pigs")
@Tag(name = "生猪个体管理", description = "提供生猪档案的增删改查及分页查询功能")
@RequiredArgsConstructor
public class PigIndividualController {

    private final PigIndividualService pigIndividualService;

    @GetMapping({"", "/page"})
    @Operation(summary = "分页查询")
    public Result<PageResult<PigIndividualVO>> pageQuery(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "耳标号") @RequestParam(required = false) String earTagNo,
            @Parameter(description = "状态：1-在养,2-已出栏,3-已屠宰,4-异常死亡") @RequestParam(required = false) Integer status) {

        Page<PigIndividualVO> page = pigIndividualService.pageQuery(current, size, earTagNo, status);
        return Result.success(PageResult.of(page));
    }

    @PostMapping
    @Operation(summary = "新增生猪个体")
    public Result<Void> add(@Valid @RequestBody PigIndividualDTO dto) {
        pigIndividualService.addIndividual(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改生猪个体")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PigIndividualDTO dto) {
        dto.setId(id);
        pigIndividualService.updateIndividual(dto);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取详情")
    public Result<PigIndividualVO> getDetail(@PathVariable Long id) {
        PigIndividualVO vo = pigIndividualService.getDetail(id);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除生猪个体")
    public Result<Void> remove(@PathVariable Long id) {
        pigIndividualService.removeIndividual(id);
        return Result.success();
    }
}
