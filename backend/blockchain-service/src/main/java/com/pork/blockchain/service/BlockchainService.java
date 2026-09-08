package com.pork.blockchain.service;

import com.pork.blockchain.adapter.BlockchainAdapter;
import com.pork.blockchain.entity.BlockchainRecord;
import com.pork.core.result.PageResult;
import com.pork.mq.ChainTxMessage;

import java.util.List;
import java.util.Map;

public interface BlockchainService {
    void store(ChainTxMessage message);
    PageResult<BlockchainRecord> records(String bizType, Long bizId, Integer status, long pageNum, long pageSize);
    BlockchainRecord record(Long id);
    BlockchainRecord retry(Long id);
    BlockchainAdapter.Verification verify(String bizType, Long bizId);
    List<BlockchainRecord> status(String batchNo);
    Map<String, Long> stats();
    void retryPending();
}
