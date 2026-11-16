package com.pork.ai.service;

import com.pork.ai.client.DifyClient;
import com.pork.ai.client.TraceDataClient;
import com.pork.ai.dto.DifyChatCommand;
import com.pork.ai.dto.DifyStreamEvent;
import com.pork.ai.prompt.PromptFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

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
