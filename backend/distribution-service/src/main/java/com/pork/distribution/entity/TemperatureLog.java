package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("temperature_log")
public class TemperatureLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transportId;
    private LocalDateTime recordTime;
    private BigDecimal temperature;
    private BigDecimal tempRangeMin;
    private BigDecimal tempRangeMax;
    private Integer isAbnormal;
    private String recorder;
    private String recordMethod;
    private LocalDateTime createTime;
}
