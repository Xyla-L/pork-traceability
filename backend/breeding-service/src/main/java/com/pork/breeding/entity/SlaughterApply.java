package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("slaughter_apply")
public class SlaughterApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pigId;
    private String applyNo;
    private LocalDateTime applyTime;
    private Double weightKg;
    private String targetSlaughterhouse;
    private Integer approvalStatus;
    private LocalDateTime approvalTime;
    private String approver;
    private String rejectReason;
    private LocalDateTime createTime;

    /** 数据来源：MANUAL 人工申报 / DEVICE 出栏口地磅自动登记 */
    private String source;
    /** 来源标识：设备采集时为设备编号（如 EXITSCALE-001），人工录入时为操作人实名 */
    private String sourceRef;
    /** 设备原始报文，人工复核时逐字段核对用 */
    private String rawPayload;
}