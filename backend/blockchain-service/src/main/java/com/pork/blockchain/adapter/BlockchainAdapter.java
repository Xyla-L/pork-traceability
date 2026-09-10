package com.pork.blockchain.adapter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

public interface BlockchainAdapter {
    TxResult storeOnChain(String bizType, String bizKey, String contentHash, Map<String, Object> payload);
    Optional<ChainData> queryOnChain(String bizKey);

    default Verification verifyHash(String bizKey, String localHash) {
        Optional<ChainData> chain = queryOnChain(bizKey);
        return new Verification(chain.isPresent() && chain.get().contentHash().equalsIgnoreCase(localHash),
                localHash, chain.map(ChainData::contentHash).orElse(null));
    }

    record TxResult(String txHash, long blockNumber, LocalDateTime chainTime) { }
    record ChainData(String bizKey, String contentHash, String txHash, long blockNumber, LocalDateTime chainTime) { }
    record Verification(boolean matched, String localHash, String onChainHash) { }
}
