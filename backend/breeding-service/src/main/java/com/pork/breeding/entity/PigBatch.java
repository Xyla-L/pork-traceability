package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生猪批次档案实体类，对应数据库表 pig_batch。
 *
 * <p>批次是养殖端档案的登记单位：现实中商品猪按批次管理（同场、同批进、同批出），
 * 逐头建档既做不到也没人做。耳标起止号圈定批次成员区间，
 * 区间内的每头猪登记时自动展开成 pig_individual 成员行——
 * 个体行不再由人工逐头录入，下游（入场查验、检疫证、盖章、白条归批）照旧按耳标逐头关联。
 */
@Data
@TableName("pig_batch")
public class PigBatch {

    /** 主键ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号，同一养殖场内唯一 */
    private String batchNo;

    /** 所属养殖场ID，关联 farm 表 */
    private Long farmId;

    /** 品种：长白猪/大白猪/杜洛克/三元杂等 */
    private String breed;

    /** 进场日期（外购为进场日，自繁为转育肥日），批次级登记事实 */
    private LocalDate entryDate;

    /**
     * 批次出生日期（同批共用）。
     * <p>
     * 只由外部养殖系统提供 —— 人工登记时填不出这个日期（产房记录不在登记人手上），
     * 留空是对的。日龄由它现算（今天 − 出生日期），不单独存日龄：存下来第二天就假了。
     */
    private LocalDate birthDate;

    /** 批次成员耳标起号 */
    private String earTagStart;

    /** 批次成员耳标止号 */
    private String earTagEnd;

    /** 批次数量（= 耳标区间大小，登记时按区间自动算出） */
    private Integer quantity;

    /** 来源：自繁/外购-供应商名 */
    private String source;

    /**
     * 外部系统标识（如 FARM_ERP 或具体养殖 SaaS 代号）。NULL = 本系统人工登记。
     * <p>
     * 与 {@link #externalId} 一起构成外部系统的幂等键：两家 ERP 都叫 {@code PB-001} 是常态，
     * 只按批次号认一定会串场。
     */
    private String externalSystem;

    /** 外部系统内的批次主键（回推认领、重复推送、撤销都靠它定位） */
    private String externalId;

    /** 数据来源：MANUAL=人工登记 / API=外部养殖系统同步（不改数据内容，只标录入方式） */
    private String dataSource;

    /** 来源说明（登记操作来源备注） */
    private String sourceRef;

    /** 设备原始报文留档（批次档暂无设备通道，预留） */
    private String rawPayload;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 作废标记：0=有效 1=已作废（同时是 MyBatis-Plus 逻辑删除标记） */
    @TableLogic(value = "0", delval = "1")
    private Integer voidFlag;
    /** 作废理由（必填），随作废事件一并上链 */
    private String voidReason;
    /** 作废操作人（实名） */
    private String voidOperator;
    /** 作废来源 MANUAL=人工作废 / EXTERNAL=源系统撤销（都上链，责任方不同） */
    private String voidSource;
    /** 作废时间 */
    private LocalDateTime voidTime;
}
