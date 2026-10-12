package com.pork.distribution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.distribution.entity.MeatPart;

/**
 * 部位字典 Mapper。
 * <p>
 * 字典是配置数据、条数固定（十几条），无需分页与复杂条件，一律走 MyBatis-Plus 包装器即可。
 */
public interface MeatPartMapper extends BaseMapper<MeatPart> {
}
