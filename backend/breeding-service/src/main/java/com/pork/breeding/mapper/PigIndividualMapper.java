package com.pork.breeding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.breeding.entity.PigIndividual;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 生猪个体数据访问层 (Mapper)
 *
 * 负责与数据库 pig_individual 表进行交互。
 * 通过继承 BaseMapper，自动拥有了 insert, delete, update, select 等基础方法。
 */
@Mapper
public interface PigIndividualMapper extends BaseMapper<PigIndividual> {
    // 如果有复杂的自定义SQL查询，可以在这里定义方法，并在对应的XML文件中实现

    /**
     * 作废指定档案：写入理由与实名操作人。
     * 逻辑删除只会把标记翻成 1，写不下「为什么作废、谁作的废」，而这两项恰恰是追溯最需要的。
     *
     * @return 受影响行数；0 表示档案不存在或已被作废
     */
    @Update("UPDATE pig_individual SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_time = NOW() WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 查已作废的档案（仅供追溯侧标注使用）。
     * <p>
     * 归档档案是追溯链的起点，被作废后 {@code @TableLogic} 会让它在常规查询里彻底消失——
     * 追溯必须能回答「这头猪的档案后来为什么不算数了」，所以留一个显式绕过逻辑删除的只读入口。
     * 返回 null 表示该 id 既没有有效档案、也没有作废档案。
     */
    @Select("SELECT id, ear_tag_no AS earTagNo, farm_id AS farmId, breed, birth_date AS birthDate, "
            + "gender, pen_no AS penNo, source, status, data_source AS dataSource, source_ref AS sourceRef, "
            + "void_reason AS voidReason, void_operator AS voidOperator, void_time AS voidTime, "
            + "create_time AS createTime, update_time AS updateTime "
            + "FROM pig_individual WHERE id = #{id} AND void_flag = 1")
    PigIndividual selectVoidedById(@Param("id") Long id);

    /**
     * 按耳标号查已作废档案（只读）。
     * <p>
     * 给下游一个能说清楚话的拒绝理由：耳标扫出来在库里「查不到」和「档案已作废」，
     * 对现场处置完全是两回事——前者要重新建档，后者得先弄清为什么被作废。
     */
    @Select("SELECT id, ear_tag_no AS earTagNo, void_reason AS voidReason, "
            + "void_operator AS voidOperator, void_time AS voidTime "
            + "FROM pig_individual WHERE ear_tag_no = #{earTagNo} AND void_flag = 1 LIMIT 1")
    PigIndividual selectVoidedByEarTag(@Param("earTagNo") String earTagNo);

    /**
     * 整批作废批次成员行：批次档案作废时，区间展开出的成员行一并失效。
     * 成员行由批次生成，去留跟着批次走——批次不算数了，成员也就不算数。
     *
     * @return 本次作废的成员行数
     */
    @Update("UPDATE pig_individual SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_time = NOW() "
            + "WHERE batch_id = #{batchId} AND void_flag = 0")
    int voidByBatchId(@Param("batchId") Long batchId, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 批次成员状态分布（只读）：批次详情页按状态汇总成员，不逐头展示。
     */
    @Select("SELECT status, COUNT(*) AS cnt FROM pig_individual "
            + "WHERE batch_id = #{batchId} AND void_flag = 0 GROUP BY status")
    List<Map<String, Object>> statusCountsByBatch(@Param("batchId") Long batchId);

    /**
     * 品种字典的「库内来源」：已经出现过的品种。
     * <p>
     * 为什么需要它：设备通道上报的品种不受前端枚举约束，若下拉只靠硬编码，
     * 就会出现「列表里能看到、搜索下拉里选不到」的错位（如设备上报的「三元杂」）。
     * 这里显式带 {@code void_flag = 0}，因为原生 SQL 绕过了 {@code @TableLogic} 的自动过滤。
     */
    @Select("SELECT DISTINCT breed FROM pig_individual "
            + "WHERE breed IS NOT NULL AND breed <> '' AND void_flag = 0 ORDER BY breed")
    List<String> selectDistinctBreeds();

    /**
     * 批次成员的实时摘要（只读）：有效成员数 + 耳标最小/最大号。
     * <p>
     * 外部系统同步时成员会变（ERP 补推、拆分合并），批次行上的 {@code quantity} 与
     * {@code ear_tag_start/end} 如果只在创建时写一次，列表页就会一直显示登记那天的数字 ——
     * 「30 头」下面挂着 32 条成员，是对不上的。这里以成员行为准重算。
     * <p>
     * 用 SQL 的 MIN/MAX 而不是在 Java 里排：结果集可能上千行，排序没有意义，范围才是要的。
     */
    @Select("SELECT COUNT(*) AS cnt, MIN(ear_tag_no) AS minTag, MAX(ear_tag_no) AS maxTag "
            + "FROM pig_individual WHERE batch_id = #{batchId} AND void_flag = 0 AND deleted = 0")
    Map<String, Object> batchMemberSummary(@Param("batchId") Long batchId);

    /**
     * 按耳标批量查「是否已被占用」，<b>包含已作废、已逻辑删除的行</b>。
     * <p>
     * 外部养殖系统同步时必须先问这一句：{@code uk_ear_tag} 是全表唯一（连作废行一起算），
     * 所以「看起来没档案」的耳标插进去会直接撞唯一键。冲突要让外部系统看到一句人话
     * （哪几个耳标已经被别的批次占了），而不是一个 Duplicate entry 异常。
     * <p>
     * 绕过逻辑删除是刻意的：一头被作废过的猪仍然占着这个耳标号，谁来也补不进去。
     */
    @Select("<script>SELECT ear_tag_no AS earTagNo, batch_id AS batchId, void_flag AS voidFlag, deleted "
            + "FROM pig_individual WHERE ear_tag_no IN "
            + "<foreach item='tag' collection='tags' open='(' separator=',' close=')'>#{tag}</foreach>"
            + " LIMIT #{limit}</script>")
    List<PigIndividual> selectAnyByEarTags(@Param("tags") List<String> tags, @Param("limit") int limit);
}