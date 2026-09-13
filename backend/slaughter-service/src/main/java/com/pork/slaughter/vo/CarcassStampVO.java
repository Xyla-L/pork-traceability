package com.pork.slaughter.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CarcassStampVO {

    private Long id;

    private Long pigId;

    private String batchNo;

    private String carcassNo;

    private String stampNo;

    private String stampType;

    private LocalDateTime stampTime;

    private String stampPosition;

    private String veterinary;

    private Integer status;

    private String contentHash;

    private String eSignature;

    private LocalDateTime createTime;
}