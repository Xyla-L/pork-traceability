package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.VaccineRecordDTO;
import com.pork.breeding.service.VaccineRecordService;
import com.pork.breeding.vo.VaccineRecordVO;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.web.source.VoidRequest;
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
    @Operation(summary = "查询生猪所有疫苗记录（includeVoid=true 时含已作废记录并带作废痕迹）")
    public Result<List<VaccineRecordVO>> listByPigId(
            @PathVariable Long pigId,
            @RequestParam(defaultValue = "false") boolean includeVoid) {
        List<VaccineRecordVO> list = vaccineRecordService.listByPigId(pigId, includeVoid);
        return Result.success(list);
    }

    /* 人工录入入口已删除：免疫注射由智能连续注射器设备通道自动记录
       （/breeding/internal/ingest/vaccine），不再提供人工录入接口。 */


    @GetMapping("/{id}")
    @Operation(summary = "查询疫苗记录详情")
    public Result<VaccineRecordVO> getDetail(@PathVariable Long pigId, @PathVariable Long id) {
        VaccineRecordVO vo = vaccineRecordService.getDetail(pigId, id);
        return Result.success(vo);
    }

    @PostMapping("/{id}/void")
    @Operation(summary = "作废疫苗记录（原删除）：需填写作废理由，记录保留不物理删除")
    public Result<Void> voidRecord(@PathVariable Long pigId, @PathVariable Long id,
                                   @Valid @RequestBody VoidRequest request) {
        vaccineRecordService.voidRecord(pigId, id, request.getReason());
        return Result.success();
    }
}