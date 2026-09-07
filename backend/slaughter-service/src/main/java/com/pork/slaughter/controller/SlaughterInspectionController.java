package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.service.SlaughterInspectionService;
import com.pork.slaughter.vo.SlaughterInspectionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/slaughter/inspections")
public class SlaughterInspectionController {

    @Autowired
    private SlaughterInspectionService slaughterInspectionService;

    @PostMapping
    public Result<Void> add(@RequestBody SlaughterInspectionDTO dto) {
        slaughterInspectionService.addInspection(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SlaughterInspectionDTO dto) {
        slaughterInspectionService.updateInspection(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        slaughterInspectionService.deleteInspection(id);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<PageResult<SlaughterInspectionVO>> pageQuery(SlaughterInspectionDTO dto) {
        Page<SlaughterInspectionVO> page = slaughterInspectionService.pageQuery(dto);
        return Result.success(PageResult.of(page));
    }
}