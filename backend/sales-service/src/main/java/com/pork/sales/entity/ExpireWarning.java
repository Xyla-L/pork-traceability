package com.pork.sales.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("expire_warning")
public class ExpireWarning {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long saleId;
    private Integer warningLevel;
    private LocalDateTime warningTime;
    private String notifyChannel;
    private Integer notified;
    private Integer handled;
    private LocalDateTime handleTime;
    private String handler;
    private LocalDateTime createTime;
}
