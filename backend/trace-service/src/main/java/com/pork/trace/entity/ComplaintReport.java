package com.pork.trace.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投诉举报 - 实体类
 * 对应数据库表: complaint_report
 */
@Data
@TableName(value = "complaint_report", autoResultMap = true)
@Getter
public class ComplaintReport {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 举报编号 (CP+yyyymmdd+4位序号)
     */
    private String reportNo;

    /**
     * 举报人姓名（可匿名）
     */
    private String reporterName;

    /**
     * 举报人联系电话
     */
    private String reporterPhone;

    /**
     * 被举报产品二维码
     */
    private String targetQrCode;

    /**
     * 被举报批次号
     */
    private String targetBatch;

    /**
     * 举报内容描述
     */
    private String complaintText;

    /**
     * 上传的问题肉品照片/视频文件ID (JSON数组格式)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fileIds;

    /**
     * 处理状态：0待受理、1处理中、2已办结、3已驳回
     */
    private Integer status;

    /**
     * 处理人
     */
    private String handler;

    /**
     * 处理备注/回复
     */
    private String handleNote;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 创建时间
     * 插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 提交设备ID（小程序端匿名举报标识）
     */
    private String deviceId;
}
