package com.pork.slaughter.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RactopamineTestVO {

    private Long id;

    private Long pigId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime testTime;

    private String testMethod;

    private String testTarget;

    private String samplePart;

    private Integer result;

    private String detectionLimit;

    private String operator;

    private String fileIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}