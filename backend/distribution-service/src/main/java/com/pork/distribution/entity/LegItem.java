package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 取回/回运段货物清单：一张「取回单/回运单」装了哪些门店的拒收货。
 * <p>
 * 一张被拒收的站点（{@code transport_stop.status=3}）全程要搭两段车：
 * 取回段（门店 → 冷链中心）和回运段（冷链中心 → 无害化处理中心），所以唯一键是
 * {@code (source_stop_id, leg_kind)} 而不是 {@code source_stop_id} 单列 ——
 * 同一批货不允许在同一层里上两辆车，但允许在两层里各上一次。
 * <p>
 * 清单行是<b>快照</b>：门店名、原运单号、批次号、重量在排车那一刻定死，
 * 之后原站点怎么改都改不动它 —— 排车依据不允许被事后改写。
 */
@Data
@TableName("leg_item")
public class LegItem {

    public static final String KIND_RETURN = "RETURN";
    public static final String KIND_DISPOSAL = "DISPOSAL";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 回运/处置运单 id（cold_chain_transport） */
    private Long legTransportId;

    /** 段落 RETURN=取回段 / DISPOSAL=回运段 */
    private String legKind;

    /** 原拒收站点 id（transport_stop） */
    private Long sourceStopId;

    /** 原运单号（快照） */
    private String transportNo;

    /** 拒收门店 id（快照） */
    private Long storeId;

    /** 拒收门店名（快照） */
    private String storeName;

    /** 分割批次号（快照，可能为空） */
    private String batchNo;

    /** 拒收站点货重 kg（快照） */
    private BigDecimal weightKg;

    /** 装车扫码状态 0=未扫码确认 1=已扫码核对（回运装车扫码枪现场扫到了这一行货） */
    private Integer scanStatus;

    /** 装车扫码时刻（设备现场时刻，不是服务器时间） */
    private LocalDateTime scanTime;

    /** 扫码设备编号（如 RETURN-SCAN-001） */
    private String scanDeviceNo;

    private LocalDateTime createTime;
}
