package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.FarmDTO;
import com.pork.breeding.service.FarmService;
import com.pork.breeding.vo.FarmVO;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/farms")
@Tag(name = "养殖场管理", description = "提供养殖场的增删改查功能")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @GetMapping({"", "/page"})
    @Operation(summary = "分页查询养殖场列表")
    public Result<PageResult<FarmVO>> pageQuery(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String farmName) {
        Page<FarmVO> page = farmService.pageQuery(current, size, farmName);
        return Result.success(PageResult.of(page));
    }

    @PostMapping
    @Operation(summary = "创建养殖场")
    public Result<Void> add(@Valid @RequestBody FarmDTO dto) {
        farmService.addFarm(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新养殖场信息")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody FarmDTO dto) {
        dto.setId(id);
        farmService.updateFarm(dto);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询养殖场详情")
    public Result<FarmVO> getDetail(@PathVariable Long id) {
        FarmVO vo = farmService.getDetail(id);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除养殖场")
    public Result<Void> remove(@PathVariable Long id) {
        farmService.removeFarm(id);
        return Result.success();
    }
}
