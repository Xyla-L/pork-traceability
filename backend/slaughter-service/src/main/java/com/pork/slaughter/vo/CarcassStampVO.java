package com.pork.slaughter.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CarcassStampVO {

    private Long id;

    private Long pigId;

    private String batchNo;

    private String earTagNo;

    private String carcassNo;

    private String stampNo;

    private String stampType;

    private LocalDateTime stampTime;

    private String stampPosition;

    private String veterinary;

    private Integer status;

    private String contentHash;

    /** 数据来源：MANUAL=人工录入 DEVICE=设备自动采集（自动盖章机） */
    private String source;

    /** 来源设备号/单据号 */
    private String sourceRef;

    private String eSignature;

    private String remark;

    private LocalDateTime createTime;
}