package com.pork.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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
                           String driverSource, String sourceRef, String rawPayload) { }

    public record TemperatureCreate(@NotNull BigDecimal temperature, String recorder,
                                    BigDecimal tempRangeMin, BigDecimal tempRangeMax, String recordMethod) { }

    public record ReceiptCreate(@NotNull Long transportId, @NotNull Long storeId,
                                @NotBlank String storeName, @NotBlank String receiver, String receiverPhone,
                                Integer qtyCheck, String qtyDiffNote, Integer tempCheck, BigDecimal tempValue,
                                Integer packageIntact, List<String> receiptPhoto, String eSignature) { }
}
