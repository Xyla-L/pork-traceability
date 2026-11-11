package com.pork.blockchain.adapter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

public interface BlockchainAdapter {
    TxResult storeOnChain(String bizType, String bizKey, String contentHash, Map<String, Object> payload);
    Optional<ChainData> queryOnChain(String bizKey);

    /**
     * 存证载体：{@code local}（平台数据库账本，未写入任何区块链）或 {@code evm}（真实写入 EVM 兼容链）。
     * <p>
     * 这个值必须一路传到页面：本地账本算出的 txHash 只是内容指纹，既不能链上查、也不能给第三方核验，
     * 展示层把它说成「区块链已验证」就是虚假陈述。
     */
    default String mode() { return "local"; }

    default Verification verifyHash(String bizKey, String localHash) {
        Optional<ChainData> chain = queryOnChain(bizKey);
        return new Verification(chain.isPresent() && chain.get().contentHash().equalsIgnoreCase(localHash),
                localHash, chain.map(ChainData::contentHash).orElse(null));
    }

    record TxResult(String txHash, long blockNumber, LocalDateTime chainTime) { }
    record ChainData(String bizKey, String contentHash, String txHash, long blockNumber, LocalDateTime chainTime) { }
    record Verification(boolean matched, String localHash, String onChainHash) { }
}
