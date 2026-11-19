package com.pork.ai.controller;

import com.pork.ai.config.DifyProperties;
import com.pork.ai.dto.DifyStreamEvent;
import com.pork.ai.exception.TraceDataUnavailableException;
import com.pork.ai.service.AiAssistService;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * AI 助手对外接口。
 *
 * 全部走 SSE（text/event-stream），事件协议：
 *   event: delta  data: {"text":"一小段增量"}      —— 前端追加到气泡里
 *   event: done   data: {"conversationId":"...","chars":123}  —— 整轮结束
 *   event: error  data: {"message":"..."}          —— 出错
 *
 * 为什么用 GET 而不是 POST：
 *   浏览器的 EventSource 只支持 GET，微信小程序的 uni.request chunked 也走 GET 最省事。
 *   AI 解读本身是"读操作"（不改数据），用 GET 语义上也成立。
 *   ⚠️ 但追问文本受 URL 长度限制（Tomcat 默认约 8KB），所以 q 限长 500 字，
 *      超过就返回 error 事件而不是让 Tomcat 丢一个 HTML 错误页回来。
 *
 * ⚠️ 参数为什么都写成 required = false：
 *   一旦交给 Spring 的参数绑定器抛异常，@RestControllerAdvice 产出的 JSON
 *   无法用 text/event-stream 协商，最终返回的是 Content-Length: 0 的 400，
 *   前端只能显示"返回异常（400）"，完全不知道错在哪。
 *   所以改成自己校验，错误也走 SSE error 事件，与正常响应保持同一套协议。
 */
@Slf4j
@Tag(name = "AI 助手", description = "内嵌实验室 Dify（千问）的双角色溯源助手")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    /** 溯源码上限，超过基本可以判定是攻击或前端 bug */
    private static final int MAX_QR_CODE_LEN = 128;
    /** 追问长度上限：500 个中文 ≈ 1500 字节，编码后仍远小于 URL 限制 */
    private static final int MAX_QUESTION_LEN = 500;
    /** 用户标识上限 */
    private static final int MAX_USER_LEN = 64;
    /** Dify 要求 conversation_id 必须是 UUID */
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final AiAssistService aiAssistService;
    private final DifyProperties difyProperties;

    @Operation(summary = "消费者端 · 一键解读溯源信息")
    @GetMapping(value = "/consumer/interpret", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter consumerInterpret(@RequestParam(required = false) String qrCode,
                                        @RequestParam(required = false) String user,
                                        @RequestParam(required = false) String conversationId) {
        return guarded(() -> {
            String code = requireText(qrCode, "qrCode", MAX_QR_CODE_LEN);
            return aiAssistService.consumerInterpret(code, normalizeUser(user), normalizeConversationId(conversationId));
        });
    }

    @Operation(summary = "消费者端 · 基于溯源数据追问")
    @GetMapping(value = "/consumer/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter consumerAsk(@RequestParam(required = false) String qrCode,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String user,
                                  @RequestParam(required = false) String conversationId) {
        return guarded(() -> {
            String code = requireText(qrCode, "qrCode", MAX_QR_CODE_LEN);
            String question = requireText(q, "q", MAX_QUESTION_LEN);
            return aiAssistService.consumerAsk(code, question,
                    normalizeUser(user), normalizeConversationId(conversationId));
        });
    }

    /**
     * 监管端简报。
     *
     * 权限由 AdminRoleInterceptor 统一校验（要求 X-User-Role 是 SUPERVISOR 或 ADMIN），
     * 这里只管业务。网关的 AuthGlobalFilter 已经把 /api/v1/ai/admin/** 排除在公开路径外，
     * 所以正常链路下这个头一定是由网关解析 JWT 后注入的。
     */
    @Operation(summary = "监管端 · 生成监管简报（需 SUPERVISOR/ADMIN）")
    @GetMapping(value = "/admin/report", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminReport(@RequestParam(required = false) String user,
                                  @RequestParam(required = false) String conversationId) {
        return guarded(() -> aiAssistService.adminReport(
                normalizeUser(user), normalizeConversationId(conversationId)));
    }

    /**
     * 监管端自由问答。
     *
     * 与 /admin/report 共用 AdminRoleInterceptor（/ai/admin/** 全部拦截），
     * 所以这里不再重复校验角色；数据取什么、怎么取在 AiAssistService.adminAsk 里。
     */
    @Operation(summary = "监管端 · 自由问答（需 SUPERVISOR/ADMIN）")
    @GetMapping(value = "/admin/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminAsk(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String user,
                               @RequestParam(required = false) String conversationId) {
        return guarded(() -> {
            String question = requireText(q, "q", MAX_QUESTION_LEN);
            return aiAssistService.adminAsk(question,
                    normalizeUser(user), normalizeConversationId(conversationId));
        });
    }

    /**
     * Dify 连通性自检。
     *
     * 两个改动：
     *   1. 依赖不可达时返回 HTTP 503，而不是 200 + code 200。
     *      原来那样写，K8s / 网关探针会把一个调不通模型的实例判定为健康。
     *   2. 不再回显 baseUrl —— 那是内网地址，没必要告诉调用方。
     */
    @Operation(summary = "Dify 连通性自检")
    @GetMapping("/health")
    public ResponseEntity<Result<Map<String, Object>>> health() {
        boolean ok = aiAssistService.ping();
        if (!ok) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(HttpStatus.SERVICE_UNAVAILABLE.value(), "AI 依赖（Dify）不可达"));
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("difyReachable", true);
        return ResponseEntity.ok(Result.success(info));
    }

    // ==================== 内部实现 ====================

    /**
     * 把「参数校验 / 取数」阶段的同步异常也转成 SSE error 事件。
     * 否则异常会冒到 Spring MVC，前端收到的是空 body 的 400。
     */
    private SseEmitter guarded(Supplier<Flux<DifyStreamEvent>> action) {
        try {
            return bridge(action.get());
        } catch (Exception e) {
            log.warn("AI 请求前置校验失败: {}", e.getMessage());
            return bridge(Flux.error(e));
        }
    }

    private String requireText(String value, String field, int maxLen) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String v = value.trim();
        if (v.length() > maxLen) {
            throw new IllegalArgumentException(field + " 过长，最多 " + maxLen + " 个字符");
        }
        return v;
    }

    /**
     * user 只用于 Dify 侧的会话隔离，不是安全边界，所以非法时降级成 guest 而不是报错。
     * 但必须过滤掉奇怪字符，否则会被写进 Dify 的会话记录里。
     */
    private String normalizeUser(String user) {
        if (user == null || user.isBlank()) {
            return null;
        }
        String v = user.trim();
        if (v.length() > MAX_USER_LEN || !v.matches("^[A-Za-z0-9_-]+$")) {
            log.debug("user 参数非法，降级为匿名");
            return null;
        }
        return v;
    }

    /** conversationId 不是 UUID 就当没传 —— 让 Dify 新建会话，而不是把垃圾值丢过去换一个 400 */
    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        String v = conversationId.trim();
        return UUID_PATTERN.matcher(v).matches() ? v : null;
    }

    /**
     * 把 Reactor 的流桥接到 Servlet 的 SseEmitter 上。
     *
     * 注意两点，这是 SSE 最容易踩的坑：
     *   1. 客户端断开后必须 dispose，否则 Dify 那边会一直推理，白烧算力
     *   2. emitter.send 是阻塞 IO，抛 IOException 就说明连接没了，立刻退订
     */
    private SseEmitter bridge(Flux<DifyStreamEvent> flux) {
        SseEmitter emitter = new SseEmitter(difyProperties.getSseTimeoutMillis());
        AtomicReference<Disposable> disposable = new AtomicReference<>();
        AtomicReference<String> conversationId = new AtomicReference<>("");
        StringBuilder full = new StringBuilder();

        disposable.set(flux.subscribe(
                event -> {
                    if (event.getConversationId() != null && !event.getConversationId().isBlank()) {
                        conversationId.set(event.getConversationId());
                    }
                    try {
                        if (event.isMessage()) {
                            full.append(event.getAnswer());
                            emitter.send(SseEmitter.event()
                                    .name("delta")
                                    .data(Map.of("text", event.getAnswer())));
                        } else if ("error".equals(event.getEvent())) {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(Map.of("message", nullToEmpty(event.getMessage()))));
                        }
                    } catch (IOException | IllegalStateException e) {
                        log.info("SSE 连接已断开，停止拉取: {}", e.getMessage());
                        dispose(disposable);
                    }
                },
                error -> {
                    log.error("AI 流式处理失败", error);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("message", friendlyMessage(error))));
                    } catch (Exception ignored) {
                        // 连接已断，无需再管
                    }
                    emitter.complete();
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data(Map.of(
                                        "conversationId", conversationId.get(),
                                        "chars", full.length())));
                    } catch (Exception ignored) {
                        // 客户端提前断开，正常情况
                    }
                    emitter.complete();
                }
        ));

        emitter.onTimeout(() -> {
            log.warn("SSE 超时，主动关闭");
            dispose(disposable);
            emitter.complete();
        });
        emitter.onCompletion(() -> dispose(disposable));
        emitter.onError(e -> dispose(disposable));

        return emitter;
    }

    /**
     * 错误信息脱敏。
     *
     * 原来直接把异常 message 发给前端，实测会漏出
     * "Connection refused: getsockopt: /127.0.0.1:9999" 这种内网拓扑信息。
     * 现在按异常类型给面向用户的文案，原始异常只进日志。
     */
    private String friendlyMessage(Throwable error) {
        if (error instanceof IllegalArgumentException) {
            // 参数问题是调用方自己的错，如实告知，不涉及内部信息
            return error.getMessage();
        }
        if (error instanceof TraceDataUnavailableException) {
            return "溯源数据暂时不可用，请稍后重试";
        }
        if (error instanceof WebClientRequestException) {
            return "AI 服务暂时不可达，请稍后重试";
        }
        return "AI 生成失败，请稍后重试";
    }

    private void dispose(AtomicReference<Disposable> ref) {
        Disposable d = ref.get();
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
