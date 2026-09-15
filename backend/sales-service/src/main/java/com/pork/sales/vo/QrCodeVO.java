package com.pork.sales.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record QrCodeVO(Long id, String qrCode, Long splitBatchId, String batchNo, Long transportId,
                       String transportNo, Long receiptId, Long storeId, String storeName, Integer status,
                       LocalDate expireDate, LocalDateTime createTime) { }
