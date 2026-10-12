package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.service.EntryInspectionService;
import com.pork.slaughter.vo.EntryInspectionVO;
import com.pork.web.source.VoidRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/slaughter/entries")
@RequiredArgsConstructor
public class EntryInspectionController {

    private final EntryInspectionService entryInspectionService;

    /* 人工新增入口已删除：入场查验为设备独占环节，数据只接受闸口设备通道上报，
       设备报文异常走「设备接入 → 待人工处理」实名复核，不再提供人工补录接口。 */


    /**
     * 作废入场查验记录：不再提供物理删除——链上已有哈希，删掉记录等于让追溯链断在这里
     */
    @PostMapping("/{id}/void")
    public Result<Boolean> voidEntry(@PathVariable Long id, @Valid @RequestBody VoidRequest request) {
        return Result.success(entryInspectionService.voidEntry(id, request.getReason()));
    }

    /**
     * 分页查询入场查验列表
     */
    @GetMapping
    public Result<PageResult<EntryInspectionVO>> listEntries(
            EntryInspectionDTO dto,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<EntryInspectionVO> page = entryInspectionService.pageQuery(dto, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }
}
