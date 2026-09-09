package com.pork.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("retail_sale")
public class RetailSale {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long splitBatchId;
    private String productQrCode;
    private Long storeId;
    private String storeName;
    private LocalDateTime shelfTime;
    private LocalDateTime sellTime;
    private BigDecimal sellPrice;
    private BigDecimal sellWeightKg;
    private Integer isActivated;
    private LocalDateTime activateTime;
    private Integer status;
    private LocalDate expireDate;
    private String blockHash;
    private LocalDateTime createTime;
}
