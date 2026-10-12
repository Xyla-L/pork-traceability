package com.pork.distribution.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.distribution.entity.SplitBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
@Mapper
public interface SplitBatchMapper extends BaseMapper<SplitBatch> {

    /**
     * 作废指定分割批次：写入理由与实名操作人。
     * 批次号是下游门店与追溯查询的引用键，删掉记录会让引用悬空，因此只允许作废。
     *
     * @return 受影响行数；0 表示批次不存在或已被作废
     */
    @Update("UPDATE split_batch SET void_flag = 1, void_reason = #{reason}, "
            + "void_operator = #{operator}, void_time = NOW() WHERE id = #{id} AND void_flag = 0")
    int voidById(@Param("id") Long id, @Param("reason") String reason, @Param("operator") String operator);
}
