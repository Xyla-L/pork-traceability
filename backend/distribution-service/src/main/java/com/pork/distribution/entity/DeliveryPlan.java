package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发货计划：冷链运单的上游决策单。
 *
 * <h3>为什么要有这一层</h3>
 * 全系统每个环节都有一台现场设备作为事实来源，唯独运单是凭空手填的 ——
 * 车牌、司机、目的地、时间这些字段在系统里查无出处。但运单和分割的自动化性质不同：
 * 分割是「事实已经发生，只是没记下来」，让设备记就行；运单是「<b>决策还没做</b>」，
 * 这车拉哪批货、发去哪家店，信息压根不存在，设备也无从采集。
 * <p>
 * 所以第一步不是装硬件，而是把决策结构化：一张计划 = 一个目的地 + 一个车次 + N 个待发分割批次，
 * 运单由它一键生成，目的地/计划时间/车辆信息全部带出，人不再重复录。
 * 附带补上一个原先的盲区：系统此前无法回答「哪些分割产出还没发出去」。
 *
 * <h3>状态</h3>
 * {@code 1=待发货 2=已排车 3=已取消 4=部分排车}。取消是置状态而非删除 ——
 * 「发过什么计划」本身是管理轨迹，删掉就查不到了。
 * 4=部分排车 是「分车」的产物：一车装不完，剩下的还在等车，这张计划既不是待发货、也不是全排完了。
 * <p>
 * <b>2 为什么叫「已排车」而不是「已发运」</b>：这一列只回答「货有没有排进运单」，
 * 不回答「车走到哪了」—— 排完车那一刻运单侧恰恰还停在「待发车」（司机上车刷卡才算发车）。
 * 两条时间线各有各的词：计划侧「待发货 / 部分排车 / 已排车」，运单侧
 * 「待发车 / 运输中 / 待签收 / 已签收」。
 */
@Data
@TableName("delivery_plan")
public class DeliveryPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 计划号 DP+yyyyMMdd+3位当日流水；当日全局排流水，含已取消的不复用 */
    private String planNo;
    private LocalDate planDate;
    /** 目的地门店（取自机构树 type=retail） */
    private Long storeId;
    private String storeName;
    /** 车牌号：计划阶段可空（车还没定），生成运单时必填 —— 这是自动化的分界点 */
    private String vehicleNo;
    private String vehicleType;
    private String refrigeration;
    /** 司机姓名：计划阶段可空，生成运单时必填。责任人不能由设备代填 */
    private String driverName;
    private String driverPhone;
    private String origin;
    private LocalDateTime plannedDepart;
    private LocalDateTime plannedArrive;
    private Integer status;
    /**
     * 计划来源（口径见 {@code support.PlanSource}）：MANUAL=内部调度 / ORDER=订单转入。
     * 订单只是计划的**来源之一**，不是计划的替代 —— 运单的上游永远是计划。
     * 补货/调拨/铺货没有订单，所以 MANUAL 是默认值、且永久保留。
     */
    private String planSource;
    /**
     * 来源订单号。订单的权威副本在外部系统（ERP/OMS），本系统只留号以便回溯，
     * 不复制订单内容 —— 存一份就是第二个真相。原始报文留在 ingest_staging.payload。
     */
    private String orderNo;
    /** 明细条数快照 = 要发运的分割批次数 */
    private Integer itemCount;
    private BigDecimal totalWeightKg;
    /**
     * 最近一次生成的运单。分车后一张计划可能对应多张运单，单值列表达不了全貌，
     * 于是它降级为「最近一次」——哪张明细搭了哪趟车，一律以 delivery_plan_item.transport_id 为准。
     */
    private Long transportId;
    private String transportNo;
    /** 已生成的运单张数（分车后 &gt;1） */
    private Integer transportCount;
    private LocalDateTime generatedTime;
    private String remark;
    /** 制单人（实名） */
    private String operator;
    private LocalDateTime createTime;
}
