package com.pork.breeding.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "出栏申报VO")
public class SlaughterApplyVO {
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "申报编号")
    private String applyNo;

    @Schema(description = "生猪ID")
    private Long pigId;

    @Schema(description = "耳标号")
    private String earTagNo;

    @Schema(description = "出栏体重(kg)")
    private BigDecimal weightKg;

    @Schema(description = "目标屠宰场")
    private String targetSlaughterhouse;

    @Schema(description = "申报时间")
    private LocalDateTime applyTime;

    @Schema(description = "审批状态：0-待审, 1-通过, 2-驳回")
    private Integer approvalStatus;

    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;

    @Schema(description = "审批人")
    private String approver;

    @Schema(description = "驳回原因")
    private String rejectReason;
}