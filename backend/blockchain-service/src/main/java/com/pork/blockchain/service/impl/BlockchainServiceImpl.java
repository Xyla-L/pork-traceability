package com.pork.blockchain.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.blockchain.adapter.BlockchainAdapter;
import com.pork.blockchain.entity.BlockchainRecord;
import com.pork.blockchain.entity.ChainLedger;
import com.pork.blockchain.mapper.BlockchainRecordMapper;
import com.pork.blockchain.mapper.ChainLedgerMapper;
import com.pork.blockchain.service.BlockchainService;
import com.pork.blockchain.support.PhiMasker;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.PageResult;
import com.pork.core.util.CanonicalStringBuilder;
import com.pork.mq.ChainTxMessage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainServiceImpl implements BlockchainService {

    /** 未完成存证的容忍窗口：超过这个时间还没写入，就不再是「正在重试」而是积压 */
    private static final long PENDING_TOLERANCE_MINUTES = 30;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final BlockchainRecordMapper mapper;
    private final ChainLedgerMapper ledgerMapper;
    private final BlockchainAdapter adapter;

    @Value("${blockchain.evm.explorer-url:}")
    private String explorerUrl;

    @Value("${blockchain.evm.contract-address:}")
    private String contractAddress;

    @Value("${blockchain.evm.rpc-url:}")
    private String rpcUrl;

    @Value("${blockchain.evm.chain-id:1337}")
    private long chainId;

    /** 上链前脱敏开关：默认开。关掉只应出现在「要拿链上原文做取证」的场合，且需自己承担合规责任 */
    @Value("${blockchain.mask-phi:true}")
    private boolean maskPhi;

    /** 迁移任务单飞：并发点两次会重复上链（合约同 hash 幂等，但会多打版本、账本行也会打架） */
    private final AtomicBoolean migrating = new AtomicBoolean(false);
    private volatile Map<String, Object> lastMigrateResult;
    private volatile String lastMigrateError;
    private final ExecutorService migrateExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chain-migrate");
        t.setDaemon(true);
        return t;
    });

    @Override
    @Transactional(noRollbackFor = ChainStoreException.class)
    public void store(ChainTxMessage message) {
        BlockchainRecord record = mapper.selectOne(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getEventId, message.eventId()));
        if (record != null && record.getStatus() == 1) return;
        // 上链报文在这里定型：脱敏后的这一份既写链、也当快照留档，两边必须完全一致，
        // 否则验真时没法证明「链上原文」与「本地留存」是一回事
        Map<String, Object> onChainPayload = maskPhi ? PhiMasker.mask(message.payload()) : message.payload();
        if (record == null) {
            record = new BlockchainRecord();
            record.setEventId(message.eventId());
            record.setBizType(message.bizType());
            record.setBizId(message.bizId());
            record.setBizKey(message.bizKey());
            record.setContentHash(message.contentHash());
            // 报文必须与哈希同批落库：只有哈希的话，失败重试就没东西可重放，链上只剩一个数字
            record.setPayloadJson(payloadJson(onChainPayload));
            record.setStatus(0);
            record.setChainMode(adapter.mode());
            record.setRetryCount(0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            mapper.insert(record);
        } else if (!StringUtils.hasText(record.getPayloadJson()) && onChainPayload != null && !onChainPayload.isEmpty()) {
            // 加列之前写入的记录（或首次因故没留下快照的）：这次既然带着原文来，就补上
            record.setPayloadJson(payloadJson(onChainPayload));
            record.setUpdateTime(LocalDateTime.now());
            mapper.updateById(record);
        }
        try {
            BlockchainAdapter.TxResult tx = adapter.storeOnChain(record.getBizType(), record.getBizKey(), record.getContentHash(), onChainPayload);
            record.setTxHash(tx.txHash());
            record.setBlockNumber(tx.blockNumber());
            record.setChainTime(tx.chainTime());
            record.setStatus(1);
            // 载体随写入路径走：切到 evm 之后新记录一律标 evm，页面不再有「假装上链」的余地
            record.setChainMode(adapter.mode());
            record.setErrorMsg(null);
            record.setUpdateTime(LocalDateTime.now());
            mapper.updateById(record);
        } catch (RuntimeException ex) {
            record.setStatus(2);
            record.setRetryCount(record.getRetryCount() + 1);
            record.setErrorMsg(crop(ex.getMessage()));
            record.setUpdateTime(LocalDateTime.now());
            mapper.updateById(record);
            // 失败不能只吞在数据库里：这条日志是对账任务之外唯一的现场，必须留
            log.warn("chain_store_failed eventId={} bizType={} bizKey={} retry={} err={}",
                    record.getEventId(), record.getBizType(), record.getBizKey(),
                    record.getRetryCount(), record.getErrorMsg());
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
                    row.getContentHash(), payloadOf(row), row.getCreateTime()));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.BLOCKCHAIN_ERROR, "上链重试失败");
        }
        return record(id);
    }

    @Override
    public BlockchainAdapter.Verification verify(String bizType, Long bizId) {
        BlockchainRecord row = latestRecord(bizType, bizId);
        return adapter.verifyHash(row.getBizKey(), row.getContentHash());
    }

    @Override
    public Map<String, Object> verifyView(String bizType, Long bizId) {
        BlockchainRecord row = latestRecord(bizType, bizId);
        BlockchainAdapter.Verification v = adapter.verifyHash(row.getBizKey(), row.getContentHash());
        boolean stored = Integer.valueOf(1).equals(row.getStatus());
        boolean onChain = "evm".equals(adapter.mode());
        // 「历史本地存证」只在切到 evm 之后才成立：local 模式下所有记录都是本地账本，属正常态
        boolean legacy = onChain && "local".equals(row.getChainMode());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("bizType", row.getBizType());
        view.put("bizId", row.getBizId());
        view.put("bizKey", row.getBizKey());
        view.put("status", row.getStatus());
        view.put("stored", stored);
        view.put("mode", adapter.mode());
        view.put("onChain", onChain);
        // 这条记录本身是不是真上过链：切 evm 后仍有迁移前的本地记录，不能按当前模式一概而论
        view.put("chainMode", row.getChainMode());
        view.put("legacy", legacy);
        view.put("note", legacy ? "该记录写于平台本地账本时期（未上链），补写链上前只能做平台内核对" : null);
        // 本地账本只能证明「平台内记录没被改过」；只有真上链的才谈得上与链上一致
        view.put("matched", stored && !legacy && v.matched());
        view.put("localHash", v.localHash());
        view.put("onChainHash", v.onChainHash());
        view.put("txHash", row.getTxHash());
        view.put("blockNumber", row.getBlockNumber());
        view.put("chainTime", row.getChainTime());
        // 有快照才谈得上「拿原文核验」；没有快照的只能证明哈希与链上一致，如实标出来
        boolean payloadAvailable = StringUtils.hasText(row.getPayloadJson());
        view.put("payloadAvailable", payloadAvailable);
        // 上链原文：审计员可以自己算指纹、也可以跟业务页面逐字对（历史记录没有快照，给 null）
        view.put("payload", payloadAvailable ? row.getPayloadJson() : null);
        // 链上原文 vs 本地快照：证明「我们留的原文」就是当初上链的那一份。
        // 只有链上能读到报文时才给结论（local 账本没有报文，返回 null 而不是 false）
        Boolean payloadOnChainMatched = null;
        if (payloadAvailable && StringUtils.hasText(v.chainPayload())) {
            payloadOnChainMatched = canonicalJson(row.getPayloadJson()).equals(canonicalJson(v.chainPayload()));
        }
        view.put("payloadOnChainMatched", payloadOnChainMatched);
        view.put("explorerUrl", explorerUrl);
        return view;
    }

    private BlockchainRecord latestRecord(String bizType, Long bizId) {
        BlockchainRecord row = mapper.selectOne(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getBizType, bizType).eq(BlockchainRecord::getBizId, bizId)
                .orderByDesc(BlockchainRecord::getCreateTime).last("LIMIT 1"));
        if (row == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "存证记录不存在");
        return row;
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
        // 「今日新增」必须按 create_time（存证记录创建时间）算：
        // chain_time 是上链时间，历史记录补写迁移时会被改成迁移当天的出块时间，
        // 按 chain_time 统计会把 962 条历史记录全算成「今日新增」，数字失真。
        Long today = mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery().ge(BlockchainRecord::getCreateTime, start));
        Long pending = mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery().in(BlockchainRecord::getStatus, 0, 2));
        Long latest = mapper.selectObjs(Wrappers.<BlockchainRecord>query().select("MAX(block_number)"))
                .stream().filter(v -> v != null).findFirst().map(v -> ((Number) v).longValue()).orElse(0L);
        return Map.of("totalTx", total, "todayTx", today, "pendingTx", pending, "latestBlock", latest);
    }

    @Override
    public Map<String, Object> mode() {
        String mode = adapter.mode();
        boolean onChain = "evm".equals(mode);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mode", mode);
        view.put("onChain", onChain);
        view.put("label", onChain ? "区块链存证（EVM）" : "本地存证账本");
        view.put("summary", onChain
                ? "存证已写入区块链，可凭交易哈希在链上独立核验"
                : "存证记录保存在平台数据库内，未写入区块链；可用于平台内一致性核对，不能作为第三方链上核验依据");
        view.put("explorerUrl", onChain && StringUtils.hasText(explorerUrl) ? explorerUrl : null);
        view.put("contractAddress", onChain && StringUtils.hasText(contractAddress) ? contractAddress : null);
        view.put("chainId", onChain ? chainId : null);
        // 开发链要自己说出来：单节点 + 无浏览器 = 第三方没法独立核验，页面不能只写「区块链存证」
        boolean developerChain = onChain && isDeveloperChain();
        view.put("developerChain", developerChain);
        view.put("chainWarning", developerChain
                ? "当前接入的是开发链（单节点、无区块浏览器）：交易哈希可自查，但第三方无法独立核验，不能据此对外宣称「已上链可查」"
                : null);
        view.put("maskPhi", maskPhi);
        // 切到 evm 后仍有「写于本地账本时期」的记录：页面必须能说清还有多少条没补写链上
        view.put("legacyLocal", onChain ? legacyLocalCount() : 0);
        view.put("migrated", onChain ? migratedCount() : 0);
        return view;
    }

    private long legacyLocalCount() {
        return mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getChainMode, "local").eq(BlockchainRecord::getStatus, 1));
    }

    /**
     * 开发链判定：没有区块浏览器，或 RPC 指向本机/ganache。
     * 这类链「能写、能读、能自证」，但拿不到第三方可访问的公开账本——页面必须把话说明白。
     */
    private boolean isDeveloperChain() {
        if (!StringUtils.hasText(explorerUrl)) return true;
        String rpc = rpcUrl == null ? "" : rpcUrl.toLowerCase();
        return rpc.contains("localhost") || rpc.contains("127.0.0.1") || rpc.contains("ganache");
    }

    private long migratedCount() {
        return mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery()
                .isNotNull(BlockchainRecord::getMigratedAt));
    }

    @Override
    public Map<String, Object> migrateLegacy(int limit, boolean dryRun) {
        requireEvm("当前是本地账本模式，历史存证本来就在本地账本里，无需迁移");
        // 同步入口也要单飞：两个管理员同时点会重复上链（合约同 hash 幂等，但会多打版本、账本行互相打架）
        if (!migrating.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.BLOCKCHAIN_ERROR, "已有迁移任务在执行，等它跑完再点");
        }
        try {
            return migrateLegacyInternal(boundLimit(limit), dryRun);
        } finally {
            migrating.set(false);
        }
    }

    @Override
    public Map<String, Object> startMigrate(int limit) {
        requireEvm("当前是本地账本模式，历史存证本来就在本地账本里，无需迁移");
        int batch = boundLimit(limit);
        Map<String, Object> view = new LinkedHashMap<>();
        if (!migrating.compareAndSet(false, true)) {
            view.put("started", false);
            view.put("running", true);
            view.put("reason", "已有迁移任务在执行，等它跑完再点");
            return view;
        }
        lastMigrateError = null;
        migrateExecutor.submit(() -> {
            try {
                lastMigrateResult = migrateLegacyInternal(batch, false);
            } catch (RuntimeException e) {
                lastMigrateError = crop(e.getMessage());
                log.warn("chain_migrate_async_failed err={}", lastMigrateError);
            } finally {
                migrating.set(false);
            }
        });
        log.info("chain_migrate_async_started limit={}", batch);
        view.put("started", true);
        view.put("running", true);
        view.put("limit", batch);
        view.put("note", "已在后台执行，用 GET /blockchain/migrate/legacy/status 看进度");
        return view;
    }

    @Override
    public Map<String, Object> migrateStatus() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("running", migrating.get());
        view.put("lastResult", lastMigrateResult);
        view.put("lastError", lastMigrateError);
        view.put("remaining", "evm".equals(adapter.mode()) ? legacyLocalCount() : 0);
        return view;
    }

    private void requireEvm(String message) {
        if (!"evm".equals(adapter.mode())) throw new BusinessException(ErrorCode.BLOCKCHAIN_ERROR, message);
    }

    /** 单轮上限 200：再多就是几十分钟的写链，出问题不好收场 */
    private int boundLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
    }

    private Map<String, Object> migrateLegacyInternal(int batch, boolean dryRun) {
        List<BlockchainRecord> rows = mapper.selectList(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getChainMode, "local").eq(BlockchainRecord::getStatus, 1)
                .orderByAsc(BlockchainRecord::getId).last("LIMIT " + batch));
        int ok = 0;
        int failed = 0;
        int payloadMissing = 0;
        List<String> errors = new ArrayList<>();
        for (BlockchainRecord row : rows) {
            // 加列之前写入的本地存证没有报文快照：这类只能凭哈希补写，链上不会有可读内容，
            // 结果里如实报数，别让「迁移完成」被理解成「内容都在链上」
            if (!StringUtils.hasText(row.getPayloadJson())) payloadMissing++;
            if (dryRun) { ok++; continue; }
            try {
                // 重放的是同一条 contentHash：链上留的是原始内容指纹，不是事后重新算的
                BlockchainAdapter.TxResult tx = adapter.storeOnChain(
                        row.getBizType(), row.getBizKey(), row.getContentHash(), payloadOf(row));
                String oldTx = row.getTxHash();
                if (StringUtils.hasText(oldTx)) {
                    // 账本行必须跟着改挂：否则旧 tx_hash 在 record 里消失，历史块会全部变孤儿
                    ledgerMapper.update(null, Wrappers.<ChainLedger>lambdaUpdate()
                            .eq(ChainLedger::getTxHash, oldTx)
                            .set(ChainLedger::getTxHash, tx.txHash())
                            .set(ChainLedger::getBlockNumber, tx.blockNumber())
                            .set(ChainLedger::getChainTime, tx.chainTime()));
                }
                row.setTxHash(tx.txHash());
                row.setBlockNumber(tx.blockNumber());
                row.setChainTime(tx.chainTime());
                row.setChainMode("evm");
                row.setMigratedAt(LocalDateTime.now());
                row.setUpdateTime(LocalDateTime.now());
                mapper.updateById(row);
                ok++;
            } catch (RuntimeException ex) {
                failed++;
                if (errors.size() < 5) {
                    errors.add(row.getBizType() + "#" + row.getBizId() + "：" + crop(ex.getMessage()));
                }
                log.warn("chain_migrate_failed id={} bizType={} bizKey={} err={}",
                        row.getId(), row.getBizType(), row.getBizKey(), crop(ex.getMessage()));
            }
        }
        long remaining = legacyLocalCount();
        log.info("chain_migrate_legacy dryRun={} picked={} ok={} failed={} remaining={}",
                dryRun, rows.size(), ok, failed, remaining);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("picked", rows.size());
        result.put("migrated", dryRun ? 0 : ok);
        result.put("checked", ok);
        result.put("failed", failed);
        result.put("payloadMissing", payloadMissing);
        result.put("remaining", remaining);
        result.put("errors", errors);
        return result;
    }

    @Override
    public Map<String, Object> health() {
        long total = mapper.selectCount(null);
        long success = mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery().eq(BlockchainRecord::getStatus, 1));
        long pending = mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery().eq(BlockchainRecord::getStatus, 0));
        long failed = mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery().eq(BlockchainRecord::getStatus, 2));
        LocalDateTime oldestUnfinished = mapper.oldestUnfinishedTime();
        long oldestPendingMinutes = oldestUnfinished == null ? 0
                : Math.max(0, Duration.between(oldestUnfinished, LocalDateTime.now()).toMinutes());

        long orphan = ledgerMapper.countOrphan();
        long orphanRecent = ledgerMapper.countOrphanSince(LocalDateTime.now().minusHours(24));
        long dangling = ledgerMapper.countDanglingRecord();

        boolean ok = failed == 0 && dangling == 0 && orphanRecent == 0
                && oldestPendingMinutes <= PENDING_TOLERANCE_MINUTES;

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mode", adapter.mode());
        view.put("onChain", "evm".equals(adapter.mode()));
        view.put("ok", ok);
        view.put("total", total);
        view.put("success", success);
        view.put("pending", pending);
        view.put("failed", failed);
        view.put("oldestPendingMinutes", oldestPendingMinutes);
        view.put("pendingToleranceMinutes", PENDING_TOLERANCE_MINUTES);
        view.put("ledgerTotal", ledgerMapper.selectCount(null));
        // 历史遗留的孤儿块如实披露、不装作 0：它们无法验真，也不该删（删了账本就不可回溯）
        view.put("orphanLedger", orphan);
        view.put("orphanLedgerRecent", orphanRecent);
        view.put("danglingRecord", dangling);
        // 切 evm 后还有多少条「写于本地账本时期」的记录没补写链上：这是如实披露的重要一项
        view.put("legacyLocal", "evm".equals(adapter.mode()) ? legacyLocalCount() : 0);
        view.put("migrated", migratedCount());
        // 已上链但没留报文快照的条数：链上只有指纹没有内容，第三方无法据此独立核验
        view.put("payloadMissing", payloadMissingCount());
        view.put("developerChain", "evm".equals(adapter.mode()) && isDeveloperChain());
        view.put("maskPhi", maskPhi);
        view.put("issues", issues(failed, dangling, orphanRecent, oldestPendingMinutes));
        view.put("checkedAt", LocalDateTime.now());
        return view;
    }

    private List<String> issues(long failed, long dangling, long orphanRecent, long oldestPendingMinutes) {
        List<String> issues = new ArrayList<>();
        if (failed > 0) issues.add(failed + " 条存证写入失败且已停止重试");
        if (dangling > 0) issues.add(dangling + " 条存证显示已存证但账本查不到对应块");
        if (orphanRecent > 0) issues.add("近 24 小时新增 " + orphanRecent + " 个账本块没有对应存证记录");
        if (oldestPendingMinutes > PENDING_TOLERANCE_MINUTES) {
            issues.add("最老一条待存证已积压 " + oldestPendingMinutes + " 分钟");
        }
        return issues;
    }

    @Override
    public Map<Long, Integer> statusMap(String bizType, List<Long> bizIds) {
        if (!StringUtils.hasText(bizType) || bizIds == null || bizIds.isEmpty()) return Map.of();
        List<BlockchainRecord> rows = mapper.selectList(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getBizType, bizType)
                .in(BlockchainRecord::getBizId, bizIds)
                .orderByAsc(BlockchainRecord::getId));
        Map<Long, Integer> map = new LinkedHashMap<>();
        // 同一业务键可能重试出多行：以最后一行（id 最大）为准，失败后重试成功要显示成功
        for (BlockchainRecord row : rows) {
            map.put(row.getBizId(), row.getStatus());
        }
        return map;
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
                        row.getContentHash(), payloadOf(row), row.getCreateTime()));
            } catch (RuntimeException ex) {
                // 失败状态与次数由 store() 落库；这里补一条日志，别让补偿任务静默失败
                log.warn("chain_retry_failed eventId={} bizType={} retry={} err={}",
                        row.getEventId(), row.getBizType(), row.getRetryCount() + 1, crop(ex.getMessage()));
            }
        }
        if (!rows.isEmpty()) log.info("chain_retry_round scanned={}", rows.size());
    }

    private String crop(String message) {
        if (message == null) return "unknown blockchain error";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    /** 报文快照落库格式：空报文存 NULL，便于用「是否为空」区分「当初没留」与「留了空对象」 */
    private String payloadJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("chain_payload_serialize_failed err={}", crop(e.getMessage()));
            return null;
        }
    }

    /** 重放报文：快照缺失时退化为空报文（历史记录没有原文可还原），不能因此阻断重试 */
    private Map<String, Object> payloadOf(BlockchainRecord row) {
        if (!StringUtils.hasText(row.getPayloadJson())) return Map.of();
        try {
            return JSON.readValue(row.getPayloadJson(), new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.warn("chain_payload_unreadable id={} err={}", row.getId(), crop(e.getMessage()));
            return Map.of();
        }
    }

    /** 规范化后再比：键序、空格差异不该被当成「链上原文与本地快照不一致」 */
    private String canonicalJson(String json) {
        if (!StringUtils.hasText(json)) return "";
        try {
            return CanonicalStringBuilder.build(com.alibaba.fastjson2.JSON.parse(json));
        } catch (Exception e) {
            return json;
        }
    }

    @PreDestroy
    void shutdownExecutor() {
        migrateExecutor.shutdownNow();
    }

    /** 已存证但没有报文快照的条数：只能证明「哈希没变」，拿不出原文给人核验 */
    private long payloadMissingCount() {
        return mapper.selectCount(Wrappers.<BlockchainRecord>lambdaQuery()
                .eq(BlockchainRecord::getStatus, 1)
                .and(w -> w.isNull(BlockchainRecord::getPayloadJson).or().eq(BlockchainRecord::getPayloadJson, "")));
    }

    private static final class ChainStoreException extends RuntimeException {
        private ChainStoreException(Throwable cause) { super(cause); }
    }
}
