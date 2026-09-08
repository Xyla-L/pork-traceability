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
                              String slaughterhouse, String operator) { }

    public record SplitCreate(String batchNo, @NotNull Long parentBatchId,
                              @NotNull @Min(1) @Max(4) Integer splitLevel,
                              @NotBlank String productName,
                              @NotNull @DecimalMin("0.1") BigDecimal weightKg,
                              @Min(1) Integer packageCount, String packageType,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime splitTime,
                              String workshop, BigDecimal workshopTemp, String operator, String fileIds) { }

    public record TransportCreate(String transportNo, @NotNull Long splitBatchId,
                                  @NotBlank String vehicleNo, String vehicleType, String refrigeration,
                                  String driverName, String driverPhone, String origin, String destination,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedDepart,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime plannedArrive) { }

    public record TemperatureCreate(@NotNull BigDecimal temperature, String recorder,
                                    BigDecimal tempRangeMin, BigDecimal tempRangeMax, String recordMethod) { }

    public record ReceiptCreate(@NotNull Long transportId, @NotNull Long storeId,
                                @NotBlank String storeName, @NotBlank String receiver, String receiverPhone,
                                Integer qtyCheck, String qtyDiffNote, Integer tempCheck, BigDecimal tempValue,
                                Integer packageIntact, List<String> receiptPhoto, @NotBlank String eSignature) { }
}
