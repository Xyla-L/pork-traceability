package com.pork.trace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.trace.dto.ComplaintReportDTO;
import com.pork.trace.entity.ComplaintReport;
import com.pork.trace.vo.ComplaintReportVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 投诉举报 - Service 接口
 */
public interface ComplaintReportService extends IService<ComplaintReport> {

    /**
     * 提交投诉举报
     * @param dto 请求参数
     * @param deviceId 提交设备ID（小程序端匿名举报标识）
     * @return 举报编号
     */
    String submitComplaint(ComplaintReportDTO dto, String deviceId);

    Page<ComplaintReportVO> pageReports(String reportNo, String reporterName, String targetBatch,
                                         Integer status, String deviceId, long pageNum, long pageSize);

    ComplaintReportVO getReportDetail(String deviceId);

    void handleComplaint(Long id, Integer status, String handleNote, Long handlerUserId);
}
