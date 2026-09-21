package com.pork.sales.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SaleRecordVO(Long id, Long splitBatchId, String productQrCode, Long storeId, String storeName,
 LocalDateTime shelfTime, LocalDateTime sellTime, BigDecimal sellPrice, BigDecimal sellWeightKg,
 Integer isActivated, LocalDateTime activateTime, Integer status, LocalDate expireDate,
 LocalDateTime createTime, String productName, String batchNo, String blockHash,
 /** 数据来源 MANUAL/DEVICE/API/IMPORT：POS 扫码售出的记录为 DEVICE */
 String source, String sourceRef) { }
