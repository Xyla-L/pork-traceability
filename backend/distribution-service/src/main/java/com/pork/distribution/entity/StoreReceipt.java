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
    /** 数据来源 MANUAL=人工录入 DEVICE=设备/PDA自动采集 API=第三方推送 IMPORT=批量导入 */
    private String source;
    /** 来源设备号/单据号 */
    private String sourceRef;
    /** 设备原始报文留档 */
    private String rawPayload;
    private String contentHash;
    private LocalDateTime createTime;
    @TableField(exist = false)
    private String transportNo;
}
