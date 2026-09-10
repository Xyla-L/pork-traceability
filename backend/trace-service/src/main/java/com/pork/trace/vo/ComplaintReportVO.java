package com.pork.trace.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 投诉举报 - 视图对象
 */
@Data
public class ComplaintReportVO {

    private Long id;
    private String reportNo;
    private String reporterName;
    private String targetQrCode;
    private String complaintText;
    private String fileIds;
    private Integer status;
    private String statusText; // 状态文本描述，如"待受理"
    private String handler;
    private String handleNote;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
    private String reporterPhone;
}