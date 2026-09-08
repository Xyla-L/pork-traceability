package com.pork.blockchain.adapter.local;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.blockchain.adapter.BlockchainAdapter;
import com.pork.blockchain.entity.ChainLedger;
import com.pork.blockchain.mapper.ChainLedgerMapper;
import com.pork.core.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "blockchain.type", havingValue = "local", matchIfMissing = true)
public class DatabaseLedgerAdapter implements BlockchainAdapter {
    private final ChainLedgerMapper mapper;

    @Override
    public TxResult storeOnChain(String bizKey, String contentHash, Map<String, Object> payload) {
        ChainLedger existing = mapper.selectOne(Wrappers.<ChainLedger>lambdaQuery()
                .eq(ChainLedger::getBizKey, bizKey)
                .eq(ChainLedger::getContentHash, contentHash)
                .orderByDesc(ChainLedger::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return new TxResult(existing.getTxHash(), existing.getBlockNumber(), existing.getChainTime());
        }
        ChainLedger row = new ChainLedger();
        row.setBizKey(bizKey);
        row.setContentHash(contentHash);
        row.setChainTime(LocalDateTime.now());
        row.setBlockNumber(mapper.selectCount(null) + 1);
        row.setTxHash("0x" + HashUtil.sha256(bizKey + ':' + contentHash + ':' + row.getBlockNumber()));
        mapper.insert(row);
        return new TxResult(row.getTxHash(), row.getBlockNumber(), row.getChainTime());
    }

    @Override
    public Optional<ChainData> queryOnChain(String bizKey) {
        ChainLedger row = mapper.selectOne(Wrappers.<ChainLedger>lambdaQuery()
                .eq(ChainLedger::getBizKey, bizKey)
                .orderByDesc(ChainLedger::getId)
                .last("LIMIT 1"));
        return Optional.ofNullable(row).map(v -> new ChainData(v.getBizKey(), v.getContentHash(), v.getTxHash(),
                v.getBlockNumber(), v.getChainTime()));
    }
}
