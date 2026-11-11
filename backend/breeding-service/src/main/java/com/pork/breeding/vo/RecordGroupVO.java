package com.pork.breeding.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 「按批次分组」的列表行。
 * <p>
 * 为什么需要它：免疫、产地检疫、出栏申报在现实里都是<b>整批一次做完</b>的动作
 * （一次免疫全批、一张检疫证覆盖一批、一份申报单含多头），但底层事实表是逐头存的
 * （一条记录挂一头猪）。直接列表就变成「同一件事刷 N 行耳标号」，看不出这是一次操作。
 * <p>
 * 这里做的是<b>纯展示层聚合</b>：不新建单据表、不改判重与状态机，只在查询时按
 * 「批次 + 动作签名 + 发生日期」归组，把一次操作收成一行；明细逐头保留在 {@code members}，
 * 前端展开即见。这样少刷屏，同时不丢「哪一头」的追溯能力。
 */
@Data
@Schema(description = "按批次分组的记录行")
public class RecordGroupVO {

    @Schema(description = "分组键（前端用作列表 key，不用于业务判断）")
    private String groupKey;

    @Schema(description = "记录类型：VACCINE / CERT / APPLY")
    private String kind;

    @Schema(description = "批次ID；null 表示这些猪尚未归入任何批次")
    private Long batchId;

    @Schema(description = "批次号；null 表示未归批")
    private String batchNo;

    @Schema(description = "品种")
    private String breed;

    @Schema(description = "养殖场ID")
    private Long farmId;

    @Schema(description = "养殖场名称")
    private String farmName;

    @Schema(description = "主行标题：疫苗名称 / 检疫证号摘要 / 申报编号摘要")
    private String title;

    @Schema(description = "主行副标题：疫苗批号 / 出证机构 / 目标屠宰场")
    private String subTitle;

    @Schema(description = "发生日期（yyyy-MM-dd）")
    private String happenedOn;

    @Schema(description = "操作人；组内多人时显示「XX 等N人」")
    private String operator;

    @Schema(description = "组内头数")
    private Integer headCount;

    @Schema(description = "组内已作废条数")
    private Integer voidCount;

    @Schema(description = "耳标区间起（组内最小耳标）")
    private String tagStart;

    @Schema(description = "耳标区间止（组内最大耳标）")
    private String tagEnd;

    @Schema(description = "状态摘要（一眼可读的结论），如「全部有效」「部分有效」「全部作废」")
    private String statusSummary;

    @Schema(description = "状态明细（挂 tooltip 用），如「有效 28 / 已作废 2」；无补充时为 null")
    private String statusDetail;

    @Schema(description = "逐头明细（原始记录行，字段与改造前列表一致）")
    private List<Map<String, Object>> members;
}
