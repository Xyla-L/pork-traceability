package com.pork.slaughter.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EntryInspectionVO {
    private Long id;
    private Long pigId;
    private String batchNo;
    private String earTagNo;
    private String sourceFarm;
    private LocalDateTime arriveTime;
    private java.math.BigDecimal weight;
    private String quarantineCert;
    private String vehicleNo;
    private Integer healthCheck; // 1通过 0异常
    private Integer certVerified; // 1通过 0异常
    private String healthCheckLabel; // "通过" 或 "异常"
    private String certVerifiedLabel;
    private String abnormalNote;
    private Integer status;
    private String inspector;
    /** 数据来源 MANUAL/DEVICE/API/IMPORT，前端据此区分人工录入与设备自动采集 */
    private String source;
    /** 来源设备号/单据号，来源为 DEVICE 时展示 */
    private String sourceRef;
    private String remark;
    private String fileIds; // 实际开发中这里通常是 List<String> 或 List<FileVO>
    private LocalDateTime createTime;
}