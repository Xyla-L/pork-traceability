package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分割作业：<b>一头白条的整份产出清单</b>。
 *
 * <h3>为什么要有这一层</h3>
 * 分割在行业里是固定部位切割（二分体 → 六分体 → 一号到五号肉等标准部位），出肉率也是刚性的。
 * 但原来的 {@code ingestSplit} 一次上报只建一条 {@link SplitBatch}，一头猪切 8 个部位就要报 8 次、
 * 建 8 条互不关联的记录，于是「这头猪切了什么」根本查不出来，也没有任何对账。
 * 这一层把「一次分割作业」显式建模：作业是主，部位是明细，出肉率对账挂在作业上。
 *
 * <h3>对账字段怎么来的</h3>
 * 分母 {@link #parentWeightKg} 有两个来源，记在 {@link #weightBasis} 里：
 * 报文带耳标时从 {@code carcass_batch_pig} 取该头的精确过磅值（{@code PIG}），
 * 否则取胴体批次的单头均重（{@code BATCH_AVG}），都拿不到则不做对账（{@code NONE}）。
 * 结论落在 {@link #checkStatus} / {@link #checkNote}，判定逻辑在 {@code SplitYieldCheck}。
 *
 * <h3>数据留证优先</h3>
 * 对账不通过（缺必出部位、总重离谱）<b>不拒绝入账</b>，只置「待人工复核」——
 * 分割事实已经发生，不入账等于把现场数据弄丢了。这与瘦肉精阳性记录照常入账留证同一个口径。
 */
@Data
@TableName("split_job")
public class SplitJob {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作业号：SV + yyyyMMdd + '-' + 3 位当日流水，例 SV20261009-001 */
    private String jobNo;

    private Long parentBatchId;
    /** 冗余批次号，追溯查询免联表 */
    private String parentBatchNo;

    /** 被分割的那头生猪；报文带耳标时精确定位，否则为空（按批次均重对账） */
    private Long pigId;
    private String earTagNo;

    /** 分割操作员（人）。设备不得代签，缺人转人工 */
    private String operator;
    private String workshop;
    private String lineNo;
    private BigDecimal workshopTemp;

    private Integer itemCount;
    private BigDecimal totalWeightKg;
    private BigDecimal parentWeightKg;
    /** 基准来源：PIG=按耳标精确 / BATCH_AVG=批次单头均值 / NONE=无基准（不做对账） */
    private String weightBasis;
    private BigDecimal totalYieldRate;
    private BigDecimal leanYieldRate;

    /** 对账结论：0=通过 1=待人工复核 */
    private Integer checkStatus;
    private String checkNote;

    private LocalDateTime splitTime;
    private String source;
    private String sourceRef;
    private String rawPayload;
    private String contentHash;
    private LocalDateTime createTime;

    @TableLogic(value = "0", delval = "1")
    private Integer voidFlag;
}
