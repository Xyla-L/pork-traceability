package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "疫苗记录DTO")
public class VaccineRecordDTO {
    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "生猪ID不能为空")
    @Schema(description = "生猪ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long pigId;

    @NotBlank(message = "疫苗名称不能为空")
    @Schema(description = "疫苗名称")
    private String vaccineName;

    @NotBlank(message = "生产批次号不能为空")
    @Schema(description = "生产批次号")
    private String batchNo;

    @NotNull(message = "注射时间不能为空")
    @Schema(description = "注射时间")
    private LocalDateTime injectTime;

    @NotBlank(message = "操作人不能为空")
    @Schema(description = "操作人")
    private String operator;

    @Schema(description = "附件文件ID列表")
    private List<String> fileIds;
}
