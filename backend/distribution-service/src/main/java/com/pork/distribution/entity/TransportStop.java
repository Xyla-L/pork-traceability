package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车次站点行程台账：一车多目的地时，<b>一站一条</b>，记这一站走到哪了。
 *
 * <h3>为什么站点状态要单独一张表</h3>
 * 站点清单本身不另建表 —— 按 {@code transport_item.store_id/seq} 聚合就是站点列表。
 * 但 {@code transport_item} 是<b>装载明细</b>，它装不下「这一站到了没、什么时候到的」：
 * <ul>
 *   <li>{@code cold_chain_transport} 只有一个 {@code arrive_time}，一趟车途经 3 站时
 *       它只能表达「到了第一个站」，中间两站的到没到没有落点；</li>
 *   <li>{@code store_receipt} 是站点的<b>终态凭证</b>（签收是货权转移，要落签收人、照片），
 *       只在签收那一刻才建行 —— 「到了但还在排队卸货」无处安放；</li>
 *   <li>签收的前置校验也必须下沉到站，否则拦不住「车还在第 1 站、第 3 站就签收了」。</li>
 * </ul>
 * 所以站点需要自己的生命周期：<b>未到达 → 已到达 → 已签收 / 已拒收</b>。
 *
 * <h3>三条口径</h3>
 * <ol>
 *   <li><b>到达的粒度是「卸货门店」，不是「批次」</b>：同店多批只到一次，多站车次一站一次。
 *       判据是卸货门店数，不是批数 —— 「到了没」问的是车在不在这个点，与车厢装了几批无关。</li>
 *   <li>{@link #stopNo} 与 {@code transport_item.seq} <b>完全同源</b>（装车扫码顺序 = 卸货顺序），
 *       不另算一套；唯一键 {@code (transport_id, stop_no)}。</li>
 *   <li>运单整体状态<b>由本表汇总推导</b>（全部到达 = 待签收，全部终结 = 已签收），
 *       不在运单主表另存一个会和站点打架的布尔值。</li>
 * </ol>
 *
 * <h3>拒收是终态，不是删除</h3>
 * 冷链货物有异常时门店有权拒收——这是真实商业事实，给它正式的状态通道而不是删运单：
 * 拒收<b>只能由门店人工发起</b>（理由必填、实名、上链），设备与司机无权代拒；
 * 拒收站点对运单推导视同「已终结」（单店拒收不卡别的店签收，全部终结运单即闭环）；
 * 车辆放行口径因此天然放行。后续处置（退回供应商/销毁/折价接收）是另一次人工登记，
 * 协商成功也可以「拒收改签收」——每一次转折都留痕。
 */
@Data
@TableName("transport_stop")
public class TransportStop {

    /** 行程状态：未到达 */
    public static final int STOP_NOT_ARRIVED = 0;
    /** 行程状态：已到达（车到了这一站，货还没交接） */
    public static final int STOP_ARRIVED = 1;
    /** 行程状态：已签收（货权已转移到门店） */
    public static final int STOP_SIGNED = 2;
    /** 行程状态：已拒收（门店验货后拒绝接收；终态，处置与改签后续另行登记） */
    public static final int STOP_REJECTED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transportId;
    /** 站点序号（1 起；口径 = transport_item.seq，即装车扫码顺序 = 卸货顺序） */
    private Integer stopNo;
    /** 卸货门店 id（权威来源是机构树）；人工建单的老运单可能为空，此时按 storeName 定位 */
    private Long storeId;
    /** 卸货门店名（展示快照） */
    private String storeName;
    /** 本站批次数 */
    private Integer itemCount;
    /** 本站货物总重 kg */
    private BigDecimal weightKg;
    /** 0 未到达 / 1 已到达 / 2 已签收 / 3 已拒收 */
    private Integer status;
    /** 到达时刻（未到达为空） */
    private LocalDateTime arriveTime;
    /** 到达怎么确认的：DEVICE=车载终端刷卡设备确认 / MANUAL=人工确认（页面补确认） */
    private String arriveSource;
    /** 到达上报编号（设备号 / 操作留痕） */
    private String arriveSourceRef;
    /**
     * 到达确认人姓名：<b>司机到站刷卡换出来的人</b>（本人确认），人工确认到达时为空。
     * <p>
     * 与运单主表的 {@code driver_name} 不是一回事：那个是「谁在开这趟车」（发车那一下定的），
     * 这个是「谁把货送到了这一站」——一车多站时每一站都可以由不同的人确认，
     * 所以它必须落在站点上，不能挤在运单主表的一个字段里。
     */
    private String arriveDriverName;
    /** 到达确认来源：CARD=刷卡确认（本人）/ MANUAL=人工录入；人工确认到达时为空 */
    private String arriveDriverSource;
    /** 签收记录 id（store_receipt，仅已签收时有值；拒收改签收后回填） */
    private Long receiptId;
    /** 到达报文原文（设备通道才有，人工确认为空） */
    private String rawPayload;

    /* ==================== 拒收（站点终态之一，只能人工发起） ==================== */

    /** 拒收理由（必填，≥2 字，随拒收事件上链留档） */
    private String rejectReason;
    /** 拒收操作人（门店登录用户实名） */
    private String rejectBy;
    /** 拒收时刻 */
    private LocalDateTime rejectTime;
    /** 拒收来源：MANUAL=门店页面人工拒收（拒收不允许设备代劳） */
    private String rejectSource;
    /** 拒收操作留痕（操作人 id） */
    private String rejectSourceRef;
    /** 拒收后协商改签收标记：1 = 本站曾拒收、协商后已改签收（改签时间看 receipt） */
    private Integer signedAfterReject;

    /* ==================== 处置登记（拒收后货去哪，人工登记事实） ==================== */

    /** 处置方式：RETURN_SUPPLIER=退回供应商 / DESTROY=就地销毁 / DISCOUNT=折价接收 */
    private String disposalType;
    /** 处置说明（可空） */
    private String disposalNote;
    /** 处置经办人（登记时的登录用户实名） */
    private String disposalBy;
    /** 处置登记时刻 */
    private LocalDateTime disposalTime;
    private LocalDateTime createTime;
}
