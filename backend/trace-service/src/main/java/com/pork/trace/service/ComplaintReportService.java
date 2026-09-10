package com.pork.trace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.trace.dto.ComplaintReportDTO;
import com.pork.trace.entity.ComplaintReport;
import com.pork.trace.vo.ComplaintReportVO;

/**
 * 投诉举报 - Service 接口
 */
public interface ComplaintReportService extends IService<ComplaintReport> {

    /**
     * 提交投诉举报
     * @param dto 请求参数
     * @param userId 当前登录用户ID
     * @return 举报编号
     */
    String submitComplaint(ComplaintReportDTO dto, Long userId);

    /**
     * 根据ID查询举报详情
     * @param userId 举报ID
     * @return 举报详情VO
     */
    ComplaintReportVO getReportDetail(Long userId);
}