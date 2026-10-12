package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 司机备案：装车发运「责任人」的人员底表。
 * <p>
 * 与 {@link DriverCard}、{@link DriverVehicle} 一起构成司机识别的两条路：
 * 刷卡（卡号 → 人，本人确认）与车牌反查（车牌 → 常用主驾，仅推定）。
 */
@Data
@TableName("driver")
public class Driver {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 司机工号（唯一） */
    private String driverNo;
    /** 司机姓名 */
    private String driverName;
    private String phone;
    /** 身份证号（选填） */
    private String idCardNo;
    /** 驾驶证号（选填） */
    private String licenseNo;
    /** 1在职 0离职 */
    private Integer status;
    private String remark;
}
