package com.pork.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class SalesRequests {
    private SalesRequests() { }

    public record QrBatch(@NotNull Long splitBatchId, @NotNull @Min(1) @Max(1000) Integer count,
                          Long storeId, String storeName, LocalDate expireDate) { }

    public record SaleCreate(@NotBlank String qrCode, @NotNull @DecimalMin("0.0") BigDecimal sellPrice,
                             @NotNull @DecimalMin("0.001") BigDecimal sellWeightKg) { }

    public record WarningHandle(boolean handled, @NotBlank String handler) { }

    public record RecallCreate(@NotBlank String reason, @NotNull @Min(1) @Max(3) Integer riskLevel,
                               @NotEmpty Map<String, Object> scope, @NotBlank String initiator) { }

    public record RecallStatus(@NotNull @Min(2) @Max(4) Integer status, Integer recalledCount) { }
}
