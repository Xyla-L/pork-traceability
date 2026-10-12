package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "carcass_batch", autoResultMap = true)
public class CarcassBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    /**
     * 关联生猪 ID 列表。
     * <p>
     * <b>它是展示快照，不是权威</b>：「一头猪只能归属一个批次」由 {@code carcass_batch_pig.pig_id}
     * 的唯一键保证；本列与 {@link #pigCount}、{@link #totalWeightKg} 由同一条原子 SQL 一并维护
     * （见 {@code CarcassBatchMapper.appendPig}）。保留它是为了让前端与追溯查询无需改动。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> pigIds;
    /** 批次头数（与 pigIds 同步维护；归集上限判断读它，免去每次算 JSON 长度） */
    private Integer pigCount;

    /**
     * 该批次已产生的分割批次条数（0 = 还没上分割线）。
     * <p>
     * 非库表字段，列表查询时顺带补上：前端据此把默认打开的批次落到「最近一条真的有分割数据的批次」，
     * 否则默认选中最新批次（刚归集、还没分割）时，拆分树只有一个根节点，页面等于空的。
     */
    @TableField(exist = false)
    private Integer splitCount;
    /**
     * 归集状态：1=归集中 2=已封。
     * <p>
     * 批次必须有上限，否则它不是批次——真实屠宰场日屠宰量几百到几千头，
     * 「一天一场一个批次」会得到一个没有追溯区分度的巨批。已封之后分拣台不再把当日白条并入。
     * 人工建的批次直接落在「已封」（它本就不参与自动归集，只作展示口径）。
     */
    private Integer status;
    private BigDecimal totalWeightKg;
    private String slaughterhouse;
    private LocalDateTime createTime;
    private String operator;
    private String note;

    /**
     * 数据来源：MANUAL=人工建批 DEVICE=分拣台自动归集。
     * <p>
     * 归集只在同来源之间进行——人工建的批次是人的分组，设备归集不去动它，
     * 否则「系统按屠宰场 + 屠宰日归集」这件事会被人的历史分组搅乱。
     */
    private String source;
    /** 来源设备号（设备归集记分拣台编号，如 BATCH-001） */
    private String sourceRef;
    /** 设备原始报文留档 */
    private String rawPayload;

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
