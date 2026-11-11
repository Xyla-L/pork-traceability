package com.pork.breeding.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生猪批次档案 VO，用于前端展示。
 */
@Data
public class PigBatchVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "批次号")
    private String batchNo;

    @Schema(description = "所属养殖场ID")
    private Long farmId;

    @Schema(description = "养殖场名称（关联查询字段）")
    private String farmName;

    @Schema(description = "品种")
    private String breed;

    @Schema(description = "进场日期")
    private LocalDate entryDate;

    @Schema(description = "批次出生日期（外部养殖系统提供；日龄前端现算，不存日龄）")
    private LocalDate birthDate;

    @Schema(description = "批次成员耳标起号")
    private String earTagStart;

    @Schema(description = "批次成员耳标止号")
    private String earTagEnd;

    @Schema(description = "批次数量（= 耳标区间大小）")
    private Integer quantity;

    @Schema(description = "来源：自繁/外购-供应商名")
    private String source;

    @Schema(description = "外部系统标识（NULL=本系统人工登记）")
    private String externalSystem;

    @Schema(description = "外部系统内的批次主键")
    private String externalId;

    @Schema(description = "数据来源 MANUAL=人工登记 / API=外部养殖系统同步")
    private String dataSource;

    @Schema(description = "登记时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    // ==================== 成员状态汇总（按成员行实时统计，前端直接展示中文） ====================

    @Schema(description = "在养数量")
    private Long onFarmCount;

    @Schema(description = "已出栏数量")
    private Long shippedCount;

    @Schema(description = "已屠宰数量")
    private Long slaughteredCount;

    @Schema(description = "异常数量")
    private Long abnormalCount;

    // ==================== 作废标注 ====================

    @Schema(description = "作废理由（实名留档）")
    private String voidReason;

    @Schema(description = "作废操作人")
    private String voidOperator;

    @Schema(description = "作废时间")
    private LocalDateTime voidTime;
}
