package com.pork.slaughter.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SlaughterInspectionDTO {

    private Long pigId;

    private Integer inspectType;

    private LocalDateTime inspectTime;

    private String organCheck;

    private Integer result;

    private String issueDesc;

    private String disposal;

    private String veterinary;

    private String licenseNo;

    private String eSignature;

    private String fileIds;

    // --- 分页参数 ---
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}