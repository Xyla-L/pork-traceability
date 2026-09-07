package com.pork.slaughter.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CarcassStampVO {

    private Long id;

    private Long pigId;

    private String stampNo;

    private LocalDateTime stampTime;

    private String stampPosition;

    private String veterinary;

    private String eSignature;

    private LocalDateTime createTime;
}