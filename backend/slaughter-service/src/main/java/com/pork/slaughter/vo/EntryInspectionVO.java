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
    private String healthCheckLabel; // "通过" 或 "异常"
    private String certVerifiedLabel;
    private String abnormalNote;
    private Integer status;
    private String inspector;
    private String fileIds; // 实际开发中这里通常是 List<String> 或 List<FileVO>
    private LocalDateTime createTime;
}