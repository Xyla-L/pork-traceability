package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "store_receipt", autoResultMap = true)
public class StoreReceipt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transportId;
    private Long storeId;
    private String storeName;
    private LocalDateTime receiptTime;
    private String receiver;
    private String receiverPhone;
    private Integer qtyCheck;
    private String qtyDiffNote;
    private Integer tempCheck;
    private BigDecimal tempValue;
    private Integer packageIntact;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> receiptPhoto;
    private String eSignature;
    private String contentHash;
    private LocalDateTime createTime;
}
