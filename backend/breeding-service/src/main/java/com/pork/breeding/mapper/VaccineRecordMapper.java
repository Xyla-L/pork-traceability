package com.pork.breeding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.breeding.entity.VaccineRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface VaccineRecordMapper extends BaseMapper<VaccineRecord> {

    /**
     * 作废指定免疫记录：写入理由与实名操作人。
     *
     * @return 受影响行数；0 表示记录不存在或已被作废
     */
    @Update("UPDATE vaccine_record SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_time = NOW() WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 该生猪「已作废」的免疫记录（仅供追溯侧标注使用，原生 SQL 显式绕过逻辑删除，只读）。
     */
    @Select("SELECT id, vaccine_name AS bizNo, void_reason AS voidReason, "
            + "void_operator AS voidOperator, void_time AS voidTime "
            + "FROM vaccine_record WHERE pig_id = #{pigId} AND void_flag = 1 ORDER BY void_time DESC")
    List<Map<String, Object>> selectVoidedByPig(@Param("pigId") Long pigId);

    /**
     * 该生猪的全部免疫记录（含已作废）。
     * <p>
     * void_flag 同时是 MyBatis-Plus 的逻辑删除标记，常规查询会把作废记录整条藏掉——
     * 列表页若只看有效记录，用户会以为「上报成功却没入账」。这里用原生 SQL 显式取全量，
     * 由上层按 voidFlag 标注「已作废」，只读、不参与业务判断。
     */
    @Select("SELECT * FROM vaccine_record WHERE pig_id = #{pigId} ORDER BY create_time DESC, id DESC")
    List<VaccineRecord> selectByPigIncludeVoid(@Param("pigId") Long pigId);
}