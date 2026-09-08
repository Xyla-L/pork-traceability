package com.pork.gateway.filter;

import com.pork.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private static final String BLACKLIST_PREFIX = "auth:blacklist:token:";
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/consumer/",
            "/actuator/health", "/doc.html", "/v3/api-docs", "/swagger-ui"
    );

    private final ReactiveStringRedisTemplate redis;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (isPublic(path) || "OPTIONS".equals(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        String token = bearer(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null || !JwtUtil.isValid(token) || !"access".equals(JwtUtil.getTokenType(token))) {
            return unauthorized(exchange, "未认证或令牌已失效");
        }

        return redis.hasKey(BLACKLIST_PREFIX + token)
                .onErrorReturn(true)
                .flatMap(blacklisted -> {
                    if (blacklisted) return unauthorized(exchange, "未认证或令牌已失效");
                    String userId = JwtUtil.getUsername(token);
                    String role = JwtUtil.getRole(token);
                    if (userId == null || role == null) return unauthorized(exchange, "令牌声明不完整");
                    ServerHttpRequest forwarded = request.mutate().headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                        headers.set("X-User-Id", userId);
                        headers.set("X-User-Role", role);
                    }).build();
                    log.info("gateway_forward path={} userId={} role={}", path, userId, role);
                    return chain.filter(exchange.mutate().request(forwarded).build());
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null,\"traceId\":\""
                + UUID.randomUUID() + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
