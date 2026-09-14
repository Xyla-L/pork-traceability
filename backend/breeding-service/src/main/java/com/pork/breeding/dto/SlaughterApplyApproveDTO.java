package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "出栏申报审批DTO")
public class SlaughterApplyApproveDTO {
    @NotNull(message = "审批结果不能为空")
    @Schema(description = "审批结果：true-通过, false-驳回")
    private Boolean approved;

    @Schema(description = "审批意见（驳回时作为驳回原因）")
    private String comment;
}
