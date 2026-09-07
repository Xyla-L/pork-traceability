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
    private LocalDateTime createTime;
}