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
     * 分组视图取数：把免疫记录连同它挂的那头猪、猪所属批次、养殖场一次 join 出来。
     * <p>
     * 为什么要 join：{@code vaccine_record} 只有 {@code pig_id}，没有批次字段——
     * 一条免疫记录属于哪一批，只能顺着 {@code pig_individual.batch_id} 反查。
     * 分组在前端做不了（会退化成 N+1 逐头拉取），因此在这里一次取全、由上层归组。
     * <p>
     * {@code i.deleted = 0} 显式写死：原生 SQL 不会自动带上 MyBatis-Plus 的逻辑删除条件。
     * {@code limit} 是取数上限（分组要全量才能算准），演示/中小规模远达不到。
     */
    @Select("<script>"
            + "SELECT v.id AS id, v.pig_id AS pigId, v.vaccine_name AS vaccineName, "
            + "v.batch_no AS batchNo, v.manufacturer AS manufacturer, "
            + "v.inject_time AS injectTime, v.dosage AS dosage, v.inject_site AS injectSite, "
            + "v.operator AS operator, v.source AS source, v.source_ref AS sourceRef, "
            + "v.file_ids AS fileIds, v.void_flag AS voidFlag, v.void_reason AS voidReason, "
            + "v.void_operator AS voidOperator, v.void_time AS voidTime, v.create_time AS createTime, "
            + "i.ear_tag_no AS earTagNo, i.breed AS pigBreed, i.farm_id AS farmId, "
            + "i.batch_id AS batchId, b.batch_no AS pigBatchNo, b.breed AS batchBreed, "
            + "f.farm_name AS farmName "
            + "FROM vaccine_record v "
            + "JOIN pig_individual i ON i.id = v.pig_id AND i.deleted = 0 "
            + "LEFT JOIN pig_batch b ON b.id = i.batch_id "
            + "LEFT JOIN farm f ON f.id = i.farm_id "
            + "<where>"
            + "<if test='earTagNo != null and earTagNo != \"\"'> AND i.ear_tag_no = #{earTagNo}</if>"
            + "<if test='farmId != null'> AND i.farm_id = #{farmId}</if>"
            + "<if test='!includeVoid'> AND v.void_flag = 0</if>"
            + "</where>"
            + " ORDER BY v.inject_time DESC, v.id DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> selectGroupRows(@Param("earTagNo") String earTagNo,
                                              @Param("includeVoid") boolean includeVoid,
                                              @Param("farmId") Long farmId,
                                              @Param("limit") int limit);

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