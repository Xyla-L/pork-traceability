package com.pork.blockchain.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存证告警投递（走 trace 的通知中心内部接口，落进用户收件箱）。
 * <p>
 * 告警的收件人只有管理员：存证链路断了是运维/管理问题，不该推给门店或司机。
 * 同类告警 1 小时内只发一次 —— 定时任务每小时跑一次，断了会一直断，
 * 每轮都发一条只会把收件箱淹掉，反而没人看。
 */
@Slf4j
@Component
public class ChainAlertNotifier {

    private static final Duration COOLDOWN = Duration.ofHours(1);
    private static final String BIZ_TYPE = "chain_alert";

    private final RestClient client;
    private final JdbcTemplate jdbc;
    private final String traceUrl;
    private final Map<String, LocalDateTime> lastSent = new ConcurrentHashMap<>();

    public ChainAlertNotifier(RestClient.Builder builder, JdbcTemplate jdbc,
                              @Value("${services.trace-url:http://localhost:8086}") String traceUrl) {
        this.client = builder.build();
        this.jdbc = jdbc;
        this.traceUrl = traceUrl;
    }

    /**
     * @param key     告警类别（用于去重，例如 reconcile / mode-mismatch）
     * @param title   收件箱标题
     * @param content 正文：必须写清「哪里不对、去看哪一页」
     */
    public void alert(String key, String title, String content) {
        LocalDateTime last = lastSent.get(key);
        if (last != null && last.plus(COOLDOWN).isAfter(LocalDateTime.now())) {
            log.info("chain_alert_suppressed key={} lastSent={}", key, last);
            return;
        }
        List<Long> users = adminUserIds();
        if (users.isEmpty()) {
            // 没有收件人时也要留痕：否则「以为通知过了」比没通知更危险
            log.warn("chain_alert_no_recipient key={} title={}", key, title);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userIds", users);
        body.put("title", title);
        body.put("content", content);
        body.put("type", "ALERT");
        body.put("bizType", BIZ_TYPE);
        body.put("bizId", 0L);
        try {
            client.post().uri(traceUrl + "/notification/internal/create")
                    .body(body).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            lastSent.put(key, LocalDateTime.now());
            log.info("chain_alert_sent key={} users={} title={}", key, users.size(), title);
        } catch (RestClientException e) {
            log.warn("chain_alert_failed key={} err={}", key, e.toString());
        }
    }

    private List<Long> adminUserIds() {
        try {
            return jdbc.queryForList("select id from sys_user where role = 'ADMIN' and status = 1", Long.class);
        } catch (RuntimeException e) {
            log.warn("chain_alert_recipient_query_failed err={}", e.toString());
            return List.of();
        }
    }
}
