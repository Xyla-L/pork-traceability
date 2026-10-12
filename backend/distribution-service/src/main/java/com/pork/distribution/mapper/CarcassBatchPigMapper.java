package com.pork.distribution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.distribution.entity.CarcassBatchPig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 胴体批次关联生猪。
 * <p>
 * 写入时不要吞掉唯一键冲突：{@code uk_pig} 抛出的重复键就是「这头猪已经归属别的批次」这个业务事实，
 * 调用方要么据此回既有批次，要么让事务回滚重来——绝不能当作成功继续往下写。
 */
@Mapper
public interface CarcassBatchPigMapper extends BaseMapper<CarcassBatchPig> {
}
