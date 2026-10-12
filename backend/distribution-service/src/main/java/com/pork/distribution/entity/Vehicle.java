package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 车辆备案：车牌 → 车型 / 载重 / 归属。
 * <p>
 * 补这块主数据之前，车牌在系统里只是一个字符串（甚至是人工敲进设备名里的），
 * 「车型」这个运单字段只能靠人手填。有了备案，装车口认出车牌就能把车型带出来。
 * <p>
 * 备案**不是准入门槛**：车牌没备案也不拦着装车（新车、外调车天天有），
 * 只是带不出车型而已 —— 备案是「能省一道手工」，不是「能不让走」。
 */
@Data
@TableName("vehicle")
public class Vehicle {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 车牌号（唯一） */
    private String vehicleNo;
    /** 车型，如 冷藏车 */
    private String vehicleType;
    /** 核定载重(kg) */
    private BigDecimal loadCapacityKg;
    /** 车辆归属（自有车队 / 承运商名称） */
    private String ownerName;
    /** 1启用 0停用 */
    private Integer status;
    private String remark;
}
