package com.pork.breeding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.breeding.entity.QuarantineCertificate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface QuarantineCertMapper extends BaseMapper<QuarantineCertificate> {

    /**
     * 作废指定检疫证：写入理由与实名操作人。
     * 检疫证作废属重大动作（直接影响生猪能否调运），理由必须留档。
     *
     * @return 受影响行数；0 表示证书不存在或已被作废
     */
    @Update("UPDATE quarantine_certificate SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_time = NOW() WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 该生猪「已作废」的检疫证（仅供追溯侧标注使用，原生 SQL 显式绕过逻辑删除，只读）。
     * 检疫证被作废后若在追溯里凭空消失，等于给了「悄悄撤证」的口子。
     */
    @Select("SELECT id, cert_no AS bizNo, void_reason AS voidReason, "
            + "void_operator AS voidOperator, void_time AS voidTime "
            + "FROM quarantine_certificate WHERE pig_id = #{pigId} AND void_flag = 1 ORDER BY void_time DESC")
    List<Map<String, Object>> selectVoidedByPig(@Param("pigId") Long pigId);
}