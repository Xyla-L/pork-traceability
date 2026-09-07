package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.VaccineRecordDTO;
import com.pork.breeding.service.VaccineRecordService;
import com.pork.breeding.vo.VaccineRecordVO;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pigs/{pigId}/vaccines")
@Tag(name = "疫苗记录管理", description = "提供生猪疫苗记录的增删改查功能")
@RequiredArgsConstructor
public class VaccineRecordController {

    private final VaccineRecordService vaccineRecordService;

    @GetMapping
    @Operation(summary = "分页查询疫苗记录列表")
    public Result<PageResult<VaccineRecordVO>> pageQuery(
            @PathVariable Long pigId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {
        Page<VaccineRecordVO> page = vaccineRecordService.pageQuery(current, size, pigId);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/list")
    @Operation(summary = "查询生猪所有疫苗记录")
    public Result<List<VaccineRecordVO>> listByPigId(@PathVariable Long pigId) {
        List<VaccineRecordVO> list = vaccineRecordService.listByPigId(pigId);
        return Result.success(list);
    }

    @PostMapping
    @Operation(summary = "记录疫苗注射")
    public Result<Void> add(@PathVariable Long pigId, @Valid @RequestBody VaccineRecordDTO dto) {
        dto.setPigId(pigId);
        vaccineRecordService.addRecord(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新疫苗记录")
    public Result<Void> update(@PathVariable Long pigId, @PathVariable Long id, @Valid @RequestBody VaccineRecordDTO dto) {
        dto.setId(id);
        dto.setPigId(pigId);
        vaccineRecordService.updateRecord(dto);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询疫苗记录详情")
    public Result<VaccineRecordVO> getDetail(@PathVariable Long id) {
        VaccineRecordVO vo = vaccineRecordService.getDetail(id);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除疫苗记录")
    public Result<Void> remove(@PathVariable Long id) {
        vaccineRecordService.removeRecord(id);
        return Result.success();
    }
}