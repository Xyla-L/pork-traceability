package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("vaccine_record")
public class VaccineRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pigId;
    private String vaccineName;
    private String batchNo;
    private String manufacturer;
    private LocalDateTime injectTime;
    private String dosage;
    private String injectSite;
    private String operator;
    private String fileIds; // JSON字段

    /** 数据来源：MANUAL=人工录入 DEVICE=设备自动采集 */
    private String source;
    /** 来源设备号/单据号 */
    private String sourceRef;
    /** 设备原始报文留档 */
    private String rawPayload;

    private LocalDateTime createTime;
}