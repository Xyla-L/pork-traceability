package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 冷链运单：一台车、一趟货、一次责任转移。
 * <p>
 * {@code splitBatchId} 是「主批次」（装载明细的第一条），仅为兼容存量数据与既有查询保留；
 * 一车多批的装载全貌一律以 {@code transport_item} 为准。
 * <p>
 * 来源三件套用于分辨这张运单是怎么来的：{@code MANUAL}=人工建单、{@code PLAN}=计划一键生成、
 * {@code DEVICE}=装车口识别终端自动生成。出了发错店的事故，第一件事就是查它从哪条路径产生。
 * <p>
 * {@link #driverSource} / {@link #driverConfirmed} 记录的是<b>司机这个人是怎么确认的</b>，
 * 与上面「运单怎么来的」是两件事、要分两处看（口径见
 * {@code com.pork.distribution.support.DriverSource}）：刷卡与人脸是本人确认，
 * 车牌反查只是推定，人工录入是调度代填。追责时能不能站住，取决于这两个字段，而不是人名非空。
 *
 * <h3>「运单 = 一个车次」（2026-10-10 改造）</h3>
 * 现实里一辆冷藏车装满后顺路送三四家门店。所以运单粒度从「一次送货」上抬到「一个车次」，
 * 但<b>刻意不另建车次表</b>：本实体本身就是车次，站点清单看
 * {@code transport_item} 按 {@code store_id} 聚合。
 * <ul>
 *   <li>{@link #destination} <b>不改列、语义降级</b>为「首站 / 主目的地」：
 *       单站时就是门店名（存量 34 条即此），多站时展示成「途经 N 站」；</li>
 *   <li>{@link #stopCount} 是途经站点数快照，列表展示免聚合；
 *       存量运单默认 1（单店直送，语义不变、无需迁移）；</li>
 *   <li>签收不再是一运单一条：{@code store_receipt} 的唯一键已放开为
 *       {@code uk_transport_store(transport_id, store_id)} —— <b>一站一条</b>。</li>
 * </ul>
 * 一车多店是<b>扫出来的、不是规划出来的</b>：每批货的门店归属由它所属的发货计划定死，
 * 装车口扫码时按批次倒查即得站点分组，设备从不需要知道目的地。
 */
@Data
@TableName("cold_chain_transport")
public class ColdChainTransport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transportNo;
    private Long splitBatchId;
    private String vehicleNo;
    private String vehicleType;
    private String refrigeration;
    private String driverName;
    private String driverPhone;
    /**
     * 司机是怎么来的 CARD=刷卡 / FACE=人脸 / PLATE_INFERRED=车牌反查推定 / MANUAL=人工录入。
     * <p>
     * <b>必须 ALWAYS 写：</b>MyBatis-Plus 默认跳过 null 字段，而本表这一列的默认值是
     * {@code 'MANUAL'} —— 司机留空时若不显式写 NULL，数据库会替我们「编」出一个来源，
     * 落成「有来源、没名字」的自相矛盾记录（实际踩过一次）。同理，清空司机必须走
     * {@code lambdaUpdate().set(..., null)}，别指望 {@code updateById}。
     */
    @TableField(insertStrategy = FieldStrategy.ALWAYS)
    private String driverSource;
    /** 是否本人确认 1=刷卡或人脸本人确认 0=系统推定或人工录入 */
    private Integer driverConfirmed;
    private String origin;
    /** 首站 / 主目的地（单站=门店名；多站时展示成「途经 N 站」，明细看 transport_item） */
    private String destination;
    private LocalDateTime plannedDepart;
    private LocalDateTime plannedArrive;
    private LocalDateTime departTime;
    private LocalDateTime arriveTime;
    private Integer status;
    /** 途经站点数：1=单店直送（存量默认），>1=一车多店 */
    private Integer stopCount;
    /**
     * 已到达站点数 / 已签收站点数：<b>不是表里的列</b>，只在列表与详情展示时按
     * {@code transport_stop} 批量聚合计入（一次分组查询，不做 N+1）。
     * <p>
     * 为什么要它在运单上：单站运单占绝大多数，「站点级到达」若只在多站时才有落点，
     * 使用者就完全看不出「到达是逐站记的」——所以列表的站点进度与详情信息条都读这两个值。
     */
    @TableField(exist = false)
    private Integer arrivedStopCount;
    /** 已签收站点数（口径同上，取 {@code transport_stop.status = 2} 的行数） */
    @TableField(exist = false)
    private Integer receivedStopCount;
    /** 数据来源 MANUAL=人工建单 / PLAN=计划生成 / DEVICE=装车口自动生成 */
    private String source;
    /** 来源设备号（装车口自动生成记设备编号，如 LOADER-001；发车终端如 DEPART-001） */
    private String sourceRef;
    /** 设备原始报文（装车口上报的完整报文，供逐字段核对） */
    private String rawPayload;
    /**
     * 本次操作是「把货追加进一张还没发车的运单」而不是新建（<b>不是表里的列</b>）。
     * <p>
     * 只用来让装车口的回执说清去向：同一台车继续装货时，回执要写「并入运单 X」而不是
     * 「生成运单 X」—— 使用者一眼能看出这趟车已经装过一批了，不必去列表里比对。
     */
    @TableField(exist = false)
    private Boolean mergedIntoExisting;
    private LocalDateTime createTime;
}
