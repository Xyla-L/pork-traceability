package com.pork.distribution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.distribution.entity.CarcassBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface CarcassBatchMapper extends BaseMapper<CarcassBatch> {

    /**
     * 当日已用掉的「CB+日期-」批号个数，<b>含已作废的</b>。
     * <p>
     * 刻意用原生 SQL 而不是 {@code selectCount}：逻辑删除会给查询自动加上 {@code void_flag=0}，
     * 于是作废过的批号被算作「没用过」而被复用——批次号是下游门店与追溯查询的引用键，不能复用。
     */
    @Select("SELECT COUNT(*) FROM carcass_batch WHERE batch_no LIKE CONCAT(#{prefix}, '%')")
    long countByBatchNoPrefix(@Param("prefix") String prefix);

    /** 批号是否已被占用（同样含已作废的，理由见上） */
    @Select("SELECT COUNT(*) FROM carcass_batch WHERE batch_no = #{batchNo}")
    long countByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 原子追加一头白条：{@code JSON_ARRAY_APPEND} + 总重量累加 + 头数加一，一条 SQL 完成。
     * <p>
     * 必须这么写，不能「读出整行 → 内存里 add → 写回整行」：后者在两条分拣线同时上报时会丢更新
     * （后写的覆盖先写的），而且批次越大每次都要重写整个数组。
     * <p>
     * 注意 {@code CAST(#{pigId} AS JSON)}：绑定参数进 MySQL 一律按字符串处理，
     * 直接塞进 {@code JSON_ARRAY_APPEND} 会得到 {@code "123"}（带引号的字符串）而不是 {@code 123}，
     * 之后所有 {@code JSON_CONTAINS} 都查不到。
     *
     * @return 受影响行数；0 表示该批次已不是「归集中」（被并发封批）
     */
    @Update("UPDATE carcass_batch SET "
            + "pig_ids = JSON_ARRAY_APPEND(COALESCE(pig_ids, JSON_ARRAY()), '$', CAST(#{pigId} AS JSON)), "
            + "total_weight_kg = COALESCE(total_weight_kg, 0) + #{weightKg}, "
            + "pig_count = pig_count + 1 "
            + "WHERE id = #{batchId} AND status = 1")
    int appendPig(@Param("batchId") Long batchId, @Param("pigId") Long pigId, @Param("weightKg") BigDecimal weightKg);

    /** 封批（人工或达到上限）：此后分拣台不再把当日白条并入本批次 */
    @Update("UPDATE carcass_batch SET status = 2 WHERE id = #{batchId} AND status = 1")
    int sealById(@Param("batchId") Long batchId);

    /**
     * 达到头数上限就自动封批。
     * 与 {@link #appendPig} 放在同一个事务里，任何一方失败一起回滚，不会出现「加了猪但没封」或反之。
     */
    @Update("UPDATE carcass_batch SET status = 2 WHERE id = #{batchId} AND status = 1 AND pig_count >= #{maxPigs}")
    int sealIfFull(@Param("batchId") Long batchId, @Param("maxPigs") int maxPigs);
}
