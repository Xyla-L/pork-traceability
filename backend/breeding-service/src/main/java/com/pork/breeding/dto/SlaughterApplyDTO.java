package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "出栏申报DTO")
public class SlaughterApplyDTO {
    @Schema(description = "申报ID")
    private Long id;

    @NotNull(message = "生猪ID不能为空")
    @Schema(description = "生猪ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long pigId;

    @Schema(description = "出栏体重")
    private BigDecimal weightKg;

    @Schema(description = "目标屠宰场")
    private String targetSlaughterhouse;
}