package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.PigBatchDTO;
import com.pork.breeding.service.PigBatchService;
import com.pork.breeding.support.FarmScope;
import com.pork.breeding.vo.PigBatchVO;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.web.source.VoidRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 生猪批次档案：养殖端档案的登记单位。
 *
 * <p>现实中商品猪按批次管理——同场、同批进、同批出。人工登记一张批次表单，
 * 耳标起止号之间的每头猪自动成为批次成员；个体信息不再逐头录入，
 * 设备读到的任何耳标只要落在某个批次的区间内就直接命中档案。
 */
@RestController
@RequestMapping("/pigs/batches")
@Tag(name = "生猪批次档案", description = "按批次登记养殖档案，耳标区间内的猪自动成为批次成员")
@RequiredArgsConstructor
public class PigBatchController {

    private final PigBatchService pigBatchService;
    private final FarmScope farmScope;

    @GetMapping({"", "/page"})
    @Operation(summary = "分页查询")
    public Result<PageResult<PigBatchVO>> pageQuery(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "批次号") @RequestParam(required = false) String batchNo,
            @Parameter(description = "养殖场ID") @RequestParam(required = false) Long farmId,
            @Parameter(description = "品种") @RequestParam(required = false) String breed) {
        // 机构隔离：养殖角色强制看自己场（与生猪档案同一条纪律）
        Page<PigBatchVO> page = pigBatchService.pageQuery(current, size, batchNo, farmScope.restrict(farmId), breed);
        return Result.success(PageResult.of(page));
    }

    @PostMapping
    @Operation(summary = "人工登记批次",
            description = "除耳标号（区间）外，批次信息由人工登记；区间内的每头猪自动成为批次成员")
    public Result<Long> create(@Valid @RequestBody PigBatchDTO dto) {
        return Result.success(pigBatchService.createBatch(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "批次详情（含成员状态汇总）")
    public Result<PigBatchVO> getDetail(@PathVariable Long id) {
        return Result.success(pigBatchService.getDetail(id));
    }

    @PostMapping("/{id}/void")
    @Operation(summary = "作废批次：需填写作废理由，批次与成员行保留不物理删除")
    public Result<Void> voidBatch(@PathVariable Long id, @Valid @RequestBody VoidRequest request) {
        pigBatchService.voidBatch(id, request.getReason());
        return Result.success();
    }
}
