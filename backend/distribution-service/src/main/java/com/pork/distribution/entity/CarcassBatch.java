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
@TableName(value = "carcass_batch", autoResultMap = true)
public class CarcassBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> pigIds;
    private BigDecimal totalWeightKg;
    private String slaughterhouse;
    private LocalDateTime createTime;
    private String operator;
}
