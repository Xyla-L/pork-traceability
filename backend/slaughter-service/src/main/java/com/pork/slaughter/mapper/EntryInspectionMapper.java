package com.pork.slaughter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.slaughter.entity.EntryInspection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface EntryInspectionMapper extends BaseMapper<EntryInspection> {

    /**
     * 作废指定记录：写入理由与实名操作人。
     * <p>
     * 用自定义 SQL 而不是 removeById，是因为逻辑删除只会把标记翻成 1，
     * 写不下「为什么作废、谁作的废」——而这两项恰恰是事后最需要回答的问题。
     *
     * @return 受影响行数；0 表示记录不存在或已被作废
     */
    @Update("UPDATE entry_inspection SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_time = NOW() WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 该生猪「已作废」的入场查验记录（仅供追溯侧标注使用）。
     * <p>
     * 常规查询会被 {@code @TableLogic} 自动滤掉作废行——那是对的（追溯一律取当前有效记录），
     * 但「这一环原本有一条记录、后来被作废了」必须能被说明，否则数据凭空消失本身就是一种失真。
     * 所以这里用原生 SQL 显式绕过逻辑删除，只读、不参与任何写路径。
     */
    @Select("SELECT id, batch_no AS bizNo, void_reason AS voidReason, "
            + "void_operator AS voidOperator, void_time AS voidTime "
            + "FROM entry_inspection WHERE pig_id = #{pigId} AND void_flag = 1 ORDER BY void_time DESC")
    List<Map<String, Object>> selectVoidedByPig(@Param("pigId") Long pigId);
}