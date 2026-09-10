package com.pork.trace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 投诉举报 - 请求数据传输对象
 */
@Data
public class ComplaintReportDTO {

    /**
     * 被举报产品二维码
     */
    @NotBlank(message = "被举报产品二维码不能为空")
    private String targetQrCode;

    /**
     * 被举报批次号
     */
    private String targetBatch;

    /**
     * 举报内容描述
     */
    @NotBlank(message = "举报内容不能为空")
    private String complaintText;

    /**
     * 上传的问题肉品照片/视频文件ID，JSON数组格式
     */
    private List<String> fileIds;

    private String reporterPhone;

}