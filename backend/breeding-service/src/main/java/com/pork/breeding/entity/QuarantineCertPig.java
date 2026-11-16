package com.pork.breeding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检疫证关联生猪：一张产地检疫证覆盖多头。
 * <p>
 * 现实里产地检疫证是「按批出证」——证面有一栏「数量」，一张证对应一车/一批猪。
 * 逐头存储时同一张证会被拆成 N 条记录，证号也就跟着变成 N 个，核对装车时反而对不上。
 * 单据化后证号在 {@link QuarantineCertificate} 上唯一，这里只回答「这张证覆盖了哪几头」。
 */
@Data
@TableName("quarantine_cert_pig")
public class QuarantineCertPig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long certId;
    private Long pigId;
    private String earTagNo;
    private LocalDateTime createTime;
}
