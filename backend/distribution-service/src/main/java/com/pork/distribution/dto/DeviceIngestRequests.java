package com.pork.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备通道（温度采集 / 门店签收 / 分割批次 / 胴体批次归集）接入请求。
 * <p>
 * 独立于人工录入的 DTO：设备侧多带来源三件套（source/sourceRef/rawPayload），
 * 且签收必须由人签（receiver/eSignature）——设备可以自动读温度、自动读数量，
 * 但不能替人承担签收责任。
 */
public final class DeviceIngestRequests {

    private DeviceIngestRequests() {
    }

    /** 冷链温度采集：车载温控/冷库探头 */
    @Data
    public static class DeviceTemperature {
        /** 运输单号（设备侧只有单号，接入层透传后由本服务换成主键） */
        private String transportNo;

        private Long transportId;

        @NotNull(message = "温度值不能为空")
        @DecimalMin(value = "-60.0", message = "温度值超出物理合理区间")
        private BigDecimal temperature;

        /** 允许区间下限，缺省用服务默认值 */
        private BigDecimal tempRangeMin;
        private BigDecimal tempRangeMax;

        /** 设备侧采集时间（断网补传时可能是过去的时间） */
        private LocalDateTime recordTime;

        /**
         * 探头声明的额定上报间隔（秒），由接入层从设备台账 {@code ingest_device.sample_interval_seconds} 带过来。
         * <p>
         * 它是<b>断档判定</b>的基准：本条与上一条的间隔 &gt; 本值 × 容差 ⇒ 标 {@code is_gap}。
         * 「多久采一次」是探头的属性，不该由系统假设，所以由设备自己声明、随报文带上来。
         * 为空则用服务默认值。
         */
        private Integer sampleIntervalSeconds;

        /** 采集设备标识，落库到 recorder */
        private String recorder;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /** 门店签收：PDA 扫码 + 门店冷柜测温 */
    @Data
    public static class DeviceReceipt {
        private String transportNo;
        private Long transportId;

        private Long storeId;

        @NotBlank(message = "门店名称不能为空")
        private String storeName;

        /** 签收人（责任转移必须落具体人，缺失时转人工处理） */
        private String receiver;
        private String receiverPhone;

        private Integer qtyCheck;
        private String qtyDiffNote;
        private Integer tempCheck;
        private BigDecimal tempValue;
        private Integer packageIntact;
        /** 电子签名（图片地址或 base64），保留手写签收的法律意义 */
        private String signature;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 分割作业：分割线扫码称重台，扫白条钩标签后<b>一次上报一整头猪的产出清单</b>。
     * <p>
     * 这是「一次上报 = 一条部位记录」的修正版：分割是固定部位切割（二分体 → 六分体 →
     * 一号到五号肉等），一头猪本来就会切出多个部位，逐条报送等于把作业拆散了，
     * 既看不出「这头猪切了什么」，也没法做出肉率对账。
     * <p>
     * <b>部位编码与作业号都不由设备自由编造</b>：作业号由系统生成；
     * 部位必须落在 {@code meat_part} 字典里（只报名称时按字典反查），查不到转人工。
     * <p>
     * 兼容：{@code items} 为空时退化成单条模式，用顶层的 {@code partCode/productName/weightKg}，
     * 老设备报文与演示表单无需改造即可继续工作。
     */
    @Data
    public static class DeviceSplit {
        @NotBlank(message = "胴体批次号不能为空")
        private String parentBatchNo;

        /** 被分割那头白条的耳标号（挂钩标签）；带了才能精确对账，不带则按批次单头均重 */
        private String earTagNo;

        /** 生猪档案主键（接入层已用耳标在养殖服务换成主键，业务侧不再认耳标） */
        private Long pigId;

        /** 整作业产出清单；为空时用下面的单条字段 */
        private List<SplitItem> items;

        // ===== 单条兼容字段（items 为空时使用） =====
        /** 标准部位编码（优先） */
        private String partCode;
        /** 部位名称，缺 partCode 时按字典反查 */
        private String productName;
        /** 称重台读数（kg），必须大于 0 */
        private BigDecimal weightKg;
        private Integer packageCount;
        private String packageType;

        private String workshop;
        /** 分割产线/班次，可选 */
        private String lineNo;
        private BigDecimal workshopTemp;
        private String operator;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 装车口：车牌识别相机 + 扫码枪（1 号装车位上的两件套）。
     * <p>
     * 这两件东西是<b>一个点位、一个通道</b>，但不是<b>一台设备</b>：相机面朝车道、扫码枪在装卸位，
     * 安装位置与故障模式各不相同，所以报文里把「车牌怎么来的」单独标（vehicleSource），
     * 相机坏了可以用人工录入兜底，不影响扫码上报。
     * <p>
     * 这台设备<b>不产生任何业务判定</b>，它只提供两个现场事实：这辆车是哪台、
     * 哪几批货真的上了车。由系统拿这两样去<b>倒查</b>待发货计划 ——
     * 目的地是人在计划里早就定死的，设备无从也无需知道。扫码是「确认」不是「选择」。
     * <p>
     * 匹配不上（货不属于任何待发货计划、或分属多张计划）一律转人工，绝不替人猜目的地：
     * 猜错就是货发错店。
     * <p>
     * <b>这里没有读卡器</b>（2026-10-10 起）：现场只在上车那一刻刷一次卡，司机身份由车载终端
     * 在发车时确认（{@link DeviceDepart}）。所以本报文里<b>司机允许留空</b>，而且
     * <b>不该带卡号</b> —— 带了说明设备接错了点位，接入层与业务层都会拒绝。
     */
    @Data
    public static class DeviceLoad {
        /** 车牌识别相机读到的车牌，必填：这是「哪台车」的唯一凭据 */
        @NotBlank(message = "车牌号不能为空")
        private String vehicleNo;

        /** 车牌是怎么来的 ANPR=相机自动识别 / MANUAL=人工录入（相机坏了时的兜底） */
        private String vehicleSource;

        /**
         * 司机卡号。<b>装车口不适用</b>（2026-10-10 起）：这条通道上没有读卡器，
         * 现场只在上车那一刻刷一次卡（见 {@link DeviceDepart#getCardNo()}）。
         * 字段保留只为让「接错点位的设备」被<b>明确拒绝</b>而不是被静默忽略 ——
         * 接入层与业务层都会拦，报文带它就是配置错了。
         */
        private String cardNo;

        /**
         * 司机姓名。<b>可选</b>：装车时司机还没上车，没人是本人来证明身份，所以留空是常态
         * （留空不转人工，运单照建，责任闸门在发车那一步）。
         * 带值时只可能是装卸工/调度代填，来源一律记「人工录入、非本人确认」。
         */
        private String driverName;
        private String driverPhone;

        /** 设备声明的识别方式 FACE/MANUAL（缺省按人工录入；CARD 在装车口已不可能出现） */
        private String driverSource;

        /** 本车实际装载的分割批次号清单（扫码枪扫部位标签所得） */
        @NotEmpty(message = "至少要扫到一个分割批次号")
        private List<String> batchNos;

        /** 装车时刻，可选 */
        private LocalDateTime loadTime;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 发车确认（车载终端，第 17 条通道）。
     * <p>
     * 装车口已经能自动建单，但建完停在「待发车」，发车仍要人去点按钮 —— 自动化断在最后一步。
     * 这条请求补的就是这一步：司机上车刷卡 → 系统按<b>车牌</b>找到该车名下「待发车」的运单
     * → 推进为「运输中」并写现场发车时刻。
     * <p>
     * 与装车口 {@link DeviceLoad} 是同一套设计：<b>设备只报「我（某车）发车了」，
     * 运单号由系统倒查</b>（刷卡是「确认」不是「选择」）；发车是物理事实，
     * 且<b>不产生任何业务判定</b>（哪辆车发、发给谁早在运单里定死了）。
     * <p>
     * <b>车牌只是找车的凭据，不是责任人</b>：司机必须由卡号换出具体的人，
     * 换不出人一律转人工（「设备 ≠ 人」）。
     */
    @Data
    public static class DeviceDepart {
        /** 车牌号；接入层已做兜底（报文没有时取设备台账绑定的 vehicle_no），到这里必非空 */
        @NotBlank(message = "车牌号不能为空")
        private String vehicleNo;

        /** 车牌是怎么来的 ANPR=相机自动识别 / BOUND=设备台账绑定 / MANUAL=人工录入 */
        private String vehicleSource;

        /** 司机刷卡终端读到的卡号；有值时以卡号为准换成具体的人（卡是机器读的，比手填可靠） */
        private String cardNo;

        private String driverName;
        private String driverPhone;

        /** 设备声明的识别方式 CARD/FACE/MANUAL；是否为本人确认由该值唯一推导，不接受自报布尔值 */
        private String driverSource;

        /** 现场发车时刻；没给则由业务侧取服务器当前时间 */
        private LocalDateTime departTime;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 到达确认（车载终端，司机到站刷卡）。
     * <p>
     * 与发车（{@link DeviceDepart}）同一套设计：设备只报<b>现场事实</b> ——
     * 「哪台车」「司机刷的是哪张卡」「什么时候」，运单与站点由配送服务<b>倒查与推导</b>。
     * 「到了没」是位置事实（车在不在这个卸货点），设备不产生任何业务判定：
     * 该报哪一站由站点顺序与完成情况推导，设备无权挑站，更无权替人决定货卸在哪。
     * <p>
     * <b>到达必须由司机本人刷卡确认</b>（2026-10-11 起）：卡在司机手上、由<b>车载终端</b>读出
     * （与发车同一台设备），所以「谁把货送到了这一站」第一次有了责任人 —— 这正是「设备 ≠ 人」的落点。
     * 没带卡号一律转人工：车载终端一定读得到卡，「没读到」就是司机没刷，不拿车牌或人名顶替。
     * <p>
     * <b>报文里没有「卸货门店」（2026-10-11 去掉）</b>：车载终端不是门店收货区的固定设备，
     * 它读得到车牌、卡号、时刻、GPS，读不到门店编码 —— 门店是 ERP/OMS 的主数据，不会下发到每台车上。
     * 而这件事本来也不该由设备回答：车牌已经把运单锁定到唯一一张「未结束」的单子，
     * 站点清单由装车扫码顺序决定，「当前第一个未到达的站」在服务端是<b>唯一确定</b>的。
     * 所以「到了第几站」由服务端推导，报文不报门店、也没法报错站 —— 见
     * {@code DistributionDeviceIngestService#ingestArrive}。
     * <p>
     * <b>代价要说清楚</b>：「车开到了清单外的店」不再能靠报文拦住（原来的拦截靠门店自报 + 清单比对）。
     * 这个能力的正解是定位（GPS / 电子围栏），不是让司机在驾驶室屏上敲门店编码。
     */
    @Data
    public static class DeviceArrive {
        /** 车牌号；接入层已做兜底（报文没有时取设备台账绑定的 vehicle_no），到这里必非空 */
        @NotBlank(message = "车牌号不能为空")
        private String vehicleNo;

        /** 车牌是怎么来的 ANPR=相机自动识别 / BOUND=设备台账绑定 / MANUAL=人工录入 */
        private String vehicleSource;

        /**
         * 司机到站刷的卡号。<b>到达必须由本人刷卡确认</b>：卡是机器读的（车载终端），比手填可靠；
         * 没有卡号一律转人工 —— 谁把货送到这一站，必须落到具体的人。
         */
        private String cardNo;

        /** 现场到达时刻；没给则由业务侧取服务器当前时间 */
        private LocalDateTime arriveTime;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /** 分割作业的一个部位产出 */
    @Data
    public static class SplitItem {
        /** 标准部位编码（优先） */
        private String partCode;
        /** 部位名称，缺 partCode 时按字典反查 */
        private String partName;
        /** 该部位的重量（kg），必须大于 0 */
        private BigDecimal weightKg;
        private Integer packageCount;
        private String packageType;
    }

    /**
     * 胴体批次归集：屠宰线胴体称重分拣台。
     * <p>
     * 设备只报<b>单头白条</b>的事实——这头白条是谁、多重、哪条线、什么时候；
     * <b>批次边界与批次号都由系统算</b>（按「屠宰场 + 屠宰日」归集），设备报文里不带批次号。
     * 这与分割批次同一个道理：批次号是人手编不出也编不对的东西。
     */
    @Data
    public static class DeviceBatch {
        /** 生猪档案主键（接入层已用耳标在养殖服务换成主键，业务侧不再认耳标） */
        private Long pigId;
        /** 耳标号，仅用于回执文案 */
        private String earTagNo;

        @NotBlank(message = "屠宰场不能为空")
        private String slaughterhouse;

        /** 分拣台过磅读数（kg），必须大于 0 */
        private BigDecimal carcassWeightKg;

        /** 胴体编号，可选 */
        private String carcassNo;
        /** 分拣产线/班次，可选（留作归集口径细分，不填按当日单批处理） */
        private String lineNo;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }

    /**
     * 分割车间监控快照：摄像头定时拍一张车间现场照片。
     * <p>
     * 与其它设备通道最大的不同：<b>图片本身不在这份报文里</b>。
     * 相机先把照片 POST 给文件服务换回 {@code fileId}，再把它报进来 ——
     * 接入层单条报文 64KB 上限，图片塞进 JSON 必然超限。接入层已核对过 fileId 真实存在，
     * 所以这里不再重复核验。
     * <p>
     * 没有任何业务校验：照片不产生溯源链上的事实，不回答「切出了什么部位」这类问题。
     */
    @Data
    public static class DeviceSplitSnapshot {
        /** 照片在文件服务里的 id（必填） */
        @NotBlank(message = "照片 fileId 不能为空")
        private String fileId;

        /** 拍摄时刻（设备侧时间；为空时接入层已用接收时间兜底） */
        private LocalDateTime capturedTime;

        /** 车间（相机安装位置） */
        private String workshop;

        /**
         * 所属胴体批次号（选填）—— 工位终端把「当前正在分割的白条属于哪个批次」一并告知相机，
         * 服务端据此把照片绑定到该批次最近一次分割作业。
         * <p>
         * 不带 = 不绑定（照片只是现场影像）；带了却查不到该批次 = 编造绑定关系，由业务侧拒绝。
         * 相机自己不认识批次，这个值只能来自工位终端。
         */
        private String batchNo;

        /** 拍摄设备编号（接入层带过来） */
        private String deviceNo;

        private String source;
        private String sourceRef;
        private String rawPayload;
    }
}
