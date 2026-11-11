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
     * 分组视图取数：检疫证连同所属生猪、批次、养殖场一次 join 出来。
     * <p>
     * 检疫证表同样只挂 {@code pig_id}，批次要顺着 {@code pig_individual.batch_id} 反查。
     * 现实里一张证覆盖一批，本系统的存量数据是「一证一头」（设备逐头出证），
     * 所以分组不按证号、按「批次 + 出证机构 + 出证日期」归——同一批同一天出的证算一次出证动作。
     */
    @Select("<script>"
            + "SELECT q.id AS id, q.pig_id AS pigId, q.cert_no AS certNo, q.issue_org AS issueOrg, "
            + "q.issue_time AS issueTime, q.valid_until AS validUntil, q.inspector AS inspector, "
            + "q.cert_type AS certType, q.file_id AS fileId, q.source AS source, q.source_ref AS sourceRef, "
            + "q.void_flag AS voidFlag, q.void_reason AS voidReason, "
            + "q.void_operator AS voidOperator, q.void_time AS voidTime, q.create_time AS createTime, "
            + "i.ear_tag_no AS earTagNo, i.breed AS pigBreed, i.farm_id AS farmId, "
            + "i.batch_id AS batchId, b.batch_no AS pigBatchNo, b.breed AS batchBreed, "
            + "f.farm_name AS farmName "
            + "FROM quarantine_certificate q "
            + "JOIN pig_individual i ON i.id = q.pig_id AND i.deleted = 0 "
            + "LEFT JOIN pig_batch b ON b.id = i.batch_id "
            + "LEFT JOIN farm f ON f.id = i.farm_id "
            + "<where>"
            + "<if test='earTagNo != null and earTagNo != \"\"'> AND i.ear_tag_no = #{earTagNo}</if>"
            + "<if test='farmId != null'> AND i.farm_id = #{farmId}</if>"
            + "</where>"
            + " ORDER BY q.issue_time DESC, q.id DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> selectGroupRows(@Param("earTagNo") String earTagNo,
                                              @Param("farmId") Long farmId,
                                              @Param("limit") int limit);

    /**
     * 该生猪「已作废」的检疫证（仅供追溯侧标注使用，原生 SQL 显式绕过逻辑删除，只读）。
     * 检疫证被作废后若在追溯里凭空消失，等于给了「悄悄撤证」的口子。
     */
    @Select("SELECT id, cert_no AS bizNo, void_reason AS voidReason, "
            + "void_operator AS voidOperator, void_time AS voidTime "
            + "FROM quarantine_certificate WHERE pig_id = #{pigId} AND void_flag = 1 ORDER BY void_time DESC")
    List<Map<String, Object>> selectVoidedByPig(@Param("pigId") Long pigId);
}