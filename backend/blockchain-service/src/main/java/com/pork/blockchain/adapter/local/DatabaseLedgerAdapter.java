package com.pork.blockchain.adapter.local;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.blockchain.adapter.BlockchainAdapter;
import com.pork.blockchain.entity.ChainLedger;
import com.pork.blockchain.mapper.ChainLedgerMapper;
import com.pork.core.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "blockchain.type", havingValue = "local", matchIfMissing = true)
public class DatabaseLedgerAdapter implements BlockchainAdapter {
    private final ChainLedgerMapper mapper;

    @Override
    public TxResult storeOnChain(String bizType, String bizKey, String contentHash, Map<String, Object> payload) {
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

    /**
     * 本地账本同样允许一个 bizKey 有多行（一车多店签收 = 同车号 3 行）。
     * 按 (bizKey, contentHash) 找存不存在，而不是只比最新一行——
     * 后者会把历史版本的好记录判成「被改过」。
     */
    @Override
    public Verification verifyHash(String bizKey, String localHash) {
        if (!StringUtils.hasText(localHash)) return new Verification(false, localHash, null);
        ChainLedger hit = mapper.selectOne(Wrappers.<ChainLedger>lambdaQuery()
                .eq(ChainLedger::getBizKey, bizKey)
                .eq(ChainLedger::getContentHash, localHash)
                .orderByDesc(ChainLedger::getId)
                .last("LIMIT 1"));
        if (hit != null) return new Verification(true, localHash, hit.getContentHash());
        return new Verification(false, localHash,
                queryOnChain(bizKey).map(ChainData::contentHash).orElse(null));
    }
}
