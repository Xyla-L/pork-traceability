package com.pork.blockchain.adapter.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.blockchain.adapter.BlockchainAdapter;
import com.pork.blockchain.config.EvmChainProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Uint;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 基于 web3j 的 EVM 真实上链适配器：将存证写入 PorkTraceability 合约（见 resources/contracts）。
 */
@Slf4j
@Component
@EnableConfigurationProperties(EvmChainProperties.class)
@ConditionalOnProperty(name = "blockchain.type", havingValue = "evm")
public class EvmBlockchainAdapter implements BlockchainAdapter, DisposableBean {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final EvmChainProperties props;
    private Web3j web3j;
    private Credentials credentials;
    private RawTransactionManager txManager;
    private TransactionReceiptProcessor receiptProcessor;
    private String contractAddress;

    public EvmBlockchainAdapter(EvmChainProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() throws IOException, TransactionException {
        if (!StringUtils.hasText(props.getPrivateKey())) {
            throw new IllegalStateException("blockchain.type=evm 需要配置 blockchain.evm.private-key（环境变量 EVM_PRIVATE_KEY）");
        }
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.web3j = Web3j.build(new HttpService(props.getRpcUrl(), http));
        this.credentials = Credentials.create(Numeric.cleanHexPrefix(props.getPrivateKey().trim()));
        this.txManager = new RawTransactionManager(web3j, credentials, props.getChainId());
        this.receiptProcessor = new PollingTransactionReceiptProcessor(web3j, props.getReceiptIntervalMs(), props.getReceiptAttempts());
        this.contractAddress = StringUtils.hasText(props.getContractAddress())
                ? props.getContractAddress().trim()
                : deployContract();
        log.info("EVM 上链适配器已就绪: chainId={}, account={}, contract={}",
                props.getChainId(), credentials.getAddress(), contractAddress);
    }

    private String deployContract() throws IOException, TransactionException {
        String bytecode = new String(new ClassPathResource("contracts/PorkTraceability.bin")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!bytecode.startsWith("0x")) bytecode = "0x" + bytecode;
        EthSendTransaction resp = txManager.sendTransaction(
                gasPrice(), props.getDeployGasLimit(), "", bytecode, BigInteger.ZERO);
        if (resp.hasError()) {
            throw new IllegalStateException("合约部署交易被拒绝: " + resp.getError().getMessage());
        }
        TransactionReceipt receipt = receiptProcessor.waitForTransactionReceipt(resp.getTransactionHash());
        if (!receipt.isStatusOK() || !StringUtils.hasText(receipt.getContractAddress())) {
            throw new IllegalStateException("合约部署失败: tx=" + resp.getTransactionHash());
        }
        log.warn("PorkTraceability 合约已自动部署: address={}, tx={}；建议通过 EVM_CONTRACT_ADDRESS 固定该地址",
                receipt.getContractAddress(), receipt.getTransactionHash());
        return receipt.getContractAddress();
    }

    @Override
    public TxResult storeOnChain(String bizType, String bizKey, String contentHash, Map<String, Object> payload) {
        try {
            Function function = new Function("storeEvidence",
                    Arrays.asList(new Utf8String(bizType), new Utf8String(bizKey),
                            new Utf8String(contentHash), new Utf8String(payloadJson(payload))),
                    List.of(new TypeReference<Uint256>() { }));
            EthSendTransaction resp = txManager.sendTransaction(
                    gasPrice(), props.getGasLimit(), contractAddress, FunctionEncoder.encode(function), BigInteger.ZERO);
            if (resp.hasError()) {
                throw new IllegalStateException("EVM 交易被拒绝: " + resp.getError().getMessage());
            }
            TransactionReceipt receipt = receiptProcessor.waitForTransactionReceipt(resp.getTransactionHash());
            if (!receipt.isStatusOK()) {
                throw new IllegalStateException("EVM 交易执行失败(reverted): tx=" + receipt.getTransactionHash());
            }
            return new TxResult(receipt.getTransactionHash(), receipt.getBlockNumber().longValueExact(),
                    blockTime(receipt.getBlockNumber()));
        } catch (IOException | TransactionException e) {
            throw new IllegalStateException("上链失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ChainData> queryOnChain(String bizKey) {
        Function function = new Function("latestEvidence",
                List.of(new Utf8String(bizKey)),
                Arrays.asList(new TypeReference<Utf8String>() { }, new TypeReference<Utf8String>() { },
                        new TypeReference<Utf8String>() { }, new TypeReference<Uint256>() { },
                        new TypeReference<Uint256>() { }));
        try {
            EthCall response = web3j.ethCall(Transaction.createEthCallTransaction(
                            credentials.getAddress(), contractAddress, FunctionEncoder.encode(function)),
                    DefaultBlockParameterName.LATEST).send();
            if (response.isReverted() || !StringUtils.hasText(response.getValue())) {
                return Optional.empty();
            }
            List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            if (decoded.size() < 5) {
                return Optional.empty();
            }
            String contentHash = decoded.get(1).getValue().toString();
            long timestamp = ((Uint) decoded.get(3)).getValue().longValueExact();
            return Optional.of(new ChainData(bizKey, contentHash, null, 0,
                    LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())));
        } catch (IOException e) {
            throw new IllegalStateException("查询链上存证失败: " + e.getMessage(), e);
        }
    }

    private BigInteger gasPrice() throws IOException {
        return web3j.ethGasPrice().send().getGasPrice();
    }

    private LocalDateTime blockTime(BigInteger blockNumber) {
        try {
            EthBlock block = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(blockNumber), false).send();
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(block.getBlock().getTimestamp().longValueExact()), ZoneId.systemDefault());
        } catch (IOException e) {
            return LocalDateTime.now();
        }
    }

    private String payloadJson(Map<String, Object> payload) throws IOException {
        return payload == null || payload.isEmpty() ? "" : JSON.writeValueAsString(payload);
    }

    @Override
    public void destroy() {
        if (web3j != null) web3j.shutdown();
    }
}
