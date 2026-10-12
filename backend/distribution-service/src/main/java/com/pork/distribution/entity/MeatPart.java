package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 分割部位字典：行业标准部位清单。
 * <p>
 * 设备上报只认 {@link #partCode}；只报了名称时按 {@link #partName} / {@link #aliases} 反查，
 * 反查不到一律转人工 —— 部位是追溯的关键字段（门店召回就是按部位圈范围的），宁缺勿猜。
 * <p>
 * {@link #category} 决定出肉率对账口径：各部位全部为红肉时按红肉比例判，
 * 出现骨/脂/皮/副产时按总量判，整条出货（{@code WHOLE}）则不判下限。
 * {@link #required} 标记必出部位，缺项即告警。
 */
@Data
@TableName("meat_part")
public class MeatPart {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部位编码，设备上报只认这个 */
    private String partCode;
    /** 标准部位名（展示用） */
    private String partName;
    /** 兼容叫法，逗号分隔；设备只报名称时按此反查 */
    private String aliases;
    /** 所属段：FRONT前段 / MIDDLE中段 / REAR后段 / WHOLE整条 */
    private String segment;
    /** 品类：LEAN红肉 / BONE骨 / FAT脂 / SKIN皮 / BYPRODUCT副产 / WHOLE整条未细分 */
    private String category;
    /** 是否必出部位：1=该出的没报就对账告警 */
    private Integer required;
    private Integer sortNo;
    private Integer enabled;
    private String note;
}
