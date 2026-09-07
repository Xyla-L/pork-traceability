package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "疫苗名称")
    private String vaccineName;

    @Schema(description = "生产批次号")
    private String batchNo;

    @Schema(description = "注射时间")
    private LocalDateTime injectTime;

    @Schema(description = "操作人")
    private String operator;

    @Schema(description = "附件文件ID列表")
    private List<String> fileIds;
}