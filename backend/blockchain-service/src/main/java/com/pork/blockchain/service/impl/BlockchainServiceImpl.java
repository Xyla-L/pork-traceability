package com.pork.blockchain.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.blockchain.adapter.BlockchainAdapter;
import com.pork.blockchain.entity.BlockchainRecord;
import com.pork.blockchain.mapper.BlockchainRecordMapper;
import com.pork.blockchain.service.BlockchainService;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.PageResult;
import com.pork.mq.ChainTxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BlockchainServiceImpl implements BlockchainService {
    private final BlockchainRecordMapper mapper;
    private final BlockchainAdapter adapter;

    @Override
    @Transactional(noRollbackFor = ChainStoreException.class)
    public void store(ChainTxMessage message) {
        BlockchainRecord record = mapper.selectOne(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getEventId, message.eventId()));
        if (record != null && record.getStatus() == 1) return;
        if (record == null) {
            record = new BlockchainRecord();
            record.setEventId(message.eventId());
            record.setBizType(message.bizType());
            record.setBizId(message.bizId());
            record.setBizKey(message.bizKey());
            record.setContentHash(message.contentHash());
            record.setStatus(0);
            record.setRetryCount(0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            mapper.insert(record);
        }
        try {
            BlockchainAdapter.TxResult tx = adapter.storeOnChain(record.getBizType(), record.getBizKey(), record.getContentHash(), message.payload());
            record.setTxHash(tx.txHash());
            record.setBlockNumber(tx.blockNumber());
            record.setChainTime(tx.chainTime());
            record.setStatus(1);
            record.setErrorMsg(null);
            record.setUpdateTime(LocalDateTime.now());
            mapper.updateById(record);
        } catch (RuntimeException ex) {
            record.setStatus(2);
            record.setRetryCount(record.getRetryCount() + 1);
            record.setErrorMsg(crop(ex.getMessage()));
            record.setUpdateTime(LocalDateTime.now());
            mapper.updateById(record);
            throw new ChainStoreException(ex);
        }
    }

    @Override
    public PageResult<BlockchainRecord> records(String bizType, Long bizId, Integer status, long pageNum, long pageSize) {
        Page<BlockchainRecord> page = mapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(StringUtils.hasText(bizType), BlockchainRecord::getBizType, bizType)
                .eq(bizId != null, BlockchainRecord::getBizId, bizId)
                .eq(status != null, BlockchainRecord::getStatus, status)
                .orderByDesc(BlockchainRecord::getCreateTime));
        return PageResult.of(page);
    }

    @Override
    public BlockchainRecord record(Long id) {
        BlockchainRecord row = mapper.selectById(id);
        if (row == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "存证记录不存在");
        return row;
    }

    @Override
    public BlockchainRecord retry(Long id) {
        BlockchainRecord row = record(id);
        if (Integer.valueOf(1).equals(row.getStatus())) return row;
        try {
            store(new ChainTxMessage(row.getEventId(), row.getBizType(), row.getBizId(), row.getBizKey(),
                    row.getContentHash(), Map.of(), row.getCreateTime()));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.BLOCKCHAIN_ERROR, "上链重试失败");
        }
        return record(id);
    }

    @Override
    public BlockchainAdapter.Verification verify(String bizType, Long bizId) {
        BlockchainRecord row = mapper.selectOne(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getBizType, bizType).eq(BlockchainRecord::getBizId, bizId)
                .orderByDesc(BlockchainRecord::getCreateTime).last("LIMIT 1"));
        if (row == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "存证记录不存在");
        return adapter.verifyHash(row.getBizKey(), row.getContentHash());
    }

    @Override
    public List<BlockchainRecord> status(String batchNo) {
        return mapper.selectList(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getBizKey, batchNo).orderByDesc(BlockchainRecord::getCreateTime));
    }

    @Override
    public Map<String, Long> stats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Long total = mapper.selectCount(null);
        Long today = mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery().ge(BlockchainRecord::getChainTime, start));
        Long pending = mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery().in(BlockchainRecord::getStatus, 0, 2));
        Long latest = mapper.selectObjs(Wrappers.<BlockchainRecord>query().select("MAX(block_number)"))
                .stream().filter(v -> v != null).findFirst().map(v -> ((Number) v).longValue()).orElse(0L);
        return Map.of("totalTx", total, "todayTx", today, "pendingTx", pending, "latestBlock", latest);
    }

    @Override
    @Scheduled(fixedDelayString = "${blockchain.retry-interval-ms:300000}")
    public void retryPending() {
        List<BlockchainRecord> rows = mapper.selectList(Wrappers.<BlockchainRecord>lambdaQuery()
                .in(BlockchainRecord::getStatus, 0, 2).lt(BlockchainRecord::getRetryCount, 3)
                .lt(BlockchainRecord::getUpdateTime, LocalDateTime.now().minusMinutes(1)).last("LIMIT 100"));
        for (BlockchainRecord row : rows) {
            try {
                store(new ChainTxMessage(row.getEventId(), row.getBizType(), row.getBizId(), row.getBizKey(),
                        row.getContentHash(), Map.of(), row.getCreateTime()));
            } catch (RuntimeException ignored) {
                // Failure state and retry counter are persisted by store().
            }
        }
    }

    private String crop(String message) {
        if (message == null) return "unknown blockchain error";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static final class ChainStoreException extends RuntimeException {
        private ChainStoreException(Throwable cause) { super(cause); }
    }
}
