package com.pork.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.trace.entity.ComplaintReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投诉举报 - Mapper 接口
 */
@Mapper
public interface ComplaintReportMapper extends BaseMapper<ComplaintReport> {
    // 继承 BaseMapper 后，已具备 CRUD 基础能力
    // 如需复杂查询，可在此定义方法并编写对应 XML
}