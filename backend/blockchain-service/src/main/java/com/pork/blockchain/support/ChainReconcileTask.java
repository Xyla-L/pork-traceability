package com.pork.blockchain.support;

import com.pork.blockchain.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 存证对账：定时比对存证记录与账本，出问题就告警。
 * <p>
 * 为什么需要它：上链是 AFTER_COMMIT 的旁路动作，业务成功了存证可能失败；
 * 补偿任务失败又是静默的（只写一行 errorMsg）。没有主动对账，
 * 页面会一直显示「已存证」，直到有人真去验真才发现对不上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainReconcileTask {

    private final BlockchainService service;
    private final ChainAlertNotifier notifier;

    /**
     * 启动 60 秒后先体检一次（部署完立刻知道现状），之后每小时一次。
     * 用固定延迟而不是固定时刻：避免和其他整点任务抢数据库。
     */
    @Scheduled(initialDelayString = "${blockchain.reconcile-initial-delay-ms:60000}",
            fixedDelayString = "${blockchain.reconcile-interval-ms:3600000}")
    public void reconcile() {
        Map<String, Object> health;
        try {
            health = service.health();
        } catch (RuntimeException e) {
            log.error("chain_reconcile_failed err={}", e.toString());
            return;
        }
        boolean ok = Boolean.TRUE.equals(health.get("ok"));
        if (ok) {
            log.info("chain_reconcile_ok mode={} total={} success={} orphanLedger={}",
                    health.get("mode"), health.get("total"), health.get("success"), health.get("orphanLedger"));
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> issues = (List<String>) health.get("issues");
        log.error("chain_reconcile_alert mode={} issues={}", health.get("mode"), issues);
        String first = issues.isEmpty() ? "存证链路异常" : issues.get(0);
        notifier.alert("reconcile", "存证对账异常：" + first,
                String.join("；", issues) + "。详情见「系统管理 → 存证审计」的存证体检。");
    }
}
