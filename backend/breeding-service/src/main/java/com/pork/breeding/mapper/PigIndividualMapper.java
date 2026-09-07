package com.pork.breeding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pork.breeding.entity.PigIndividual;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生猪个体数据访问层 (Mapper)
 *
 * 负责与数据库 pig_individual 表进行交互。
 * 通过继承 BaseMapper，自动拥有了 insert, delete, update, select 等基础方法。
 */
@Mapper
public interface PigIndividualMapper extends BaseMapper<PigIndividual> {
    // 如果有复杂的自定义SQL查询，可以在这里定义方法，并在对应的XML文件中实现
}