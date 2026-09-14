package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "出栏申报DTO")
public class SlaughterApplyDTO {
    @Schema(description = "申报ID")
    private Long id;

    /** 由创建入口的路径变量 /pigs/{pigId}/apply 注入，请求体无需携带 */
    @Schema(description = "生猪ID（由路径变量注入，请求体可省略）")
    private Long pigId;

    @Schema(description = "出栏体重")
    private BigDecimal weightKg;

    @Schema(description = "目标屠宰场")
    private String targetSlaughterhouse;
}