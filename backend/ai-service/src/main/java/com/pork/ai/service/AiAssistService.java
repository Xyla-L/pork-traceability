package com.pork.ai.service;

import com.pork.ai.client.DifyClient;
import com.pork.ai.client.TraceDataClient;
import com.pork.ai.dto.DifyChatCommand;
import com.pork.ai.dto.DifyStreamEvent;
import com.pork.ai.prompt.PayloadCondenser;
import com.pork.ai.prompt.PromptFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 双角色 AI 编排层：取数 → 组 Prompt → 调 Dify。
 *
 * 这一层是"业务"，DifyClient 是"管道"，PromptFactory 是"话术"。
 * 三者分开的好处：换模型只动管道，调效果只动话术，加新角色只动编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistService {

    /** 问题里可能指向业务对象的标识：字母数字开头，允许 . _ - ，长度 6~40 */
    private static final Pattern CANDIDATE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{5,39}");
    /** 一轮问答最多查 3 个标识：再多下游压力大、Prompt 也会被数据挤爆 */
    private static final int MAX_CANDIDATES = 3;

    private final TraceDataClient traceDataClient;
    private final DifyClient difyClient;
    private final PromptFactory promptFactory;

    /**
     * 消费者端：扫码后的一键解读。
     */
    public Flux<DifyStreamEvent> consumerInterpret(String qrCode, String userId, String conversationId) {
        return traceDataClient.safeBuy(qrCode)
                .flatMapMany(data -> {
                    log.info("消费者解读 qrCode={} 数据字段={}", qrCode, data.keySet());
                    String prompt = promptFactory.consumerInterpret(data);
                    return callDify(prompt, "consumer-" + (userId == null ? "guest" : userId), conversationId);
                });
    }

    /**
     * 消费者端：基于同一份溯源数据的自由追问。
     */
    public Flux<DifyStreamEvent> consumerAsk(String qrCode, String question, String userId, String conversationId) {
        return traceDataClient.safeBuy(qrCode)
                .flatMapMany(data -> {
                    String prompt = promptFactory.consumerFollowUp(data, question);
                    return callDify(prompt, "consumer-" + (userId == null ? "guest" : userId), conversationId);
                });
    }

    /**
     * 监管端：当日监管简报。
     */
    public Flux<DifyStreamEvent> adminReport(String userId, String conversationId) {
        return traceDataClient.dashboardOverview()
                .flatMapMany(data -> {
                    log.info("监管报告生成，数据字段={}", data.keySet());
                    String prompt = promptFactory.adminReport(data);
                    return callDify(prompt, "admin-" + (userId == null ? "guest" : userId), conversationId);
                });
    }

    /**
     * 监管端：自由问答。
     *
     * 取数策略（顺序即优先级）：
     *   1. 平台总览 —— 任何问题都带上，模型回答「多少、什么趋势」时才有据可依
     *   2. 问题里出现的业务标识（批次号/溯源码） —— 最多 3 个，逐个检索真实记录
     *
     * 两条硬规矩：
     *   - 总览取失败不致命（还能回答单条记录的问题），但也不能假装有数据：
     *     两个来源都空时，在数据里显式写「未取到任何数据」，让 Prompt 直接认输。
     *   - 检索失败 ≠ 故障：查不到就是查不到，记录日志后跳过，不抛错把整轮问答打断。
     */
    public Flux<DifyStreamEvent> adminAsk(String question, String userId, String conversationId) {
        Mono<Map<String, Object>> overview = traceDataClient.dashboardOverview()
                .onErrorResume(e -> {
                    log.warn("管理端问答取总览失败: {}", e.getMessage());
                    return Mono.just(Map.of());
                });

        List<String> candidates = candidatesOf(question);
        Mono<List<Map<String, Object>>> matched = candidates.isEmpty()
                ? Mono.just(List.of())
                : Flux.fromIterable(candidates)
                        .flatMap(keyword -> traceDataClient.search(keyword)
                                .map(data -> {
                                    Map<String, Object> hit = new LinkedHashMap<>();
                                    hit.put("关键词", keyword);
                                    // 瘦身 + 重排：单条全链路近万字，不裁剪会把门店流向挤出 prompt 上限；
                                    // 即便裁剪后仍超长，PromptFactory 是「从头截断」，所以把下游放前面
                                    hit.put("记录", downstreamFirst(PayloadCondenser.condense(data)));
                                    return hit;
                                })
                                .onErrorResume(e -> {
                                    log.info("管理端问答检索 {} 未命中: {}", keyword, e.getMessage());
                                    return Mono.empty();
                                }))
                        .collectList();

        return Mono.zip(overview, matched)
                .flatMapMany(tuple -> {
                    Map<String, Object> overviewData = tuple.getT1();
                    List<Map<String, Object>> hits = tuple.getT2();

                    Map<String, Object> data = new LinkedHashMap<>();
                    // 顺序很关键：PromptFactory 超长时从尾部截断。
                    // 用户点名了具体对象时，「命中记录」比「平台总览」重要得多，所以放前面。
                    if (!hits.isEmpty()) {
                        data.put("按问题检索到的记录", hits);
                    }
                    data.put("平台总览", PayloadCondenser.condense(overviewData));
                    if (overviewData.isEmpty() && hits.isEmpty()) {
                        data.put("数据可用性", "未取到任何数据");
                    }

                    log.info("管理端问答 问题长度={} 检索标识={} 命中={}",
                            question == null ? 0 : question.length(), candidates, hits.size());

                    String prompt = promptFactory.adminAsk(question, data);
                    return callDify(prompt, "admin-" + (userId == null ? "guest" : userId), conversationId);
                });
    }

    /**
     * 从问题里挑出可能指向具体业务对象的标识。
     *
     * 只认「字母数字混合」的 token：批次号（SP20261027-001-05）、溯源码（QR-PORK-xxx）、
     * 车牌（沪A12345）都符合；纯英文单词（Vaccine、today）一律跳过，
     * 免得把普通词拿去查库，白打一次下游。
     */
    private List<String> candidatesOf(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = CANDIDATE_PATTERN.matcher(question);
        while (matcher.find() && found.size() < MAX_CANDIDATES) {
            String token = matcher.group();
            if (token.chars().anyMatch(Character::isDigit)) {
                found.add(token);
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * 把 downstream（流向门店）提到最前面。
     *
     * PromptFactory 超长时是从尾部截断的，而监管最关心的恰恰是「货去哪了」；
     * 排在 upstream/slaughter 后面的话，数据一长就先被切掉。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> downstreamFirst(Object condensed) {
        if (!(condensed instanceof Map<?, ?> map)) {
            return Map.of("记录", condensed);
        }
        Map<String, Object> source = (Map<String, Object>) map;
        Map<String, Object> ordered = new LinkedHashMap<>();
        Object downstream = source.get("downstream");
        if (downstream != null) {
            ordered.put("下游流向", downstream);
        }
        source.forEach(ordered::putIfAbsent);
        return ordered;
    }

    /**
     * 健康检查：拿一句最短的话探一下 Dify 通不通。
     */
    public boolean ping() {
        try {
            String answer = difyClient.chatBlocking(
                            DifyChatCommand.builder().query("回复 ok").user("health-check").build())
                    .block(java.time.Duration.ofSeconds(30));
            return answer != null && !answer.isBlank();
        } catch (Exception e) {
            log.warn("Dify 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    private Flux<DifyStreamEvent> callDify(String prompt, String user, String conversationId) {
        return difyClient.streamChat(DifyChatCommand.builder()
                .query(prompt)
                .user(user)
                .conversationId(conversationId)
                .inputs(Map.of())
                .build());
    }
}
