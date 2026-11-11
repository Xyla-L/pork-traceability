package com.pork.breeding.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 跨库读机构名：机构与养殖场的关联只靠「名称一致」（20-sync-org-from-business.sql 的同步口径），
 * 这里按机构 id 反查名称，再由 FarmScope 拿名称去 farm 表找场。
 * 只读单行、不做任何写入——机构归属的权威仍是机构管理页。
 */
@Mapper
public interface SysOrgRefMapper {

    @Select("SELECT name FROM db_common.sys_org WHERE id = #{orgId}")
    String selectNameById(@Param("orgId") Long orgId);
}
