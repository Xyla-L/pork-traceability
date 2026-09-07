package com.pork.slaughter.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("entry_inspection")
public class EntryInspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pigId;
    private LocalDateTime arriveTime;
    private String vehicleNo;
    private Integer healthCheck; // 1通过 0异常
    private Integer certVerified; // 1通过 0异常
    private String abnormalNote;
    private String inspector;
    private String fileIds; // JSON字符串存储
    private LocalDateTime createTime;
}