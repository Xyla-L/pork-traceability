package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 生猪个体 DTO
 * 用于接收前端请求参数（新增/修改）
 */
@Data
public class PigIndividualDTO {

    @Schema(description = "主键ID（修改时必填）")
    private Long id;

    @Schema(description = "耳标号", requiredMode = Schema.RequiredMode.REQUIRED, example = "ET20240701001")
    @NotBlank(message = "耳标号不能为空")
    private String earTagNo;

    @Schema(description = "所属养殖场ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "101")
    @NotNull(message = "养殖场ID不能为空")
    private Long farmId;

    @Schema(description = "品种", example = "长白猪")
    private String breed;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "圈舍编号", example = "A-01")
    private String penNo;

    @Schema(description = "来源（自繁/外购-供应商名）", example = "自繁")
    private String source;
}