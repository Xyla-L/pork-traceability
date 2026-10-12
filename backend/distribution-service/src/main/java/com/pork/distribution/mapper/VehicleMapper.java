package com.pork.distribution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.distribution.entity.Vehicle;

/**
 * 车辆备案 Mapper。
 * <p>
 * 主数据条数少（几十台车），只做「按车牌查一条」，不需要分页与复杂条件。
 */
public interface VehicleMapper extends BaseMapper<Vehicle> {
}
