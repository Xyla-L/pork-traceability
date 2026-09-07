// com.pork.breeding.entity.PigIndividual
package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生猪个体档案实体类
 * 对应数据库表：pig_individual
 *
 * 业务说明：记录每一头生猪的唯一身份信息，是整个追溯体系的源头数据。
 */
@Data
@TableName("pig_individual")
public class PigIndividual {

    /**
     * 主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 耳标号（RFID电子耳标编码），业务主键，全局唯一
     */
    private String earTagNo;

    /**
     * 所属养殖场ID，关联 farm 表
     */
    private Long farmId;

    /**
     * 品种：长白猪/大白猪/杜洛克/二元杂/三元杂
     */
    private String breed;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 圈舍编号
     */
    private String penNo;

    /**
     * 来源：自繁/外购-供应商名
     */
    private String source;

    /**
     * 状态：1在养、2已出栏、3已屠宰、4异常死亡/淘汰
     */
    private Integer status;

    /**
     * 逻辑删除标记 (0-未删除, 1-已删除)
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}