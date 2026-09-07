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
}