package com.pork.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.config.DifyProperties;
import com.pork.ai.dto.DifyChatCommand;
import com.pork.ai.dto.DifyStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Dify Service API 客户端。
 *
 * 只封装 /v1/chat-messages（Chatflow 统一走这个口子）。
 * blocking 与 streaming 共用同一个请求体，区别只有 response_mode 字段。
 */
@Slf4j
@Component
public class DifyClient {

    private static final String CHAT_PATH = "/v1/chat-messages";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DifyProperties properties;

    public DifyClient(@Qualifier("difyWebClient") WebClient webClient,
                      ObjectMapper objectMapper,
                      DifyProperties properties) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        // Key 现在从环境变量 / application-local.yml 注入，没配的话尽早提示，别等到线上调才发现
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Dify API Key 未配置：请设置环境变量 DIFY_API_KEY，"
                    + "或在 application-local.yml 里填 dify.api-key。否则所有 AI 接口都会返回 401");
        }
    }

    /**
     * 一次性拿完整回答（非流式）。适合后台任务、定时报告这类不需要打字机效果的场景。
     */
    public Mono<String> chatBlocking(DifyChatCommand command) {
        Map<String, Object> body = buildBody(command, "blocking");
        return webClient.post()
                .uri(CHAT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() >= 400, resp ->
                        resp.bodyToMono(String.class).flatMap(err -> {
                            log.error("Dify 返回错误 {}: {}", resp.statusCode(), err);
                            return Mono.error(new IllegalStateException("Dify 调用失败: " + err));
                        }))
                .bodyToMono(JsonNode.class)
                .map(node -> node.path("answer").asText(""));
    }

    /**
     * 流式回答。返回的 Flux 每来一个元素就是一小段增量文本。
     *
     * 解析说明：
     *   Dify 用标准 SSE 格式推送 `data: {json}`，Spring 的 SSE 解码器会把 data 部分还原成字符串，
     *   所以这里拿到的每一块基本就是一个完整 JSON。少数情况下会带 "data:" 前缀或空行，
     *   统一在 strip 里做兼容处理。
     */
    public Flux<DifyStreamEvent> streamChat(DifyChatCommand command) {
        Map<String, Object> body = buildBody(command, "streaming");
        return webClient.post()
                .uri(CHAT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.value() >= 400, resp ->
                        resp.bodyToMono(String.class).flatMap(err -> {
                            log.error("Dify 流式调用失败 {}: {}", resp.statusCode(), err);
                            return Mono.error(new IllegalStateException("Dify 调用失败: " + err));
                        }))
                .bodyToFlux(String.class)
                .map(this::strip)
                .filter(s -> !s.isEmpty() && !"[DONE]".equals(s))
                .mapNotNull(this::toEvent)
                // message_end / error 之后没必要继续读，主动断开
                .takeUntil(DifyStreamEvent::isEnd);
    }

    private Map<String, Object> buildBody(DifyChatCommand command, String responseMode) {
        Map<String, Object> body = new HashMap<>(8);
        body.put("inputs", command.getInputs() == null ? Map.of() : command.getInputs());
        body.put("query", command.getQuery());
        body.put("response_mode", responseMode);
        body.put("user", command.getUser() == null ? "anonymous" : command.getUser());
        if (command.getConversationId() != null && !command.getConversationId().isBlank()) {
            body.put("conversation_id", command.getConversationId());
        }
        return body;
    }

    private String strip(String raw) {
        String s = raw.trim();
        if (s.startsWith("data:")) {
            s = s.substring(5).trim();
        }
        return s;
    }

    private DifyStreamEvent toEvent(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String event = node.path("event").asText("");
            if (event.isEmpty()) {
                return null;
            }
            DifyStreamEvent e = new DifyStreamEvent();
            e.setEvent(event);
            e.setAnswer(node.path("answer").asText(""));
            e.setConversationId(node.path("conversation_id").asText(""));
            e.setMessageId(node.path("message_id").asText(""));
            e.setMessage(node.path("message").asText(""));
            e.setCode(node.path("code").asText(""));
            return e;
        } catch (Exception ex) {
            // 心跳 ping 之类不是合法 JSON 的内容，直接忽略
            log.debug("忽略无法解析的 SSE 片段: {}", json);
            return null;
        }
    }

    /** 供健康检查/排障用 */
    public String describe() {
        return "Dify[" + properties.getBaseUrl() + "] key=" + mask(properties.getApiKey());
    }

    private String mask(String key) {
        if (key == null || key.length() < 12) {
            return "****";
        }
        return key.substring(0, 8) + "****" + key.substring(key.length() - 2);
    }
}
