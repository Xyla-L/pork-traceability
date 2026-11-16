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

    /**
     * 验真：判断某条记录的 contentHash 是否真的写进过这个 bizKey 的存证历史。
     * <p>
     * <b>bizKey 不是记录唯一键</b>——一车多店签收、同批多次检验都会在同一个 bizKey 下追加版本，
     * 而调用方传进来的 localHash 属于某一条具体记录（bizId）。所以实现必须比对<b>整段历史</b>，
     * 只比最新版会把「数据一个字没改」的老版本记录全部判成不一致（本项目实测 143 条误报）。
     * <p>
     * 返回值里的 onChainHash 只做展示参考：匹配时给命中的那一版，不匹配时给最新一版。
     */
    default Verification verifyHash(String bizKey, String localHash) {
        Optional<ChainData> chain = queryOnChain(bizKey);
        return new Verification(chain.isPresent() && chain.get().contentHash().equalsIgnoreCase(localHash),
                localHash, chain.map(ChainData::contentHash).orElse(null));
    }

    record TxResult(String txHash, long blockNumber, LocalDateTime chainTime) { }
    record ChainData(String bizKey, String contentHash, String txHash, long blockNumber, LocalDateTime chainTime) { }

    /**
     * 验真结果。
     *
     * @param chainPayload 链上那一版的原始报文（只有能在链上读到报文时才给）：调用方用它核对
     *                     「本地留存的原文」是否就是当初上链的那一份——比只比哈希更能说明问题
     */
    record Verification(boolean matched, String localHash, String onChainHash, String chainPayload) {
        public Verification(boolean matched, String localHash, String onChainHash) {
            this(matched, localHash, onChainHash, null);
        }
    }
}
