package com.pork.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "recall_order", autoResultMap = true)
public class RecallOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recallNo;
    private String reason;
    private Integer riskLevel;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> scope;
    private String initiator;
    private LocalDateTime initiateTime;
    private Integer status;
    private LocalDateTime completedTime;
    private Integer affectedCount;
    private Integer recalledCount;
    private String blockHash;
    private LocalDateTime createTime;
}
