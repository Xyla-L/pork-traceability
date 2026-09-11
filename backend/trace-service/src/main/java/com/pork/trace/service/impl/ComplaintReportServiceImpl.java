package com.pork.trace.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.trace.dto.ComplaintReportDTO;
import com.pork.trace.entity.ComplaintReport;
import com.pork.trace.mapper.ComplaintReportMapper;
import com.pork.trace.service.ComplaintReportService;
import com.pork.trace.vo.ComplaintReportVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 投诉举报 - Service 实现类
 */
@Service
@RequiredArgsConstructor
public class ComplaintReportServiceImpl extends ServiceImpl<ComplaintReportMapper, ComplaintReport>
        implements ComplaintReportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final StringRedisTemplate redis;

    /**
     * 提交投诉举报
     * 使用 @Transactional 确保数据一致性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitComplaint(ComplaintReportDTO dto, Long userId) {
        String reportNo = nextReportNo();

        // 2. 转换 DTO 为 Entity
        ComplaintReport report = new ComplaintReport();
        BeanUtils.copyProperties(dto, report);
        report.setReportNo(reportNo);
        report.setReporterName(userId == null ? "匿名用户" : "用户" + userId);
        report.setStatus(0);
        report.setCreateTime(LocalDateTime.now());
        report.setUserId(userId);

        // 3. 保存到数据库
        if (!this.save(report)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "举报保存失败");

        return reportNo;
    }

    @Override
    public ComplaintReportVO getReportDetail(Long userId) {
        ComplaintReport report = this.lambdaQuery()
                .eq(ComplaintReport::getId, userId)
                .one();

        if (report == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "举报记录不存在");
        }
        ComplaintReportVO vo = new ComplaintReportVO();
        BeanUtils.copyProperties(report, vo);
        // 转换状态码为文本
        vo.setStatusText(getStatusText(report.getStatus()));
        return vo;
    }

    @Override
    public Page<ComplaintReportVO> pageReports(String reportNo, String reporterName, String targetBatch,
                                                Integer status, long pageNum, long pageSize) {
        long safePage = Math.max(pageNum, 1);
        long safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<ComplaintReport> page = new Page<>(safePage, safeSize);
        Page<ComplaintReport> result = page(page, Wrappers.<ComplaintReport>lambdaQuery()
                .like(reportNo != null && !reportNo.isBlank(), ComplaintReport::getReportNo, reportNo)
                .like(reporterName != null && !reporterName.isBlank(), ComplaintReport::getReporterName, reporterName)
                .like(targetBatch != null && !targetBatch.isBlank(), ComplaintReport::getTargetBatch, targetBatch)
                .eq(status != null, ComplaintReport::getStatus, status)
                .orderByDesc(ComplaintReport::getCreateTime));
        Page<ComplaintReportVO> views = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        views.setRecords(result.getRecords().stream().map(this::toView).toList());
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleComplaint(Long id, Integer status, String handleNote, String handler) {
        if (status == null || status < 1 || status > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "举报处理状态无效");
        }
        ComplaintReport report = getById(id);
        if (report == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "举报记录不存在");
        if (handleNote == null || handleNote.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "处理回复不能为空");
        }
        report.setStatus(status);
        report.setHandler(handler);
        report.setHandleNote(handleNote.trim());
        report.setHandleTime(LocalDateTime.now());
        if (!updateById(report)) throw new BusinessException(ErrorCode.DATABASE_ERROR, "举报处理保存失败");
    }

    private ComplaintReportVO toView(ComplaintReport report) {
        ComplaintReportVO vo = new ComplaintReportVO();
        BeanUtils.copyProperties(report, vo);
        vo.setStatusText(getStatusText(report.getStatus()));
        return vo;
    }

    private String nextReportNo() {
        LocalDate today = LocalDate.now();
        String key = "trace:complaint:sequence:" + DATE.format(today);
        Long sequence = redis.opsForValue().increment(key);
        if (sequence == null || sequence > 9999) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当日举报编号已用尽");
        }
        if (sequence == 1) redis.expire(key, 2, TimeUnit.DAYS);
        return "CP" + DATE.format(today) + String.format("%04d", sequence);
    }

    private String getStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "待受理";
            case 1 -> "处理中";
            case 2 -> "已办结";
            case 3 -> "已驳回";
            default -> "未知";
        };
    }
}
