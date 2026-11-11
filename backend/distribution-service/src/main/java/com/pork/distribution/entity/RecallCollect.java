package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 召回待收运池：门店货架上的召回货，一件（召回单 × 门店 × 批次）一行。
 * <p>
 * 与拒收货的区别只有一件事：<b>货在哪</b>。拒收的货已经在冷链中心，回运段不用排车；
 * 召回的货还在门店，冷链中心得派车逐店上门收 —— 所以这张表是「处理计划」的第二来源，
 * 出来的是收运运单（RECALL）的装载清单，不是回运单（DISPOSAL）的。
 * <p>
 * 状态即去向：0 待收运（在池里）→ 1 已排车（已并入某张收运运单）→ 2 已收上车（司机到店扫过码）
 * → 3 已送无害化（无害化处理中心确认签收，链条终结）。池子里只认 0。
 */
@Data
@TableName("recall_collect")
public class RecallCollect {

    /** 待收运：还在处理计划池里，没排上任何收运运单 */
    public static final int ST_PENDING = 0;
    /** 已排车：已并入收运运单，司机还没到店 */
    public static final int ST_PLANNED = 1;
    /** 已收上车：司机到店扫码收货完成 */
    public static final int ST_COLLECTED = 2;
    /** 已送无害化：链条终结 */
    public static final int ST_DISPOSED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 召回单 id（db_sales.recall_order） */
    private Long recallId;

    /** 召回编号 RC… */
    private String recallNo;

    /** 召回原因（快照） */
    private String reason;

    /** 货所在门店机构 id */
    private Long storeId;

    private String storeName;

    private Long splitBatchId;

    /** 分割批次号（司机到店扫的就是它） */
    private String batchNo;

    private BigDecimal weightKg;

    /** 涉及销售记录条数 */
    private Integer saleCount;

    /** 0=待收运 1=已排车 2=已收上车 3=已送无害化 */
    private Integer status;

    /** 并入的收运运单 */
    private Long transportId;

    /** 该门店在这趟运单上的站点 */
    private Long stopId;

    /** 司机到店扫码收货时刻 */
    private LocalDateTime collectTime;

    private LocalDateTime createTime;
}
