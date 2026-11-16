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

    /** 验真明细：在哈希比对结果上补「存在哪种载体、有没有真的上链」，页面据此措辞 */
    Map<String, Object> verifyView(String bizType, Long bizId);

    List<BlockchainRecord> status(String batchNo);
    Map<String, Long> stats();

    /** 当前存证模式（local/evm）与如实描述，前端据此区分「本地存证」与「链上存证」 */
    Map<String, Object> mode();

    /** 对账体检：积压、失败、悬空存证、孤儿块 */
    Map<String, Object> health();

    /** 批量查存证状态：bizId -> status(0 待上链/1 已存证/2 失败)，查不到的 bizId 不出现在结果里 */
    Map<Long, Integer> statusMap(String bizType, List<Long> bizIds);

    /**
     * 把「local 时期写下的历史存证」补写到真实链上（只在 evm 模式可用）。
     * <p>
     * 逐条重放 contentHash 到合约，成功后就地改写该记录的 tx/块号并把对应账本行改挂到新交易上
     * （不一起改会让历史账本块全部变成孤儿）。幂等：已迁成 evm 的记录不会再被选中。
     *
     * @param limit  本轮最多迁移多少条（单轮别太大，避免长时间占住请求）
     * @param dryRun true=只统计待迁移条数，不发交易
     */
    Map<String, Object> migrateLegacy(int limit, boolean dryRun);

    /**
     * 后台批量补写历史存证：立即返回，不占住 HTTP 请求（同步跑 200 条要一分钟以上，网关先超时）。
     * <p>
     * 单飞：同一时刻只允许一个迁移任务（同步入口与后台入口共用一把锁），
     * 已有任务在跑时返回 started=false，不会排队堆积。
     */
    Map<String, Object> startMigrate(int limit);

    /** 迁移进度：running + 上一轮结果 + 剩余待迁条数 */
    Map<String, Object> migrateStatus();

    void retryPending();
}
