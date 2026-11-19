package com.pork.ai.client;

import com.pork.ai.exception.TraceDataUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TraceDataClient 的回归测试。
 *
 * 这里锁死的两个行为都是线上踩过的坑：
 *   1. qrCode 直接拼 URL 导致路径穿越（可越权读到 /dashboard/overview）
 *   2. 下游故障被静默降级成空数据（AI 会输出"建议不要购买"）
 */
class TraceDataClientTest {

    private static final String TRACE_URL = "http://trace-service";

    /** 记录实际发出去的 URL，用来断言编码结果 */
    private final AtomicReference<String> capturedUrl = new AtomicReference<>();

    private TraceDataClient clientReturning(String jsonBody) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    capturedUrl.set(request.url().toString());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(jsonBody)
                            .build());
                })
                .build();
        return new TraceDataClient(webClient, TRACE_URL);
    }

    private TraceDataClient clientReturningStatus(HttpStatus status, String body) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    capturedUrl.set(request.url().toString());
                    return Mono.just(ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build());
                })
                .build();
        return new TraceDataClient(webClient, TRACE_URL);
    }

    @Test
    @DisplayName("正常溯源码：路径保持原样，不被改写")
    void normalQrCodeKeepsPath() {
        TraceDataClient client = clientReturning("{\"code\":200,\"data\":{\"ok\":true}}");

        Map<String, Object> data = client.safeBuy("QR20261116001").block();

        assertEquals(TRACE_URL + "/trace/safe-buy/QR20261116001", capturedUrl.get());
        assertTrue(data.containsKey("ok"));
    }

    @Test
    @DisplayName("含点号的溯源码允许通过（真实码可能带版本号）")
    void dottedQrCodeAllowed() {
        TraceDataClient client = clientReturning("{\"code\":200,\"data\":{}}");

        client.safeBuy("QR.2026.001").block();

        assertEquals(TRACE_URL + "/trace/safe-buy/QR.2026.001", capturedUrl.get());
    }

    @Test
    @DisplayName("★ 路径穿越必须被拒绝，而不是把 /dashboard/overview 读出来")
    void pathTraversalRejected() {
        TraceDataClient client = clientReturning("{\"code\":200,\"data\":{}}");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> client.safeBuy("../../dashboard/overview"));

        assertTrue(ex.getMessage().contains("非法字符"));
        // 关键：异常必须在发出请求之前抛出，不能有任何下游调用
        assertEquals(null, capturedUrl.get());
    }

    @Test
    @DisplayName("斜杠 / 反斜杠一并拒绝")
    void slashRejected() {
        TraceDataClient client = clientReturning("{\"code\":200,\"data\":{}}");

        assertThrows(IllegalArgumentException.class, () -> client.safeBuy("QR/2026/001"));
        assertThrows(IllegalArgumentException.class, () -> client.safeBuy("QR\\2026"));
        assertThrows(IllegalArgumentException.class, () -> client.safeBuy("QR 2026"));
        assertEquals(null, capturedUrl.get());
    }

    @Test
    @DisplayName("超长参数被拒绝，避免把超长 URL 打到下游")
    void overlongRejected() {
        TraceDataClient client = clientReturning("{\"code\":200,\"data\":{}}");

        String longCode = "A".repeat(129);
        assertThrows(IllegalArgumentException.class, () -> client.safeBuy(longCode));
    }

    @Test
    @DisplayName("空参数被拒绝")
    void blankRejected() {
        TraceDataClient client = clientReturning("{\"code\":200,\"data\":{}}");

        assertThrows(IllegalArgumentException.class, () -> client.safeBuy("   "));
        assertThrows(IllegalArgumentException.class, () -> client.safeBuy(null));
    }

    @Test
    @DisplayName("★ 业务码非 200 必须抛异常，不能伪装成空数据")
    void businessErrorThrows() {
        TraceDataClient client = clientReturning("{\"code\":5000,\"message\":\"系统内部异常\",\"data\":null}");

        // 注意：safeBuy 返回的是惰性 Mono，必须订阅（block）才会真正发起请求
        assertThrows(TraceDataUnavailableException.class,
                () -> client.safeBuy("QR001").block());
    }

    @Test
    @DisplayName("★ 下游 5xx 必须抛异常，不能伪装成空数据")
    void serverErrorThrows() {
        TraceDataClient client = clientReturningStatus(HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        assertThrows(TraceDataUnavailableException.class,
                () -> client.safeBuy("QR001").block());
    }

    @Test
    @DisplayName("业务码 200 但 data 为空：属于“没有记录”，应正常返回空 Map")
    void emptyDataIsNotFailure() {
        TraceDataClient client = clientReturning("{\"code\":200,\"message\":\"操作成功\",\"data\":null}");

        Map<String, Object> data = client.safeBuy("QR-NOT-EXIST").block();

        assertTrue(data.isEmpty());
    }
}
