package com.pork.blockchain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigInteger;

/**
 * EVM 兼容链配置：本地开发用 Ganache（免费开源），公网可用 Sepolia 等测试网（免费 RPC + 水龙头）。
 */
@Data
@ConfigurationProperties(prefix = "blockchain.evm")
public class EvmChainProperties {
    /** JSON-RPC 地址，如 http://localhost:8545 或 https://rpc.sepolia.org */
    private String rpcUrl = "http://localhost:8545";
    /** 链 ID：Ganache 默认 1337，Sepolia 为 11155111 */
    private long chainId = 1337;
    /** 上链账户私钥（0x 前缀）。开发环境可用 Ganache 确定性账户，生产环境务必用环境变量注入 */
    private String privateKey;
    /** 已部署的 PorkTraceability 合约地址；留空则启动时用内置字节码自动部署 */
    private String contractAddress;
    /** storeEvidence 交易 gas 上限 */
    private BigInteger gasLimit = BigInteger.valueOf(2_000_000L);
    /** 合约部署交易 gas 上限 */
    private BigInteger deployGasLimit = BigInteger.valueOf(4_000_000L);
    /** 交易回执轮询间隔（毫秒） */
    private long receiptIntervalMs = 2_000L;
    /** 交易回执最大轮询次数（默认 30 次 ≈ 60 秒） */
    private int receiptAttempts = 30;
}
