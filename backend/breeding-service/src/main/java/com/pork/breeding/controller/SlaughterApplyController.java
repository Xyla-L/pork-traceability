package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.SlaughterApplyApproveDTO;
import com.pork.breeding.service.SlaughterApplyService;
import com.pork.breeding.vo.SlaughterApplyVO;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/applies")
@Tag(name = "出栏申报管理", description = "提供出栏申报的查询和审批功能")
@RequiredArgsConstructor
public class SlaughterApplyController {

    private final SlaughterApplyService slaughterApplyService;

    @GetMapping({"", "/page"})
    @Operation(summary = "分页查询出栏申报列表")
    public Result<PageResult<SlaughterApplyVO>> pageQuery(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer approvalStatus) {
        Page<SlaughterApplyVO> page = slaughterApplyService.pageQuery(current, size, approvalStatus);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/counts")
    @Operation(summary = "按审批状态统计数量")
    public Result<Map<Integer, Long>> countByStatus() {
        return Result.success(slaughterApplyService.countByStatus());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询出栏申报详情")
    public Result<SlaughterApplyVO> getDetail(@PathVariable Long id) {
        SlaughterApplyVO vo = slaughterApplyService.getDetail(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "审批出栏申报")
    public Result<Void> approve(@PathVariable Long id,
                                @Valid @RequestBody SlaughterApplyApproveDTO dto,
                                @RequestHeader(value = "X-User-Id", required = false) String userId) {
        slaughterApplyService.approve(id, dto, userId);
        return Result.success();
    }
}
