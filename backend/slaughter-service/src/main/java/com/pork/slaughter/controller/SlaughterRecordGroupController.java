package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.service.SlaughterRecordGroupService;
import com.pork.slaughter.vo.SlaughterGroupVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 屠宰四张列表的「按批次折叠」入口。
 * <p>
 * 与 {@code /slaughter/entries} 这类逐头列表<b>并存</b>：逐头列表是事实来源与单头追溯入口，
 * 这里只换一种读法（一批一行），筛选参数与原接口一致。写操作一律走原接口。
 */
@RestController
@RequestMapping("/slaughter/records")
@Tag(name = "屠宰记录按批次折叠", description = "入场查验/屠宰检验/检疫盖章/瘦肉精检测按批次折叠成一行，明细逐头展开")
@RequiredArgsConstructor
public class SlaughterRecordGroupController {

    private final SlaughterRecordGroupService groupService;

    @GetMapping("/entries")
    @Operation(summary = "入场查验按批次折叠")
    public Result<PageResult<SlaughterGroupVO>> entries(
            EntryInspectionDTO dto,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(PageResult.of(groupService.pageEntries(current, size, dto)));
    }

    @GetMapping("/inspections")
    @Operation(summary = "屠宰检验按批次折叠")
    public Result<PageResult<SlaughterGroupVO>> inspections(
            SlaughterInspectionDTO dto,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(PageResult.of(groupService.pageInspections(current, size, dto)));
    }

    @GetMapping("/stamps")
    @Operation(summary = "检疫盖章按批次折叠")
    public Result<PageResult<SlaughterGroupVO>> stamps(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Long pigId,
            @RequestParam(required = false) String stampNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String carcassNo,
            @RequestParam(required = false) String stampType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        CarcassStampDTO query = new CarcassStampDTO();
        query.setPigId(pigId);
        query.setStampNo(stampNo);
        query.setBatchNo(batchNo);
        query.setCarcassNo(carcassNo);
        query.setStampType(stampType);
        Page<SlaughterGroupVO> page = groupService.pageStamps(current, size, query, status, startDate, endDate);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/ractopamine")
    @Operation(summary = "瘦肉精检测按批次折叠")
    public Result<PageResult<SlaughterGroupVO>> ractopamine(
            RactopamineTestDTO dto,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(PageResult.of(groupService.pageRactopamine(current, size, dto)));
    }
}
