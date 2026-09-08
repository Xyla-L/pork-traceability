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
    private String contentHash;
    private LocalDateTime createTime;
}
