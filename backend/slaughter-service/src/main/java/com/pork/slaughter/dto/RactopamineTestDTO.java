package com.pork.slaughter.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class RactopamineTestDTO {

    @NotNull(message = "生猪ID不能为空")
    private Long pigId;

    @NotBlank(message = "检测编号不能为空")
    private String testNo;

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    private String earTagNo;

    @NotBlank(message = "样本编号不能为空")
    private String sampleNo;

    @NotBlank(message = "检测类型不能为空")
    private String testType;

    @NotNull(message = "检测时间不能为空")
    private LocalDateTime testTime;

    @NotBlank(message = "检测方法不能为空")
    private String testMethod;

    private String testTarget;

    private String samplePart;

    // 查询时支持中文("阴性/阳性/待检测")或数字编码
    private String result;

    private Integer status;

    private String detectionLimit;

    @NotBlank(message = "检测人员不能为空")
    private String operator;

    private String fileIds;

    private String reportUrl;
    private String remark;

    // --- 自动录入（设备通道）来源标记 ---
    private String source;
    private String sourceRef;
    private String rawPayload;

    // --- 查询条件：检测日期区间 YYYY-MM-DD ---
    private String startDate;
    private String endDate;

    // --- 分页参数 ---
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
