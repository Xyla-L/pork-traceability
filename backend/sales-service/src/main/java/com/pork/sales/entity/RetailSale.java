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
    private Long transportId;
    private Long receiptId;
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
    /** 数据来源 MANUAL=人工录入 DEVICE=POS/收银机自动采集 API=第三方推送 IMPORT=批量导入 */
    private String source;
    /** 来源设备号/单据号 */
    private String sourceRef;
    /** 设备原始报文留档 */
    private String rawPayload;
    private LocalDateTime createTime;
}
