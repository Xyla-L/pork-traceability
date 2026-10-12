package com.pork.slaughter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.slaughter.entity.RactopamineTest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface RactopamineTestMapper extends BaseMapper<RactopamineTest> {

    /**
     * 作废指定记录：写入理由与实名操作人。
     * 逻辑删除只会把标记翻成 1，写不下「为什么作废、谁作的废」，而这两项恰恰是事后最需要回答的。
     *
     * @return 受影响行数；0 表示记录不存在或已被作废
     */
    @Update("UPDATE ractopamine_test SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_time = NOW() WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 该生猪「已作废」的瘦肉精检测记录（仅供追溯侧标注使用，原生 SQL 显式绕过逻辑删除，只读）。
     */
    @Select("SELECT id, test_no AS bizNo, void_reason AS voidReason, "
            + "void_operator AS voidOperator, void_time AS voidTime "
            + "FROM ractopamine_test WHERE pig_id = #{pigId} AND void_flag = 1 ORDER BY void_time DESC")
    List<Map<String, Object>> selectVoidedByPig(@Param("pigId") Long pigId);
}