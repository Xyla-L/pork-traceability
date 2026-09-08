package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "产地检疫证DTO")
public class QuarantineCertDTO {
    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "生猪ID不能为空")
    @Schema(description = "生猪ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long pigId;

    @NotBlank(message = "检疫证编号不能为空")
    @Schema(description = "检疫证编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String certNo;

    @NotBlank(message = "签发机构不能为空")
    @Schema(description = "签发机构")
    private String issueOrg;

    @NotNull(message = "签发时间不能为空")
    @Schema(description = "签发时间")
    private LocalDateTime issueTime;

    @Schema(description = "有效期至")
    private LocalDate validUntil;

    @Schema(description = "官方兽医")
    private String inspector;

    @NotBlank(message = "文件ID不能为空")
    @Schema(description = "扫描件文件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileId;

    @NotBlank(message = "CA数字签名不能为空")
    @Schema(description = "CA数字签名(Base64)")
    private String caSignature;
}
