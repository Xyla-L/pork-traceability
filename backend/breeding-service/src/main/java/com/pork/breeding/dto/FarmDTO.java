package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "养殖场DTO")
public class FarmDTO {
    @Schema(description = "主键ID", example = "1")
    private Long id;

    @NotBlank(message = "养殖场名称不能为空")
    @Schema(description = "养殖场名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String farmName;

    @NotBlank(message = "养殖许可证编号不能为空")
    @Schema(description = "养殖许可证编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String licenseNo;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系电话")
    private String contactPhone;

    @NotNull(message = "养殖规模不能为空")
    @Schema(description = "养殖规模（存栏头数）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer scale;
}