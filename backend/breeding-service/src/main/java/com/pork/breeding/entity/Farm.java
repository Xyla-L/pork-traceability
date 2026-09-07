package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("farm")
public class Farm {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String farmName;
    private String licenseNo;
    private String address;
    private String contactPerson;
    private String contactPhone;
    private Integer scale;
    private Integer status;
    private LocalDateTime createTime;
}