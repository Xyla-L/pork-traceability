package com.pork.slaughter.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CarcassStampDTO {

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
}