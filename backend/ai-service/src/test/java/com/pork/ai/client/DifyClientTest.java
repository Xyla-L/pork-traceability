package com.pork.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.config.DifyProperties;
import com.pork.ai.dto.DifyChatCommand;
import com.pork.ai.dto.DifyStreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DifyClient 的 SSE 解析测试。
 *
 * 之前这段逻辑只有"跑起来看一眼"的人工验证，
 * 一旦换 Dify 版本或改解析，很容易悄悄退化（比如增量文本被吞、结束事件识别不到
 * 导致流永远不断开）。这几条用例把行为固定下来。
 */
class DifyClientTest {

    private DifyClient clientWithSseBody(String body) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(body)
                        .build()))
                .build();
        DifyProperties props = new DifyProperties();
        props.setBaseUrl("http://dify");
        props.setApiKey("app-test");
        return new DifyClient(webClient, new ObjectMapper(), props);
    }

    private DifyChatCommand command() {
        return DifyChatCommand.builder()
                .query("你好")
                .user("test")
                .build();
    }

    @Test
    @DisplayName("正常流：逐条解析 message 增量，并在 message_end 停止")
    void parsesDeltasAndStopsAtEnd() {
        String sse = """
                data: {"event":"message","answer":"你","conversation_id":"c-1"}

                data: {"event":"message","answer":"好","conversation_id":"c-1"}

                data: {"event":"message_end","conversation_id":"c-1"}

                """;
        DifyClient client = clientWithSseBody(sse);

        List<DifyStreamEvent> events = client.streamChat(command()).collectList().block();

        assertEquals(3, events.size());
        assertEquals("你", events.get(0).getAnswer());
        assertEquals("好", events.get(1).getAnswer());
        assertEquals("message_end", events.get(2).getEvent());
        assertTrue(events.get(2).isEnd());
        // 会话 ID 要透出去，前端靠它续聊
        assertEquals("c-1", events.get(0).getConversationId());
    }

    @Test
    @DisplayName("非 JSON 片段（心跳/ping）被忽略，不影响后续解析")
    void ignoresNonJsonChunk() {
        String sse = """
                data: ping

                data: {"event":"message","answer":"ok"}

                data: {"event":"message_end"}

                """;
        DifyClient client = clientWithSseBody(sse);

        List<DifyStreamEvent> events = client.streamChat(command()).collectList().block();

        assertEquals(2, events.size());
        assertEquals("ok", events.get(0).getAnswer());
    }

    @Test
    @DisplayName("Dify 的 error 事件也会终止流，并带出错误信息")
    void errorEventEndsStream() {
        String sse = """
                data: {"event":"error","message":"model overloaded","code":"server_error"}

                """;
        DifyClient client = clientWithSseBody(sse);

        List<DifyStreamEvent> events = client.streamChat(command()).collectList().block();

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).getEvent());
        assertTrue(events.get(0).isEnd());
        assertEquals("model overloaded", events.get(0).getMessage());
        assertEquals("server_error", events.get(0).getCode());
    }

    @Test
    @DisplayName("Dify 返回 4xx 时抛异常，而不是返回一条空流")
    void httpErrorThrows() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"code\":\"unauthorized\",\"message\":\"Access token is invalid\"}")
                        .build()))
                .build();
        DifyProperties props = new DifyProperties();
        props.setBaseUrl("http://dify");
        props.setApiKey("app-wrong");
        DifyClient client = new DifyClient(webClient, new ObjectMapper(), props);

        assertThrows(IllegalStateException.class,
                () -> client.streamChat(command()).collectList().block());
    }

    @Test
    @DisplayName("describe() 不回显明文 Key")
    void masksApiKey() {
        DifyProperties props = new DifyProperties();
        props.setBaseUrl("http://dify");
        props.setApiKey("app-very-secret-key-123456");
        DifyClient client = new DifyClient(WebClient.builder().build(), new ObjectMapper(), props);

        String desc = client.describe();

        assertFalse(desc.contains("very-secret"));
        assertTrue(desc.contains("****"));
    }
}
