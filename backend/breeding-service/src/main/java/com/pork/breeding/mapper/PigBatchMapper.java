package com.pork.breeding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.breeding.entity.PigBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PigBatchMapper extends BaseMapper<PigBatch> {

    /**
     * 作废批次：写入理由、实名操作人、作废来源。成员行由调用方一并作废。
     * <p>
     * {@code void_source} 明确写 MANUAL：这条 SQL 只有人工作废会走（外部系统撤销走
     * {@link #voidByExternal}），两者在追溯里要能分清楚是谁的决定。
     *
     * @return 受影响行数；0 表示批次不存在或已被作废
     */
    @Update("UPDATE pig_batch SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_source = 'MANUAL', void_time = NOW() "
            + "WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 源系统撤销（外部养殖 ERP）：来源标 EXTERNAL，理由与系统代号一起留档。
     * <p>
     * 与 {@link #voidById} 的差别只在来源标记 —— 数据结果一致（作废 + 成员一并作废），
     * 但「谁撤的」不同：一个是本系统的人，一个是外部系统，事后追责不能混。
     */
    @Update("UPDATE pig_batch SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_source = 'EXTERNAL', void_time = NOW() "
            + "WHERE id = #{id} AND void_flag = 0")
    int voidByExternal(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 按外部身份查批次，<b>包含已作废的</b>。
     * <p>
     * 必须绕过 {@code @TableLogic}：外部系统重推一条已被撤销的批次时，如果查不到就会
     * 当成新批次重建，历史作废记录瞬间被覆盖 —— 那等于用一次重推抹掉了撤销的痕迹。
     */
    @Select("SELECT id, batch_no AS batchNo, farm_id AS farmId, breed, entry_date AS entryDate, "
            + "birth_date AS birthDate, ear_tag_start AS earTagStart, ear_tag_end AS earTagEnd, quantity, source, "
            + "external_system AS externalSystem, external_id AS externalId, data_source AS dataSource, "
            + "void_flag AS voidFlag, void_reason AS voidReason, void_operator AS voidOperator, "
            + "void_source AS voidSource, void_time AS voidTime, create_time AS createTime "
            + "FROM pig_batch WHERE external_system = #{system} AND external_id = #{externalId} LIMIT 1")
    PigBatch selectByExternal(@Param("system") String system, @Param("externalId") String externalId);

    /**
     * 按「场 + 批次号」查批次，<b>包含已作废的</b>（唯一键 uk_batch 也是这么算的）。
     * <p>
     * 用途是给人一句说得清的话：外部系统推来的批次号如果与人工登记的重了，
     * 要转人工核对而不是硬插（硬插会撞唯一键，报一个数据库错误给外部系统看，毫无意义）。
     */
    @Select("SELECT id, batch_no AS batchNo, farm_id AS farmId, external_system AS externalSystem, "
            + "external_id AS externalId, void_flag AS voidFlag "
            + "FROM pig_batch WHERE farm_id = #{farmId} AND batch_no = #{batchNo} LIMIT 1")
    PigBatch selectAnyByBatchNo(@Param("farmId") Long farmId, @Param("batchNo") String batchNo);
}
