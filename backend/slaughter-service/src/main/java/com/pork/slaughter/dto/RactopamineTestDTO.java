package com.pork.slaughter.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RactopamineTestDTO {

    private Long pigId;

    private LocalDateTime testTime;

    private String testMethod;

    private String testTarget;

    private String samplePart;

    private Integer result;

    private String detectionLimit;

    private String operator;

    private String fileIds;

    // --- 分页参数 ---
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}