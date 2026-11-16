package com.pork.ai.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 向 Dify 发请求用的 WebClient。
 *
 * 为什么用 WebClient 而不是 RestTemplate：
 *   Dify 的流式回答是一条长连接不断吐数据，RestTemplate 会把整个响应读完才返回，
 *   做不到"边生成边显示"。WebClient 基于 Reactor，可以直接拿到 Flux 流。
 *
 * 注意：引了 webflux 依赖但没引 webflux 的 starter，所以 Spring MVC 仍然是主容器，
 *      Controller 依然能用 SseEmitter —— 这样前端拿到的是标准 SSE，不用改造成 WebFlux 全栈。
 */
@Configuration
@RequiredArgsConstructor
public class DifyClientConfig {

    private final DifyProperties properties;

    @Bean("difyWebClient")
    public WebClient difyWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                // 千问本地部署，首 token 可能等几秒，长报告更久
                .responseTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(properties.getTimeoutSeconds(), TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 报告类回答可能很长，放宽编解码缓冲
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /** 给 trace-service 用的普通 JSON 客户端，不走流式 */
    @Bean("plainWebClient")
    public WebClient plainWebClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }
}
