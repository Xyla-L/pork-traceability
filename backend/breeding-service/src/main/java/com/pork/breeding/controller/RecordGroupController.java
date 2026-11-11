package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.service.RecordGroupService;
import com.pork.breeding.vo.RecordGroupVO;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「按批次分组」的记录视图入口。
 * <p>
 * 与 {@code /pigs/{pigId}/vaccines} 这类逐头接口<b>并存</b>：逐头接口是事实来源与单头追溯入口，
 * 这里只换了一种读法（一次操作一行）。写操作一律仍走原接口，分组视图不新增任何写路径。
 */
@RestController
@RequestMapping("/records")
@Tag(name = "记录分组视图", description = "把逐头存储的免疫/检疫/申报记录按批次聚合成「一次操作一行」")
@RequiredArgsConstructor
public class RecordGroupController {

    private final RecordGroupService recordGroupService;

    @GetMapping("/vaccines")
    @Operation(summary = "免疫记录按批次分组（主行为一次免疫，明细逐头）")
    public Result<PageResult<RecordGroupVO>> pageVaccines(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String earTagNo,
            @RequestParam(required = false) String vaccineName,
            @RequestParam(defaultValue = "true") boolean includeVoid) {
        Page<RecordGroupVO> page = recordGroupService.pageVaccine(current, size, earTagNo, vaccineName, includeVoid);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/certs")
    @Operation(summary = "产地检疫按批次分组（主行为一次出证，明细逐头）")
    public Result<PageResult<RecordGroupVO>> pageCerts(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) String earTagNo,
            @RequestParam(required = false) String certState) {
        Page<RecordGroupVO> page = recordGroupService.pageCert(current, size, earTagNo, certState);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/applies")
    @Operation(summary = "出栏申报按批次分组（主行为一次出栏申报，明细逐头）")
    public Result<PageResult<RecordGroupVO>> pageApplies(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer approvalStatus) {
        Page<RecordGroupVO> page = recordGroupService.pageApply(current, size, approvalStatus);
        return Result.success(PageResult.of(page));
    }
}
