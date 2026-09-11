package com.pork.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.trace.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 - Mapper 接口（只读，用于解析处理人姓名）
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
