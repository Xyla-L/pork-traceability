package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发货计划明细：这一车要装的分割批次清单。
 * <p>
 * {@code batch_no / part_code / product_name / weight_kg} 都是制单时的快照（冗余），
 * 目的是让计划单本身可读 —— 三年后分割批次表变了，这份计划单仍能说清当时打算发什么。
 * 唯一键 {@code uk_plan_split} 保证同一计划里同一批次只出现一次。
 *
 * <h3>已排车标记与「分车」</h3>
 * 一台车装不完一张计划是常态，装不下就把剩下的留到下一车，而不是取消计划重开。
 * 所以明细带 {@code shipped} 标记：已经排进车的那部分留在计划里作为台账，没排上的继续等车。
 * {@code transport_id} 记的是「这张明细实际搭了哪一趟车」——分车时同一计划的明细会指向不同运单，
 * 所以它不能只放在计划主表上。删运单时这些明细会被退回「待排车」。
 */
@Data
@TableName("delivery_plan_item")
public class DeliveryPlanItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long splitBatchId;
    /** 分割批次号（制单时快照） */
    private String batchNo;
    private String partCode;
    private String productName;
    private BigDecimal weightKg;
    /** 是否已排车：0=待排车 1=已排进某张运单 */
    private Integer shipped;
    /** 实际发运这张明细的运单 id（分车时同一计划的不同明细指向不同运单） */
    private Long transportId;
    private LocalDateTime shippedTime;
    private LocalDateTime createTime;
}
