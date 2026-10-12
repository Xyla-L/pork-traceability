package com.pork.slaughter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.slaughter.entity.CarcassStamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface CarcassStampMapper extends BaseMapper<CarcassStamp> {

    /**
     * 作废指定记录：写入理由与实名操作人，并把 status 同步为 2（已作废）保持旧口径兼容。
     *
     * @return 受影响行数；0 表示记录不存在或已被作废
     */
    @Update("UPDATE carcass_stamp SET void_flag = 1, void_reason = #{reason}, status = 2, "
            + "void_operator = #{operator}, void_time = NOW() WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 该生猪「已作废」的胴体盖章记录（仅供追溯侧标注使用，原生 SQL 显式绕过逻辑删除，只读）。
     * 盖章是法定授权行为，被作废的印章必须能被追溯方看到——否则等于章悄悄收回。
     */
    @Select("SELECT id, stamp_no AS bizNo, void_reason AS voidReason, "
            + "void_operator AS voidOperator, void_time AS voidTime "
            + "FROM carcass_stamp WHERE pig_id = #{pigId} AND void_flag = 1 ORDER BY void_time DESC")
    List<Map<String, Object>> selectVoidedByPig(@Param("pigId") Long pigId);

    /**
     * 该生猪名下的盖章记录（**含已作废的**，原生 SQL 显式绕过逻辑删除，只读；无则返回 null）。
     * <p>
     * `carcass_stamp` 上 `pig_id` 是唯一键，而「作废」只是把 `void_flag` 置 1、行本身还在，
     * 所以判断「这头白条能不能再盖一枚章」必须连作废行一起看：只查有效行会漏判，
     * 写入时直接撞唯一键（对外表现为一次数据库异常，而不是一条待人工）。
     */
    @Select("SELECT id, batch_no AS batchNo, carcass_no AS carcassNo, stamp_no AS stampNo, "
            + "status, void_flag AS voidFlag, void_reason AS voidReason "
            + "FROM carcass_stamp WHERE pig_id = #{pigId} LIMIT 1")
    Map<String, Object> selectAnyByPigId(@Param("pigId") Long pigId);
}