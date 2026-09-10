package com.pork.trace.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
        report.setReporterName("用户" + userId);
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
                .eq(ComplaintReport::getUserId,userId)
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
