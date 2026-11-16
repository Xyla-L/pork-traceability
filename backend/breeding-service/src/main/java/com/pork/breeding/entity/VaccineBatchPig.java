package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 免疫批次关联生猪：一次免疫作业覆盖了哪几头。耳标号冗余存一份，成员列表不必再 join 猪档案。 */
@Data
@TableName("vaccine_batch_pig")
public class VaccineBatchPig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long pigId;
    private String earTagNo;
    /** 该头在本次免疫中的记录是否作废（与 vaccine_record.void_flag 同步） */
    private Integer voidFlag;
    private LocalDateTime createTime;
}
