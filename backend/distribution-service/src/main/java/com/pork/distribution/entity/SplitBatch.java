package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("split_batch")
public class SplitBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private Long parentBatchId;
    private Integer splitLevel;
    private String productName;
    private BigDecimal weightKg;
    private Integer packageCount;
    private String packageType;
    private LocalDateTime splitTime;
    private String workshop;
    private BigDecimal workshopTemp;
    private String operator;
    private String fileIds;
    private String note;
    private String contentHash;

    /** 数据来源：MANUAL=人工录入 DEVICE=设备自动采集（分割线扫码称重台） */
    private String source;
    /** 来源设备号/单据号 */
    private String sourceRef;
    /** 设备原始报文留档 */
    private String rawPayload;

    private LocalDateTime createTime;
}
