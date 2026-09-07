package com.pork.breeding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.breeding.entity.Farm;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FarmMapper extends BaseMapper<Farm> {
}