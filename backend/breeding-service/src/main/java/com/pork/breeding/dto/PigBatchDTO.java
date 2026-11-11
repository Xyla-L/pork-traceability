package com.pork.breeding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 生猪批次登记 DTO。
 *
 * <p>批次档案是养殖端的登记单位：除耳标号（区间）外，其余信息由人工登记。
 * 耳标起止号之间（含两端）的每一头猪登记时自动成为批次成员，
 * 不存在逐头录入个体信息的入口——品种/来源是批次共有的，个体差异现实中本就不建档。
 */
@Data
public class PigBatchDTO {

    @Schema(description = "批次号（同一养殖场内唯一）")
    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    @Schema(description = "所属养殖场ID")
    @NotNull(message = "养殖场不能为空")
    private Long farmId;

    @Schema(description = "品种")
    private String breed;

    @Schema(description = "进场日期")
    private LocalDate entryDate;

    @Schema(description = "批次成员耳标起号")
    @NotBlank(message = "耳标起号不能为空")
    private String earTagStart;

    @Schema(description = "批次成员耳标止号")
    @NotBlank(message = "耳标止号不能为空")
    private String earTagEnd;

    @Schema(description = "来源：自繁/外购-供应商名")
    private String source;
}
