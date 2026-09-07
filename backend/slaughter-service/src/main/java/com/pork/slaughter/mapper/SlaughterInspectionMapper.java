package com.pork.slaughter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.slaughter.entity.SlaughterInspection;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SlaughterInspectionMapper extends BaseMapper<SlaughterInspection> {
    // 继承了 BaseMapper，已经拥有了通用的 CRUD 方法
}