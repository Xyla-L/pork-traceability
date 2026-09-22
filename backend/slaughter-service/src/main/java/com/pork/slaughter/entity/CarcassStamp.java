package com.pork.slaughter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("carcass_stamp")
public class CarcassStamp {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联猪只ID
     */
    private Long pigId;

    private String batchNo;

    private String earTagNo;

    private String carcassNo;

    /**
     * 印章编号
     */
    private String stampNo;

    private String stampType;

    /**
     * 盖章时间
     */
    private LocalDateTime stampTime;

    /**
     * 盖章位置
     */
    private String stampPosition;

    /**
     * 兽医/操作人员
     */
    private String veterinary;

    /**
     * 电子签名
     */
    private String eSignature;

    private String contentHash;

    private Integer status;

    private String remark;

    /**
     * 数据来源：MANUAL=人工录入 DEVICE=设备自动采集（自动盖章机）
     */
    private String source;

    /**
     * 来源设备号/单据号
     */
    private String sourceRef;

    /**
     * 设备原始报文留档
     */
    private String rawPayload;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
