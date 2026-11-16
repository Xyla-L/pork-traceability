package com.pork.blockchain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    /**
     * 上链报文快照（JSON）。
     * <p>
     * 重试与历史迁移要重放「当初那条报文」，没有快照就只能往链上写空 payload——
     * 那等于只保住一个数字，链上存证失去可读内容。历史记录（加列之前写入的）为 null。
     * 不对外序列化：列表接口按页返回，报文正文只走 verifyView 的 payloadAvailable 披露是否有。
     */
    @JsonIgnore
    private String payloadJson;
    private String txHash;
    private Long blockNumber;
    private LocalDateTime chainTime;
    private Integer status;
    /** 存证载体：local=平台本地账本（未上链） evm=区块链 */
    private String chainMode;
    /** 历史本地存证补写链上的时间（实时写入的记录为 null） */
    private LocalDateTime migratedAt;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
