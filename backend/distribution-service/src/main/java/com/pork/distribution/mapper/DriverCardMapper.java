package com.pork.distribution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.distribution.entity.DriverCard;

/**
 * 司机-卡绑定 Mapper：装车口刷卡的「卡号 → 司机」查询入口。
 */
public interface DriverCardMapper extends BaseMapper<DriverCard> {
}
