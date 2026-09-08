package com.pork.blockchain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blockchain_record")
public class BlockchainRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String bizType;
    private Long bizId;
    private String bizKey;
    private String contentHash;
    private String txHash;
    private Long blockNumber;
    private LocalDateTime chainTime;
    private Integer status;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
