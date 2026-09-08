package com.pork.trace.controller;

import com.pork.trace.dto.ComplaintReportDTO;
import com.pork.trace.service.ComplaintReportService;
import com.pork.trace.vo.ComplaintReportVO;
import com.pork.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
/**
 * 投诉举报 - 控制器
 */
@RestController
@RequestMapping("/trace/complaints")
public class ComplaintController {

    private final ComplaintReportService complaintReportService;

    public ComplaintController(ComplaintReportService complaintReportService) {
        this.complaintReportService = complaintReportService;
    }

    /**
     * 提交投诉举报
     */
    @PostMapping
    public Result<String> submit(@Valid @RequestBody ComplaintReportDTO dto,
                                 @RequestHeader("X-User-Id") Long userId) {
        String reportNo = complaintReportService.submitComplaint(dto, userId);
        return Result.success("举报提交成功", reportNo);
    }

    /**
     * 查询举报详情
     */
    @GetMapping("/{id}")
    public Result<ComplaintReportVO> getDetail(@PathVariable Long id) {
        ComplaintReportVO vo = complaintReportService.getReportDetail(id);
        return Result.success(vo);
    }
}
