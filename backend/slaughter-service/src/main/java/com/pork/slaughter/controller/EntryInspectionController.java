package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.service.EntryInspectionService;
import com.pork.slaughter.vo.EntryInspectionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/entry")
@RequiredArgsConstructor
public class EntryInspectionController {

    private final EntryInspectionService entryInspectionService;

    /**
     * 新增入场查验记录
     */
    @PostMapping("/add")
    public Result<Boolean> addEntry(@Valid @RequestBody EntryInspectionDTO dto) {
        return Result.success(entryInspectionService.addEntry(dto));
    }

    /**
     * 修改入场查验记录
     */
    @PostMapping("/update")
    public Result<Boolean> updateEntry(@PathVariable Long id, @Valid @RequestBody EntryInspectionDTO dto) {
        return Result.success(entryInspectionService.updateEntry(id, dto));
    }

    /**
     * 删除入场查验记录
     */
    @PostMapping("/delete/{id}")
    public Result<Boolean> deleteEntry(@PathVariable Long id) {
        return Result.success(entryInspectionService.deleteEntry(id));
    }

    /**
     * 分页查询入场查验列表
     */
    @GetMapping("/page")
    public Result<PageResult<EntryInspectionVO>> listEntries(
            EntryInspectionDTO dto,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<EntryInspectionVO> page = entryInspectionService.pageQuery(dto, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }
}