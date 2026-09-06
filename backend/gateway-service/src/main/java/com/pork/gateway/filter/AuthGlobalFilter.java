package com.pork.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    // 鉴权白名单（这些接口不需要登录即可访问）
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/login",
            "/api/auth/register"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 判断是否在白名单中
        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }

        // 2. 鉴权校验
        try {
            // 获取请求头中的 Token (Sa-Token 默认 header 名为 satoken)
            String token = request.getHeaders().getFirst("satoken");
            if (token == null || token.trim().isEmpty()) {
                return buildErrorResponse(exchange, 401, "未登录或Token已过期");
            }
            
            // 校验 Token 有效性
            StpUtil.checkLogin();
            
            // 3. Token 验证通过后，将 userId 放入 Header 传递给下游微服务
            // 这样后端服务（如 auth-service、breeding-service）直接从 Header 拿 userId，无需重复查 Redis
            Object loginId = StpUtil.getLoginId();
            ServerHttpRequest newRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(loginId))
                    .build();
            exchange = exchange.mutate().request(newRequest).build();

        } catch (Exception e) {
            return buildErrorResponse(exchange, 401, "鉴权失败：" + e.getMessage());
        }

        // 4. 打印请求日志
        log.info("网关转发请求: {} | IP: {}", path, request.getRemoteAddress());

        // 5. 放行，将请求交给下一级（转发给具体的微服务）
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100; // 过滤器优先级，数字越小优先级越高
    }

    /**
     * 判断路径是否在白名单中
     */
    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /**
     * 构造统一 JSON 错误响应
     */
    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        
        String result = String.format("{\"code\":%d,\"msg\":\"%s\"}", code, message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(result.getBytes())));
    }
}