package com.pork.slaughter.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 屠宰端「按批次折叠」的列表行。
 * <p>
 * 与养殖端的 {@code RecordGroupVO} 是同一个思路、不同的口径：养殖端聚合的是「一次动作」
 * （一次免疫、一张证、一份申报），屠宰端聚合的是<b>一个批次</b>——因为屠宰是一条流水线，
 * 一批猪陆续进场、陆续宰、陆续盖章，跨天是常态，没有「动作签名」可归。
 * <p>
 * 所以这里只做<b>浏览层的折叠</b>：主行给批次的汇总与结论，展开仍是一头一行。
 * 逐头的检验结论、盖章凭据、作废入口一个不少——聚合只换读法，不换事实。
 */
@Data
@Schema(description = "屠宰记录按批次折叠的行")
public class SlaughterGroupVO {

    @Schema(description = "分组键（前端列表 key，不用于业务判断）")
    private String groupKey;

    @Schema(description = "记录类型：ENTRY / INSPECT / STAMP / RACTO")
    private String kind;

    @Schema(description = "批次号；null 表示该头猪没有批次号（按头独立成行，不与其它猪并）")
    private String batchNo;

    @Schema(description = "未归批时的单头 pigId；归批时为 null")
    private Long pigId;

    @Schema(description = "耳标区间起（组内最小耳标）")
    private String tagStart;

    @Schema(description = "耳标区间止（组内最大耳标）")
    private String tagEnd;

    @Schema(description = "组内记录条数（= 该批涉及的头数）")
    private Integer headCount;

    @Schema(description = "组内最早发生时间（yyyy-MM-dd HH:mm）")
    private String firstTime;

    @Schema(description = "组内最晚发生时间（yyyy-MM-dd HH:mm）")
    private String lastTime;

    @Schema(description = "状态结论，如「全部合格」「含不合格」「部分已盖章」")
    private String statusSummary;

    @Schema(description = "状态明细（挂 tooltip），如「合格 28 / 不合格 1」")
    private String statusDetail;

    @Schema(description = "逐头明细（原始列表行，字段与折叠前一致）")
    private List<Object> members;
}
