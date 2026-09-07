package com.pork.breeding.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生猪个体 VO
 * 用于返回给前端展示的数据
 */
@Data
public class PigIndividualVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "耳标号")
    private String earTagNo;

    @Schema(description = "所属养殖场ID")
    private Long farmId;

    @Schema(description = "养殖场名称（关联查询字段，非数据库字段）")
    private String farmName;

    @Schema(description = "品种")
    private String breed;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "圈舍编号")
    private String penNo;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "状态 (1-在养, 2-已出栏, 3-已屠宰, 4-异常死亡/淘汰)")
    private Integer status;

    @Schema(description = "状态描述（用于前端直接展示）")
    private String statusLabel;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}