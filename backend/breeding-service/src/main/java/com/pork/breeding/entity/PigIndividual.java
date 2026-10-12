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
     * 性别：1公 2母
     */
    private Integer gender;

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
     * 数据来源：MANUAL=人工录入 DEVICE=设备自动采集（注意与上面的 source「自繁/外购」区分，
     * 那是业务上的猪只来源，这个是审计上的录入方式）
     */
    private String dataSource;

    /**
     * 来源设备号/单据号（data_source=DEVICE 时填设备编号）
     */
    private String sourceRef;

    /**
     * 设备原始报文留档
     */
    private String rawPayload;

    /**
     * 历史逻辑删除标记。已由 void_flag 统一承接「不再生效」语义（见 16-void-and-fallback.sql），
     * 本列保留仅为兼容历史数据，新代码一律使用 voidFlag。
     */
    private Integer deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /** 作废标记：0=有效 1=已作废（同时是 MyBatis-Plus 逻辑删除标记，查询默认只取有效记录） */
    @TableLogic(value = "0", delval = "1")
    private Integer voidFlag;
    /** 作废理由（必填），随作废事件一并上链 */
    private String voidReason;
    /** 作废操作人（实名） */
    private String voidOperator;
    /** 作废时间 */
    private LocalDateTime voidTime;
}