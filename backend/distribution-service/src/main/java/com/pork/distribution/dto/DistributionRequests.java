package com.pork.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DistributionRequests {
    private DistributionRequests() { }

    public record BatchCreate(String batchNo, @NotEmpty List<Long> pigIds,
                              @NotNull @DecimalMin("0.1") BigDecimal totalWeightKg,
                              @NotBlank(message = "屠宰场不能为空") String slaughterhouse,
                              @NotBlank(message = "操作人不能为空") String operator,
                              String note) { }

    // 分割批次编辑：仅允许修改业务属性，父批次/层级/批次号/哈希不可变
    public record SplitUpdate(@NotBlank(message = "产品名称不能为空") String productName,
                              @NotNull(message = "重量不能为空") @DecimalMin(value = "0.1", message = "重量必须大于0") BigDecimal weightKg,
                              @Min(value = 1, message = "包装数量至少为1") Integer packageCount,
                              String packageType, String workshop, BigDecimal workshopTemp,
                              String operator, String note) { }

    public record SplitCreate(String batchNo, @NotNull Long parentBatchId,
                              @Min(1) @Max(4) Integer splitLevel,
                              @NotBlank String productName,
                              @NotNull @DecimalMin("0.1") BigDecimal weightKg,
                              @Min(1) Integer packageCount, String packageType,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime splitTime,
                              String workshop, BigDecimal workshopTemp, String operator, String fileIds, String note) { }

    public record TransportCreate(String transportNo, @NotNull Long splitBatchId,
                                  @NotBlank String vehicleNo, String vehicleType, String refrigeration,
                                  String driverName, String driverPhone, String origin, String destination,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedDepart,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedArrive) { }

    // 运单编辑：仅业务属性，运单号/状态/实际发到达时间不可改
    public record TransportUpdate(@NotNull Long splitBatchId,
                                  @NotBlank String vehicleNo, String vehicleType, String refrigeration,
                                  String driverName, String driverPhone, String origin, String destination,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedDepart,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedArrive) { }

    /*
     * 发货计划：把「发哪批货、去哪家店、什么时候走」这个决策结构化。
     * 车与人（vehicleNo / driverName）在计划阶段允许留空——车次还没定是常态；
     * 生成运单时会强制校验必填，因为那时货已经要走了，责任必须落到具体车、具体人。
     * 制单人不从报文取，一律由 SourceGuard 从登录态解析，避免前端伪造。
     */
    public record PlanCreate(@NotNull(message = "计划发货日不能为空")
                             @JsonFormat(pattern = "yyyy-MM-dd") LocalDate planDate,
                             @NotNull(message = "目的地门店不能为空") Long storeId,
                             @NotBlank(message = "目的地门店名不能为空") String storeName,
                             @NotEmpty(message = "至少要勾选一个待发运的分割批次") List<Long> splitBatchIds,
                             String vehicleNo, String vehicleType, String refrigeration,
                             String driverName, String driverPhone, String origin,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedDepart,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedArrive,
                             String remark) { }

    /*
     * 计划编辑：已排车/已取消的计划不允许再改货物明细，服务层拦。
     * splitBatchIds 可空有两种含义：① 只想改车/司机/时间等属性，不动货物；
     * ② 「部分排车」的计划里，已排进运单的那部分不能再动，剩下的才是可改集合。
     */
    public record PlanUpdate(@NotNull(message = "计划发货日不能为空")
                             @JsonFormat(pattern = "yyyy-MM-dd") LocalDate planDate,
                             @NotNull(message = "目的地门店不能为空") Long storeId,
                             @NotBlank(message = "目的地门店名不能为空") String storeName,
                             List<Long> splitBatchIds,
                             String vehicleNo, String vehicleType, String refrigeration,
                             String driverName, String driverPhone, String origin,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedDepart,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedArrive,
                             String remark) { }

    /*
     * 生成运单：splitBatchIds 为空 = 把剩余未排车的货物全部发走；
     * 给了子集 = 分车，只发这几批，剩下的留在计划里等下一车。
     */
    public record PlanGenerate(List<Long> splitBatchIds) { }

    /*
     * 订单转入（第 16 条通道 ORDER 的落点）：外部系统把「要什么货、去哪、什么时候要」交进来，
     * 本系统转成一张**待排车的**发货计划。货怎么装、哪台车、谁开，仍由排车决定 ——
     * 订单不等于计划，更不等于运单。
     *
     * 只收四件事，**金额、优惠、合同条款、支付状态一个都不收**：
     * 不是"事后不接支付"，而是字段层面就不接收 —— 金额不参与上链，也不影响召回圈范围。
     *
     * items 用「部位 + 重量」表达（商家选购的粒度），不是具体分割批次：
     * split_batch 是实物批次（18.6kg 大排一批 + 12.4kg 大排一批），
     * 「要 20kg 大排」到「装哪几批」之间必须有一次分配，这一步由服务端按可发运批次凑，
     * 凑不齐一律转人工 —— 不猜、不拆零兜底。
     *
     * storeName 即使前端带了也以机构树的为准（机构树是门店的权威来源）。
     */
    public record OrderIntake(@NotBlank(message = "订单号不能为空") String orderNo,
                              @NotNull(message = "目的地门店不能为空") Long storeId,
                              String storeName,
                              @JsonFormat(pattern = "yyyy-MM-dd") LocalDate planDate,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime expectArrive,
                              // @Valid 必须加：没有它，嵌套明细上的 @DecimalMin 不会级联生效，
                              // 一个负重量就能绕过校验、生成一张 0 明细的空计划
                              @NotEmpty(message = "订单至少要有一个货物明细") @Valid List<OrderItem> items,
                              String remark,
                              String sourceRef,
                              String rawPayload) { }

    /** 订单明细：商家选购的粒度是「部位 + 重量」 */
    public record OrderItem(String partCode, String partName,
                            @NotNull(message = "订购重量不能为空")
                            @DecimalMin(value = "0.1", message = "订购重量必须大于0") BigDecimal weightKg) { }

    /*
     * 装车口现场事实：车牌识别相机读到的车牌 + 司机刷卡/识别到的人，外加来源三件套。
     * 它只用来「补齐」计划里空着的车与人，或校验与计划填的是否一致 ——
     * 一致：说明装的就是计划安排的那辆车，放行；
     * 不一致：可能装错车了，必须转人工核对，设备无权自行改掉计划安排。
     *
     * vehicleType 是「车牌 → 车辆备案」带出来的车型，仅用于补齐计划里没填的车型
     * （车型不是安全红线，与计划不一致不做拦截，车牌不一致才拦）。
     * driverSource 是司机来源（口径见 DriverSource），它同时决定 driver_confirmed：
     * 刷卡/人脸=本人确认，车牌反查=推定，人工录入=非本人确认。
     */
    public record LoadSite(String vehicleNo, String vehicleType, String driverName, String driverPhone,
                           String driverSource, String sourceRef, String rawPayload, Long orgId) { }

    public record TemperatureCreate(@NotNull BigDecimal temperature, String recorder,
                                    BigDecimal tempRangeMin, BigDecimal tempRangeMax, String recordMethod) { }

    public record ReceiptCreate(@NotNull Long transportId, @NotNull Long storeId,
                                @NotBlank String storeName, @NotBlank String receiver, String receiverPhone,
                                Integer qtyCheck, String qtyDiffNote, Integer tempCheck, BigDecimal tempValue,
                                Integer packageIntact, List<String> receiptPhoto, String eSignature) { }

    /**
     * 批量签收：一次勾选多张「待签收」运单，按统一核验结果逐单签收。
     * <p>
     * 门店不由前端指定 —— 每张运单签的是它自己「已到达且未签收」的那一站；
     * 零售商的站点边界由服务端按令牌归属收敛（见 OrgScope），请求里给不了别家门店。
     * 一回最多 50 单，是「一次卸货作业的合理上限」，不是性能限制。
     */
    public record ReceiptBatch(@NotEmpty(message = "请选择要签收的运单") @Size(max = 50, message = "一次最多签收 50 单")
                               List<Long> transportIds,
                               @NotBlank(message = "请输入签收人姓名") String receiver, String receiverPhone,
                               Integer qtyCheck, String qtyDiffNote, Integer tempCheck, BigDecimal tempValue,
                               Integer packageIntact, List<String> receiptPhoto, String eSignature) { }

    /**
     * 门店拒收：冷链货物有异常时门店验货后拒绝接收。
     * <p>
     * 理由必填（随拒收事件上链留档）；storeId 供管理端代登记时指明门店，
     * 零售商调用时服务端按令牌门店边界覆写 —— 门店只能拒自己那站的货。
     */
    public record StopReject(Long storeId, @NotBlank(message = "拒收必须填写理由") String reason) { }

    /**
     * 拒收后的处置登记：货去哪由人决定，系统只记事实（退回供应商/就地销毁/折价接收）。
     */
    public record StopDisposal(@NotBlank(message = "请选择处置方式") String disposalType, String note) { }

    // ==================== 拒收回运链（2026-11-02）====================

    /**
     * 回运/处置计划生成：可选指定每辆车的司机；不指定则系统自动选车（按核定载重贪心装箱），
     * 司机留空 —— 发车闸门要求补录司机（或司机在车载终端刷卡发车）。
     */
    public record LegPlanGenerate(java.util.List<VehicleAssign> vehicles) {
        public record VehicleAssign(@NotBlank String vehicleNo, String driverName, String driverPhone) { }
    }

    // ==================== 召回收运（2026-11-04）====================

    /**
     * 召回登记（销售服务 → 配送服务，内部接口）：把召回单涉及的「门店 × 批次」写进待收运池。
     * <p>
     * 传的是召回单的事实（门店、批次、涉及条数与货重），不传车牌/司机 ——
     * 收运运单是冷链中心排车时的事，召回发起时还没有车。
     */
    public record RecallCollectRegister(@NotNull Long recallId,
                                        @NotBlank String recallNo,
                                        String reason,
                                        @NotEmpty(message = "召回范围为空") java.util.List<RecallCollectItem> items) {
        public record RecallCollectItem(@NotNull Long storeId, String storeName,
                                        @NotNull Long splitBatchId, java.math.BigDecimal weightKg,
                                        Integer saleCount) { }
    }

    /**
     * 生成收运运单（冷链中心页面）：把待收运池里的召回货按门店分站排成一张运单，
     * 路线是「冷链中心 → 门店1 … 门店N → 无害化处理中心」。
     * <p>
     * 车牌必填：收运是一趟真实的车，谁去收必须落到具体车；司机可留空，发车由车载终端刷卡落人。
     */
    public record RecallTransportGenerate(@NotBlank(message = "请选择收运车辆") String vehicleNo,
                                          String driverName, String driverPhone) { }
}
