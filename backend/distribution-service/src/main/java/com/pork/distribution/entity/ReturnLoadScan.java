package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拒收回运装车扫码事实：装车位那把专用扫码枪扫到的「这台车上了哪件拒收货」。
 * <p>
 * 为什么它必须独立于 {@link LegItem}：扫码发生在<b>生成处理运单之前</b>（对齐冷链装车口
 * 的「车牌 + 货码 → 倒查」），而 leg_item 的 leg_transport_id 非空 —— 单还没生成，
 * 清单行就无处可挂。所以现场事实先落本表，调度再拿它去生成计划。
 * <p>
 * 本表只记事实，不做判定：货码对不上任何拒收站点照样留原文（status=2），
 * 一码多站也不猜（status=2）；能不能装车由生成计划时按载重与车辆占用判定。
 */
@Data
@TableName("return_load_scan")
public class ReturnLoadScan {

    /** 已扫、尚未并入任何处理运单：生成计划时可用 */
    public static final int ST_PENDING = 0;
    /** 已并入处理运单 */
    public static final int ST_PLANNED = 1;
    /** 异常待人工：货码对不上、一码多站、已并入别的单 */
    public static final int ST_ABNORMAL = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 扫到的货码：拒收包装上的原运单号或分割批次号 */
    private String scanCode;

    /** 反查命中的拒收站点（transport_stop）；NULL = 没对上任何拒收站点 */
    private Long stopId;

    /** 命中的原运单号（快照） */
    private String transportNo;

    /** 命中的分割批次号（快照） */
    private String batchNo;

    /** 拒收门店（快照） */
    private String storeName;

    /** 该站拒收货重（快照） */
    private BigDecimal weightKg;

    /** 扫这一枪时识别到的车牌：货装的是哪台车 */
    private String plateNo;

    /** 扫码设备编号（如 RETURN-SCAN-001） */
    private String deviceNo;

    /** 设备现场扫码时刻 */
    private LocalDateTime scanTime;

    /** 0=已扫待排车 1=已并入处理运单 2=异常待人工 */
    private Integer status;

    /** 并入的处理运单 id */
    private Long legTransportId;

    /** 异常说明 */
    private String note;

    private LocalDateTime createTime;
}
