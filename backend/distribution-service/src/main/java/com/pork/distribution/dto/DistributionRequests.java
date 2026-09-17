package com.pork.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
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

    public record TemperatureCreate(@NotNull BigDecimal temperature, String recorder,
                                    BigDecimal tempRangeMin, BigDecimal tempRangeMax, String recordMethod) { }

    public record ReceiptCreate(@NotNull Long transportId, @NotNull Long storeId,
                                @NotBlank String storeName, @NotBlank String receiver, String receiverPhone,
                                Integer qtyCheck, String qtyDiffNote, Integer tempCheck, BigDecimal tempValue,
                                Integer packageIntact, List<String> receiptPhoto, String eSignature) { }
}
