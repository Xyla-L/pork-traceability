package com.pork.ingest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 接入设备台账（db_ingest.ingest_device） */
@Data
@TableName("ingest_device")
public class IngestDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 设备编号（设备侧唯一标识） */
    private String deviceNo;
    private String deviceName;
    /** 接入通道 TEMPERATURE/ENTRY/RACTOPAMINE/SALE/RECEIPT */
    private String channel;
    private String location;
    /** 设备密钥（请求头 X-Device-Key） */
    private String secretKey;
    /** 1启用 0停用 */
    private Integer status;
    private LocalDateTime lastSeenTime;
    private String remark;
    private LocalDateTime createTime;
}
