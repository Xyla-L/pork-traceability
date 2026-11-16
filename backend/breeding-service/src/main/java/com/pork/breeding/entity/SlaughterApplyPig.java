package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出栏申报关联生猪：一张申报单含多头。
 * <p>
 * 原来 {@code slaughter_apply} 有 uk_pig——一头猪一生只能有一条申报，等于把
 * 「一车猪一次申报」拆成了一头一单。单据化后申报号唯一在单据上，成员在这里，
 * 逐头体重也挂在这里（地磅称重是逐头来的，单据上的总重是它们的和）。
 */
@Data
@TableName("slaughter_apply_pig")
public class SlaughterApplyPig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applyId;
    private Long pigId;
    private String earTagNo;
    /** 该头体重（地磅称重来自设备，人工申报可空） */
    private BigDecimal weightKg;
    private LocalDateTime createTime;
}
