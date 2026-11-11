package com.pork.breeding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.breeding.entity.SlaughterApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SlaughterApplyMapper extends BaseMapper<SlaughterApply> {

    /**
     * 分组视图取数：出栏申报连同生猪、批次、养殖场一次 join 出来。
     * <p>
     * 申报表只有 {@code pig_id}——现实里一份申报单含多头（还带逐头体重），
     * 本系统存量是「一单一头」，所以分组按「批次 + 申报日期」归，
     * 把同一批同一天提交的申报收成一次出栏动作，逐头体重留在明细里。
     */
    @Select("<script>"
            + "SELECT s.id AS id, s.pig_id AS pigId, s.apply_no AS applyNo, s.apply_time AS applyTime, "
            + "s.weight_kg AS weightKg, s.target_slaughterhouse AS targetSlaughterhouse, "
            + "s.approval_status AS approvalStatus, s.approval_time AS approvalTime, "
            + "s.approver AS approver, s.reject_reason AS rejectReason, "
            + "s.source AS source, s.source_ref AS sourceRef, s.create_time AS createTime, "
            + "i.ear_tag_no AS earTagNo, i.breed AS pigBreed, i.farm_id AS farmId, "
            + "i.batch_id AS batchId, b.batch_no AS pigBatchNo, b.breed AS batchBreed, "
            + "f.farm_name AS farmName "
            + "FROM slaughter_apply s "
            + "JOIN pig_individual i ON i.id = s.pig_id AND i.deleted = 0 "
            + "LEFT JOIN pig_batch b ON b.id = i.batch_id "
            + "LEFT JOIN farm f ON f.id = i.farm_id "
            + "<where>"
            + "<if test='approvalStatus != null'> AND s.approval_status = #{approvalStatus}</if>"
            + "<if test='farmId != null'> AND i.farm_id = #{farmId}</if>"
            + "</where>"
            + " ORDER BY s.apply_time DESC, s.id DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> selectGroupRows(@Param("approvalStatus") Integer approvalStatus,
                                              @Param("farmId") Long farmId,
                                              @Param("limit") int limit);
}
