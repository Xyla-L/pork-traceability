package com.pork.slaughter.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SlaughterInspectionVO {

    private Long id;

    private Long pigId;

    private Integer inspectType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime inspectTime;

    private String organCheck;

    private Integer result;

    private String issueDesc;

    private String disposal;

    private String veterinary;

    private String licenseNo;

    private String eSignature;

    private String fileIds;

    private String contentHash;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}