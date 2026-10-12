package com.pork.distribution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.distribution.entity.SplitJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 分割作业 Mapper。
 * <p>
 * 作业号计数走<b>原生 SQL</b>：{@code selectCount} 会被 {@code @TableLogic} 自动加上
 * {@code void_flag = 0}，导致已作废的作业号被当成没用过、于是被复用——
 * 而作业号是追溯查询的引用键，绝不能被复用。
 */
public interface SplitJobMapper extends BaseMapper<SplitJob> {

    /** 当日前缀下已占用的作业号个数（含已作废，绕开逻辑删除） */
    @Select("SELECT COUNT(*) FROM split_job WHERE job_no LIKE CONCAT(#{prefix}, '%')")
    long countByJobNoPrefix(@Param("prefix") String prefix);

    /** 作业号是否已被占用（含已作废） */
    @Select("SELECT COUNT(*) FROM split_job WHERE job_no = #{jobNo}")
    long countByJobNo(@Param("jobNo") String jobNo);
}
