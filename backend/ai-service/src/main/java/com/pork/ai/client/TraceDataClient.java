package com.pork.ai.client;

import com.pork.ai.exception.TraceDataUnavailableException;
import com.pork.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 从 trace-service 取业务数据。
 *
 * 为什么 AI 服务要自己去取数据，而不是让前端把数据传进来：
 *   1. 前端传的数据可以被篡改，AI 解读就成了"帮用户圆谎"
 *   2. 监管报告必须基于库里的真实数据，可信链不能断
 *   3. 后续做自动报告（定时任务）时没有前端参与
 *
 * ⚠️ 安全说明（这是评审发现的问题，已修）：
 *   原来 qrCode 直接字符串拼接进 URL，攻击者传 `../../dashboard/overview` 就能让
 *   `GET /trace/safe-buy/../../dashboard/overview` 被 Tomcat 归一化成
 *   `GET /dashboard/overview`，把监管看板的汇总数据当成"这块肉"的溯源结果返回，
 *   甚至让模型输出"这块肉可以放心购买"。
 *   现在做了两层：先按白名单校验字符，再用 UriUtils.encodePathSegment 编码。
 */
@Slf4j
@Component
public class TraceDataClient {

    private static final ParameterizedTypeReference<Result<Map<String, Object>>> RESULT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /** 取数超时。trace-service 是内网同机房调用，10 秒足够；超时要能感知到 */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    /** 业务码 200 表示成功，与 Result.success() 保持一致 */
    private static final int SUCCESS_CODE = 200;

    /**
     * 允许出现在路径段里的字符：字母、数字、下划线、短横线、点。
     * 点单独允许是因为真实溯源码可能带版本号（如 QR.2026.001），
     * 但下面会额外拒绝连续的 ".."，避免构成上级目录语义。
     */
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private final WebClient webClient;
    private final String traceUrl;

    public TraceDataClient(@Qualifier("plainWebClient") WebClient webClient,
                           @Value("${services.trace-url}") String traceUrl) {
        this.webClient = webClient;
        this.traceUrl = traceUrl;
    }

    /** 消费者扫码后的安全购买数据（产品信息 + 证书链 + 检测报告 + 上链记录） */
    public Mono<Map<String, Object>> safeBuy(String qrCode) {
        return get("/trace/safe-buy/" + encodePathSegment(qrCode, "qrCode"), "safeBuy");
    }

    /** 完整溯源链路（上游养殖/屠宰 + 下游流通/销售） */
    public Mono<Map<String, Object>> fullTrace(String batchNo) {
        return get("/trace/full/" + encodePathSegment(batchNo, "batchNo"), "fullTrace");
    }

    /** 监管看板总览（批次量、异常数、投诉等） */
    public Mono<Map<String, Object>> dashboardOverview() {
        return get("/dashboard/overview", "dashboardOverview");
    }

    /**
     * 校验并编码路径段。
     *
     * 三层防护，顺序不能反：
     *   1. 非空 + 长度上限 —— 防止超长 URL 打到下游
     *   2. 白名单正则 —— 直接拒掉 / \ .. ? # 空格 中文 这些会改变 URL 结构的字符
     *   3. 再用 encodePathSegment 编一次 —— 兜底，万一以后放宽了正则也不会出事
     */
    private String encodePathSegment(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String trimmed = raw.trim();
        if (!SAFE_SEGMENT.matcher(trimmed).matches()) {
            // 不把原始值打进日志，避免被日志注入
            log.warn("拒绝非法 {} 参数，长度={}", field, trimmed.length());
            throw new IllegalArgumentException(field + " 含非法字符");
        }
        if (trimmed.contains("..")) {
            log.warn("拒绝包含 '..' 的 {} 参数", field);
            throw new IllegalArgumentException(field + " 含非法字符");
        }
        return UriUtils.encodePathSegment(trimmed, StandardCharsets.UTF_8);
    }

    /**
     * 统一取数。
     *
     * 关键改动：不再 onErrorResume 吞掉所有异常。
     * 现在严格区分三种情况：
     *   - 网络失败 / 超时 / 5xx  → 抛 TraceDataUnavailableException（上层转 SSE error）
     *   - 业务码非 200            → 抛 TraceDataUnavailableException（下游明确报错了）
     *   - 业务码 200 + data 为空  → 返回空 Map（"这块肉确实没有记录"，属于正常业务态）
     *
     * 这个区分是整个修复的核心：前者是故障，后者是事实，不能混为一谈。
     */
    private Mono<Map<String, Object>> get(String path, String tag) {
        return webClient.get()
                .uri(traceUrl + path)
                .retrieve()
                .bodyToMono(RESULT_TYPE)
                .timeout(FETCH_TIMEOUT)
                .onErrorMap(WebClientResponseException.class,
                        e -> new TraceDataUnavailableException(tag,
                                "trace-service 返回 " + e.getStatusCode().value(), e))
                .onErrorMap(WebClientRequestException.class,
                        e -> new TraceDataUnavailableException(tag, "trace-service 连接失败", e))
                .onErrorMap(TimeoutException.class,
                        e -> new TraceDataUnavailableException(tag, "trace-service 取数超时", e))
                .map(result -> {
                    if (result == null) {
                        throw new TraceDataUnavailableException(tag, "trace-service 返回空响应");
                    }
                    Integer code = result.getCode();
                    if (code == null || code != SUCCESS_CODE) {
                        throw new TraceDataUnavailableException(tag,
                                "trace-service 业务码异常: " + code + " " + result.getMessage());
                    }
                    // 业务成功但数据为空：这是"没有记录"，不是故障
                    Map<String, Object> data = result.getData();
                    if (data == null || data.isEmpty()) {
                        log.info("[{}] 下游返回空数据", tag);
                        return Map.<String, Object>of();
                    }
                    return data;
                })
                .doOnError(e -> log.warn("[{}] 取数失败: {}", tag, e.getMessage()));
    }
}
