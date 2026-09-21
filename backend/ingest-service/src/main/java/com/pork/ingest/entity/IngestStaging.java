package com.pork.ingest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 设备上报待确认队列（db_ingest.ingest_staging） */
@Data
@TableName("ingest_staging")
public class IngestStaging {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String channel;
    private String deviceNo;
    /** 设备侧业务唯一号（幂等键，与 deviceNo 组成唯一约束） */
    private String bizKey;
    /** 设备侧数据时间（用于乱序/补传场景判断） */
    private LocalDateTime reportTime;
    /** 接入层接收时间 */
    private LocalDateTime receiveTime;
    /** 原始报文（全量留档，不可变） */
    private String payload;
    private String sourceRef;
    /** 0待处理 1待人工处理 2已入账 3已拒绝 4已降采样丢弃 */
    private Integer status;
    private String targetTable;
    private Long targetId;
    private String errorMsg;
    private LocalDateTime handleTime;
    private String handler;
    private String remark;
    private LocalDateTime createTime;
}
