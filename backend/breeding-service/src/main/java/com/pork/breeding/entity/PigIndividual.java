package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pig_individual")
public class PigIndividual {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("ear_tag_no")
    private String earTagNo; // 耳标号（唯一）

    @TableField("farm_id")
    private Long farmId; // 养殖场ID

    @TableField("breed")
    private String breed; // 品种

    @TableField("birth_date")
    private LocalDate birthDate; // 出生日期

    @TableField("pen_no")
    private String penNo; // 圈舍编号

    @TableField("source")
    private String source; // 来源

    @TableField("status")
    private Integer status; // 状态：1在养、2已出栏...

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}