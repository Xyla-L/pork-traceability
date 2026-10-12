package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运单装载明细：一张运单实际装运的分割批次 —— 同时也是<b>站点明细</b>。
 *
 * <h3>为什么要这张表</h3>
 * {@code cold_chain_transport} 只有单值 {@code split_batch_id}，一张运单只能表达一个分割批次。
 * 但现实里一车必然装多批（同一屠宰场、同一屠宰日的多个分割产出），
 * 单值字段既装不下一车多批，也让「哪些产出还没发运」这个查询无从下手。
 * <p>
 * 保留 {@code cold_chain_transport.split_batch_id} 作为「主批次」（第一条明细），
 * 是为了向后兼容存量 34 条运单与现有查询；装载全貌以本表为准。
 *
 * <h3>一车多目的地（2026-10-10 改造）</h3>
 * 一辆冷藏车装满后顺路送三四家门店是常态。运单粒度因此从「一次送货」上抬到「一个车次」，
 * 而<b>站点清单不另建表</b>：按 {@link #storeId} 聚合本表就是站点列表
 * （{@code SELECT DISTINCT seq, store_id, store_name WHERE transport_id=?}）。
 * <ul>
 *   <li>{@link #seq}：站点序号，按装车/扫码顺序。**按站装车**时扫码顺序天然就是站点顺序；
 *       混装则留 NULL，站点顺序改由签收时间回填 —— 那也是事实；</li>
 *   <li>{@link #storeId}/{@link #storeName}：这批货<b>在哪一站卸下</b>。
 *       以前只能绕道 {@code delivery_plan_item.plan_id → delivery_plan.store_id} 反查，
 *       货的门店归属不是装载记录自己的属性；多站点时那条绕路就绕不回来了。</li>
 * </ul>
 */
@Data
@TableName("transport_item")
public class TransportItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transportId;
    private Long splitBatchId;
    /** 分割批次号（装车时快照） */
    private String batchNo;
    private BigDecimal weightKg;
    /** 卸货门店 id（多站车次：这批货在哪一站卸下）；存量为 NULL 表示单店直送 */
    private Long storeId;
    /** 卸货门店名（冗余，展示免联表） */
    private String storeName;
    /** 站点序号（按装车/扫码顺序，从 1 起）；NULL = 混装未按站，顺序由签收时间回填 */
    private Integer seq;
    private LocalDateTime createTime;
}
