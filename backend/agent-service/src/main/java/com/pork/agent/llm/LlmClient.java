package com.pork.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 大模型客户端（OpenAI 兼容协议）。
 * <p>
 * 两条调用路径，用途不同：
 * <ul>
 *   <li>{@link #complete}：<b>只组织语言</b>。事实已由代码查好，模型没有调用工具的权限。
 *       单意图问题走这条，一次调用 3 秒出结果，且它再怎么跑偏也改不了数字。</li>
 *   <li>{@link #chatWithTools}：<b>让模型自己决定查什么</b>，用于「换了说法」和多步问题
 *       （先看运单 → 再查这车上的猪 → 再看检疫证）。工具仍然只读，参数也由工具自己校验。</li>
 * </ul>
 * <p>
 * <b>没配 key 就不发请求：</b>返回 {@link Optional#empty()}，由上层降级成本地归纳。
 * 绝不静默走一个「假装成模型」的分支，也不产生任何调用费用。
 */
@Slf4j
@Component
public class LlmClient {

    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;

    public LlmClient(AgentProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(props.getLlm().getTimeoutSeconds()));
        this.client = RestClient.builder().requestFactory(factory).build();
        if (props.getLlm().usable()) {
            log.info("agent_llm_ready model={} baseUrl={}", props.getLlm().getModel(), props.getLlm().getBaseUrl());
        } else {
            log.info("agent_llm_disabled —— 未配置 AGENT_LLM_API_KEY，回答走本地归纳（不产生任何调用费用）");
        }
    }

    public boolean enabled() {
        return props.getLlm().usable();
    }

    /**
     * @param system    系统提示词（约束边界）
     * @param history   最近若干轮对话，按时间正序
     * @param userNow   本轮用户问题（已附上事实摘要）
     */
    public Optional<String> complete(String system, List<Map<String, String>> history, String userNow) {
        if (!enabled()) return Optional.empty();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        for (Map<String, String> h : history) messages.add(new LinkedHashMap<>(h));
        messages.add(Map.of("role", "user", "content", userNow));

        Map<String, Object> body = baseBody();
        body.put("messages", messages);
        return post(body).map(msg -> msg.path("content").asText(""))
                .filter(s -> !s.isBlank())
                .map(s -> {
                    log.info("agent_llm_used model={} promptChars={} answerChars={}",
                            props.getLlm().getModel(), userNow.length(), s.length());
                    return s.trim();
                });
    }

    /**
     * 带工具的一次对话：模型可以要求调用一个或多个工具，也可以直接给答复。
     *
     * @param messages  完整消息序列（system / 历史 / 本轮问题 / 之前的 tool 消息）
     * @param toolsJson 工具目录的 JSON 数组（OpenAI tools 格式）
     */
    public Optional<LlmTurn> chatWithTools(List<Map<String, Object>> messages, String toolsJson) {
        if (!enabled()) return Optional.empty();
        Map<String, Object> body = baseBody();
        body.put("messages", messages);
        try {
            body.put("tools", mapper.readTree(toolsJson));
            body.put("tool_choice", "auto");
        } catch (Exception e) {
            log.warn("agent_tools_invalid err={}", e.toString());
            return Optional.empty();
        }
        return post(body).map(msg -> {
            String content = msg.path("content").asText("");
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode c : msg.path("tool_calls")) {
                calls.add(new ToolCall(
                        c.path("id").asText(""),
                        c.path("function").path("name").asText(""),
                        c.path("function").path("arguments").asText("{}")));
            }
            return new LlmTurn(content, calls, assistantMessage(content, msg.path("tool_calls")));
        });
    }

    /** assistant 回传消息：content 为空时给空串而非 null——部分兼容实现不接受 null content */
    private Map<String, Object> assistantMessage(String content, JsonNode toolCalls) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        msg.put("content", content == null ? "" : content);
        if (toolCalls.isArray() && !toolCalls.isEmpty()) {
            msg.put("tool_calls", mapper.convertValue(toolCalls, List.class));
        }
        return msg;
    }

    private Map<String, Object> baseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getLlm().getModel());
        body.put("temperature", 0.2);
        body.put("max_tokens", 900);
        body.put("stream", false);
        // 三态：空串不传（换供应商时保持各自默认）；通义千问可显式关掉思考以缩短等待
        String thinking = props.getLlm().getEnableThinking();
        if (thinking != null && !thinking.isBlank()) {
            body.put("enable_thinking", Boolean.parseBoolean(thinking.trim()));
        }
        return body;
    }

    /** 发一次请求，返回 choices[0].message；任何失败都返回 empty，由上层降级 */
    private Optional<JsonNode> post(Map<String, Object> body) {
        String url = props.getLlm().getBaseUrl().replaceAll("/$", "") + "/chat/completions";
        try {
            String raw = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + props.getLlm().getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = mapper.readTree(raw == null ? "{}" : raw);
            JsonNode msg = root.path("choices").path(0).path("message");
            if (msg.isMissingNode() || msg.isNull()) {
                log.warn("agent_llm_empty_response raw={}", raw == null ? "" : raw.substring(0, Math.min(300, raw.length())));
                return Optional.empty();
            }
            return Optional.of(msg);
        } catch (Exception e) {
            // 模型侧失败不该让用户看不到答案：降级成本地归纳，用户仍然拿到真实数据
            log.warn("agent_llm_failed model={} err={}", props.getLlm().getModel(), e.toString());
            return Optional.empty();
        }
    }
}
