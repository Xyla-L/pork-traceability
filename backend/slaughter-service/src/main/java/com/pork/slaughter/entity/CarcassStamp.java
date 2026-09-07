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

    /**
     * 印章编号
     */
    private String stampNo;

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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}