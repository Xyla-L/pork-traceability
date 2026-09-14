package com.pork.sales.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpireWarningVO(
        Long id,
        Long saleId,
        Integer warningLevel,
        LocalDateTime warningTime,
        String notifyChannel,
        Integer notified,
        Integer handled,
        LocalDateTime handleTime,
        String handler,
        LocalDateTime createTime,
        String productQrCode,
        String productName,
        String storeName,
        LocalDate expireDate
) {}
