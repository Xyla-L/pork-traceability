package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.PigIndividualDTO;
import com.pork.breeding.dto.SlaughterApplyDTO;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.service.SlaughterApplyService;
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
    private final SlaughterApplyService slaughterApplyService;

    @GetMapping({"", "/page"})
    @Operation(summary = "分页查询")
    public Result<PageResult<PigIndividualVO>> pageQuery(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "耳标号") @RequestParam(required = false) String earTagNo,
            @Parameter(description = "养殖场ID") @RequestParam(required = false) Long farmId,
            @Parameter(description = "品种") @RequestParam(required = false) String breed,
            @Parameter(description = "状态：中文（在养/已出栏/已屠宰/异常）或数字 1-4") @RequestParam(required = false) String status,
            @Parameter(description = "出生日期起（YYYY-MM-DD）") @RequestParam(required = false) String birthDateStart,
            @Parameter(description = "出生日期止（YYYY-MM-DD）") @RequestParam(required = false) String birthDateEnd) {

        Page<PigIndividualVO> page = pigIndividualService.pageQuery(current, size, earTagNo, farmId, breed, status, birthDateStart, birthDateEnd);
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

    @PostMapping("/{pigId}/apply")
    @Operation(summary = "创建出栏申报")
    public Result<Long> applySlaughter(@PathVariable Long pigId, @Valid @RequestBody SlaughterApplyDTO dto) {
        // pigId 由路径变量注入，请求体无需携带
        dto.setPigId(pigId);
        return Result.success(slaughterApplyService.createApply(dto));
    }
}
