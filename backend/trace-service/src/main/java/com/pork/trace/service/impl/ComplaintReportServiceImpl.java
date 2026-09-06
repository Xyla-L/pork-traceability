package com.pork.trace.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.trace.dto.ComplaintReportDTO;
import com.pork.trace.entity.ComplaintReport;
import com.pork.trace.mapper.ComplaintReportMapper;
import com.pork.trace.service.ComplaintReportService;
import com.pork.trace.vo.ComplaintReportVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投诉举报 - Service 实现类
 */
@Service
public class ComplaintReportServiceImpl extends ServiceImpl<ComplaintReportMapper, ComplaintReport>
        implements ComplaintReportService {

    /**
     * 提交投诉举报
     * 使用 @Transactional 确保数据一致性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitComplaint(ComplaintReportDTO dto, Long userId) {
        // 1. 生成举报编号 CP + yyyymmdd + 4位序号 (简化版，生产环境建议用Redis incr)
        String reportNo = "CP" + LocalDate.now().toString().replace("-", "") + String.format("%04d", (int)(System.currentTimeMillis() % 10000));

        // 2. 转换 DTO 为 Entity
        ComplaintReport report = new ComplaintReport();
        BeanUtils.copyProperties(dto, report);
        report.setReportNo(reportNo);
        report.setReporterName("用户" + userId); // 实际应从用户服务获取真实姓名
        report.setStatus(0); // 默认状态：待受理

        // 3. 保存到数据库
        this.save(report);

        return reportNo;
    }

    @Override
    public ComplaintReportVO getReportDetail(Long id) {
        ComplaintReport report = this.getById(id);
        if (report == null) {
            return null;
        }
        ComplaintReportVO vo = new ComplaintReportVO();
        BeanUtils.copyProperties(report, vo);
        // 转换状态码为文本
        vo.setStatusText(getStatusText(report.getStatus()));
        return vo;
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