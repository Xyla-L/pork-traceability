package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 胴体批次 ↔ 生猪 关联表。
 *
 * <h3>为什么要有这张表</h3>
 * 「一头猪只能归属一个批次」原先靠应用层遍历所有批次的 {@code pig_ids} JSON 数组判断，两个问题：
 * <ul>
 *   <li><b>有竞态</b>——两条分拣线同时上报同一头白条时，两个请求都能通过检查再各自入批；
 *       唯一键 {@code uk_pig(pig_id)} 才是这条规则的最终防线，应用层那次查询只算快速路径。</li>
 *   <li><b>慢</b>——判断一次占用要全表扫 + 逐个解析 JSON 数组，批次一多就不可用。</li>
 * </ul>
 *
 * <h3>与 {@code carcass_batch.pig_ids} 的关系</h3>
 * 关联表是权威；{@code carcass_batch.pig_ids} / {@code pig_count} / {@code total_weight_kg}
 * 是由同一条原子 SQL 一并维护的展示快照（前端与追溯查询在读）。
 * 两者在同一个事务里写入，不会只看得到一个。
 */
@Data
@TableName("carcass_batch_pig")
public class CarcassBatchPig {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属胴体批次 carcass_batch.id */
    private Long batchId;
    /** 生猪档案 pig_individual.id；本列唯一，即「一头猪只归一批」 */
    private Long pigId;
    /** 该头胴体过磅重量(kg)，与批次总重量同源 */
    private BigDecimal weightKg;
    /** 归入批次的时间 */
    private LocalDateTime createTime;
}
