package com.pork.slaughter.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RactopamineTestVO {

    private Long id;

    private Long pigId;

    private String testNo;

    private String batchNo;

    private String earTagNo;

    private String sampleNo;

    private String testType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime testTime;

    private String testMethod;

    private String testTarget;

    private String samplePart;

    private Integer result;

    private Integer status;

    private String detectionLimit;

    private String operator;

    /** 数据来源 MANUAL/DEVICE/API/IMPORT，前端据此区分人工录入与仪器自动采集 */
    private String source;
    /** 来源设备号/单据号 */
    private String sourceRef;

    private String reportUrl;

    private String remark;

    private String fileIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}