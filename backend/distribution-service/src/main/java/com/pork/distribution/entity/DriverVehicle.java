package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 司机-车辆绑定：装车口「车牌反查」的落点。
 * <p>
 * <b>刻意不是一对一</b>：一个司机可以开多台车，一台车也可以有多个司机（交接班、顶班）。
 * 所以它只能标出 {@link #isPrimary 常用主驾}，反查命中时得到的是**推定**，
 * 转人工时把推定值一并带给操作员省一道查询，但绝不用它直接放行 ——
 * 「设备 ≠ 人」，车牌号不能顶替责任人。
 */
@Data
@TableName("driver_vehicle")
public class DriverVehicle {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long driverId;
    private String vehicleNo;
    /** 1=常用主驾（车牌反查时命中） */
    private Integer isPrimary;
    /** 1有效 0解除 */
    private Integer status;
    private String remark;
}
