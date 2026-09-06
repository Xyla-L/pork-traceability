package com.pork.trace.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.pork.trace.dto.ComplaintReportDTO;
import com.pork.trace.service.ComplaintReportService;
import com.pork.trace.vo.ComplaintReportVO;
import com.pork.core.result.Result; // 假设你有统一返回结果类
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.pork.core.enums.ErrorCode;
/**
 * 投诉举报 - 控制器
 */
@RestController
@RequestMapping("/complaint")
public class ComplaintController {

    private final ComplaintReportService complaintReportService;

    public ComplaintController(ComplaintReportService complaintReportService) {
        this.complaintReportService = complaintReportService;
    }

    /**
     * 提交投诉举报
     */
    @PostMapping
    public Result<String> submit(@Valid @RequestBody ComplaintReportDTO dto) {
        // 从 Sa-Token 获取当前登录用户ID
        Long userId = StpUtil.getLoginIdAsLong();
        String reportNo = complaintReportService.submitComplaint(dto, userId);
        return Result.success(reportNo, "举报提交成功");
    }

    /**
     * 查询举报详情
     */
    @GetMapping("/{id}")
    public Result<ComplaintReportVO> getDetail(@PathVariable Long id) {
        ComplaintReportVO vo = complaintReportService.getReportDetail(id);
        if (vo == null) {
            return Result.fail(ErrorCode.RECORD_NOT_FOUND);
        }
        return Result.success(vo);
    }
}