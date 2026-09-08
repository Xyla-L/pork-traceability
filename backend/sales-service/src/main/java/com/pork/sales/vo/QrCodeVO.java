package com.pork.sales.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record QrCodeVO(Long id, String qrCode, Long splitBatchId, Integer status,
                       LocalDate expireDate, LocalDateTime createTime) { }
