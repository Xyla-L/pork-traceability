package com.pork.slaughter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定点屠宰场
 */
@Data
@TableName("slaughterhouse")
public class Slaughterhouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String licenseNo;
    private String address;
    private String contactPerson;
    private String contactPhone;
    private Integer dailyCapacity;
    private Integer status; // 1启用 0停用
    private String remark;
    private LocalDateTime createTime;
}
