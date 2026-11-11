package com.pork.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 召回到店扫码收货事实：司机手持扫码枪在门店扫的每一枪。
 * <p>
 * 与 {@link ReturnLoadScan} 同一个理由：<b>现场事实先落库，判定交给业务层</b>。
 * 货码对不上本店召回货也照留原文（status=1），让现场知道「这一枪为什么没被认」。
 */
@Data
@TableName("recall_collect_scan")
public class RecallCollectScan {

    /** 已收货：货码命中本店召回货，行已翻「已收上车」 */
    public static final int ST_OK = 0;
    /** 异常待人工：货码对不上本店召回货（扫错店、标签不对、不是召回货） */
    public static final int ST_ABNORMAL = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scanCode;

    private Long transportId;

    private Long stopId;

    private Long collectId;

    private String plateNo;

    private String deviceNo;

    private LocalDateTime scanTime;

    /** 0=已收货 1=异常待人工 */
    private Integer status;

    private String note;

    private LocalDateTime createTime;
}
