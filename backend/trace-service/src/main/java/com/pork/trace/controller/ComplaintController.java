package com.pork.trace.controller;

import com.pork.trace.dto.ComplaintReportDTO;
import com.pork.trace.service.ComplaintReportService;
import com.pork.trace.vo.ComplaintReportVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
                                 @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                 @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        String reportNo = complaintReportService.submitComplaint(dto, userId, deviceId);
        return Result.success("举报提交成功", reportNo);
    }

    /**
     * 查询举报详情
     */
    @GetMapping("/{id}")
    public Result<ComplaintReportVO> getDetail(@PathVariable Long id,
                                               @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        ComplaintReportVO vo = complaintReportService.getReportDetail(id, deviceId);
        return Result.success(vo);
    }

    @GetMapping
    public Result<Page<ComplaintReportVO>> list(@RequestParam(required = false) String reportNo,
                                                @RequestParam(required = false) String reporterName,
                                                @RequestParam(required = false) String targetBatch,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "20") long pageSize,
                                                @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return Result.success(complaintReportService.pageReports(reportNo, reporterName, targetBatch,
                status, deviceId, pageNum, pageSize));
    }

    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody HandleRequest request,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId,
                               @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (userId == null || role == null || !("SUPERVISOR".equals(role) || "ADMIN".equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅监管人员可处理举报");
        }
        complaintReportService.handleComplaint(id, request.status(), request.handleNote(), userId);
        return Result.success();
    }

    public record HandleRequest(Integer status, String handleNote) { }
}
