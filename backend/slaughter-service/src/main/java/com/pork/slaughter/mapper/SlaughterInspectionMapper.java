package com.pork.slaughter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.slaughter.entity.SlaughterInspection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface SlaughterInspectionMapper extends BaseMapper<SlaughterInspection> {
    // 继承了 BaseMapper，已经拥有了通用的 CRUD 方法

    /**
     * 作废指定记录：写入理由与实名操作人，并把 status 同步为 3（已作废）保持旧口径兼容。
     *
     * @return 受影响行数；0 表示记录不存在或已被作废
     */
    @Update("UPDATE slaughter_inspection SET void_flag = 1, void_reason = #{reason}, status = 3, "
            + "void_operator = #{operator}, void_time = NOW() WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 该生猪「已作废」的屠宰检验记录（仅供追溯侧标注使用，原生 SQL 显式绕过逻辑删除，只读）。
     */
    @Select("SELECT id, inspect_no AS bizNo, void_reason AS voidReason, "
            + "void_operator AS voidOperator, void_time AS voidTime "
            + "FROM slaughter_inspection WHERE pig_id = #{pigId} AND void_flag = 1 ORDER BY void_time DESC")
    List<Map<String, Object>> selectVoidedByPig(@Param("pigId") Long pigId);
}