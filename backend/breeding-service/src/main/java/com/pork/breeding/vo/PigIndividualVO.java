package com.pork.breeding.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    @Schema(description = "性别: 1公 2母")
    private Integer gender;

    @Schema(description = "圈舍编号")
    private String penNo;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "数据来源：MANUAL=人工录入 DEVICE=设备自动采集（区别于业务来源 source）")
    private String dataSource;

    @Schema(description = "来源设备号/单据号")
    private String sourceRef;

    @Schema(description = "状态 (1-在养, 2-已出栏, 3-已屠宰, 4-异常死亡/淘汰)")
    private Integer status;

    @Schema(description = "状态描述（用于前端直接展示）")
    private String statusLabel;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    // ==================== 作废标注（仅追溯侧使用）====================
    // 常规列表/详情只返回有效档案；但追溯链上「这条档案被作废了」必须说得出来，
    // 否则记录凭空消失，看起来跟从没录过一样。

    @Schema(description = "该档案是否已被作废（true 时其余业务字段可能为空）")
    private Boolean voided;

    @Schema(description = "作废理由（实名留档）")
    private String voidReason;

    @Schema(description = "作废操作人")
    private String voidOperator;

    @Schema(description = "作废时间")
    private LocalDateTime voidTime;

    @Schema(description = "关联的已作废记录（免疫、检疫证等），用于追溯侧标注")
    private List<Map<String, Object>> voidedRecords;
}