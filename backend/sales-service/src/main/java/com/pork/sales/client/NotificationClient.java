package com.pork.sales.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知投递（trace 的通知中心，内部接口）。
 * <p>
 * 通知中心只负责「写进收件箱」，收件人由调用方解析好再传 —— 它不猜谁该收到。
 * 投递失败只记日志：召回是既成事实，不会因为一条通知发不出去就撤销；
 * 但日志必须留，否则「以为通知过了」比没通知更危险。
 */
@Slf4j
@Component
public class NotificationClient {

    private final RestClient client;
    private final String traceUrl;

    public NotificationClient(RestClient.Builder builder,
                              @Value("${services.trace-url:http://localhost:8086}") String traceUrl) {
        this.client = builder.build();
        this.traceUrl = traceUrl;
    }

    /**
     * 给一批用户推同一条通知。
     *
     * @return 实际写入条数；失败返回 0
     */
    public int notifyUsers(List<Long> userIds, String title, String content, String bizType, Long bizId) {
        if (userIds == null || userIds.isEmpty()) return 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userIds", userIds);
        body.put("title", title);
        body.put("content", content);
        body.put("type", "BUSINESS");
        body.put("bizType", bizType);
        body.put("bizId", bizId);
        try {
            Map<String, Object> envelope = client.post().uri(traceUrl + "/notification/internal/create")
                    .body(body).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null) return 0;
            Object data = envelope.get("data");
            return data instanceof Number n ? n.intValue() : 0;
        } catch (RestClientException e) {
            log.warn("notification_failed bizType={} bizId={} users={} err={}", bizType, bizId,
                    userIds.size(), e.toString());
            return 0;
        }
    }
}
