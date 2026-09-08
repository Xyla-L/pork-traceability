package com.pork.slaughter.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SlaughterInspectionDTO {

    @NotNull(message = "生猪ID不能为空")
    private Long pigId;

    @NotBlank(message = "检验编号不能为空")
    private String inspectNo;

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    private String earTagNo;

    @NotNull(message = "检验类型不能为空")
    private Integer inspectType;

    @NotNull(message = "检验时间不能为空")
    private LocalDateTime inspectTime;

    private BigDecimal temperature;

    private String organCheck;

    private Integer result;

    private Integer status;

    private String conclusion;

    private String issueDesc;

    private String disposal;

    @NotBlank(message = "官方兽医不能为空")
    private String veterinary;

    private String licenseNo;

    private String eSignature;

    private String fileIds;

    // --- 分页参数 ---
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
