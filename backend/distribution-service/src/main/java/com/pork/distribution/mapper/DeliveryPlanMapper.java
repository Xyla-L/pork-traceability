package com.pork.distribution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.distribution.entity.DeliveryPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeliveryPlanMapper extends BaseMapper<DeliveryPlan> {

    /**
     * 当日已用掉的「DP+日期-」计划号个数，<b>含已取消的</b>。
     * <p>
     * 用原生 SQL 而不是 {@code selectCount}：计划号是现场与单据上唯一的称呼，
     * 取消过的计划若被算作「没用过」，号就会复用，两份不同的单据撞同一个号。
     */
    @Select("SELECT COUNT(*) FROM delivery_plan WHERE plan_no LIKE CONCAT(#{prefix}, '%')")
    long countByPlanNoPrefix(@Param("prefix") String prefix);

    /** 计划号是否已被占用（同样含已取消的，理由见上） */
    @Select("SELECT COUNT(*) FROM delivery_plan WHERE plan_no = #{planNo}")
    long countByPlanNo(@Param("planNo") String planNo);
}
