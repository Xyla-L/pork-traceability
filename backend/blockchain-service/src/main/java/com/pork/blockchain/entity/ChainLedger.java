package com.pork.blockchain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blockchain_ledger")
public class ChainLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizKey;
    private String contentHash;
    private String txHash;
    private Long blockNumber;
    private LocalDateTime chainTime;
}
