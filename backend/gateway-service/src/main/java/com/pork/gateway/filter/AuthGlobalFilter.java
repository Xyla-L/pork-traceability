package com.pork.gateway.filter;

import com.pork.core.source.OperatorHeader;
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
    private static final String COMPLAINTS_PATH = "/api/v1/trace/complaints";
    /**
     * 服务间内部接口前缀：仅供服务在容器网络内直连调用（如养殖场档案同步登记机构）。
     * 网关一律不转发，返回 404，避免外部调用者绕过机构管理的 ADMIN 限制。
     */
    private static final String INTERNAL_ONLY_PREFIX = "/api/v1/auth/internal/";
    /**
     * 消费者侧只读接口（小程序无需登录即可访问）：
     * 扫码溯源 / 安心购 / 一键验真 / 溯源搜索
     */
    private static final List<String> CONSUMER_READ_PATHS = List.of(
            "/api/v1/trace/safe-buy", "/api/v1/trace/verify"
    );
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh", "/api/v1/consumer/",
            "/actuator/health", "/doc.html", "/v3/api-docs", "/swagger-ui",
            "/api/v1/trace/search", "/api/v1/file/upload", "/api/v1/file/",
            // 设备上报不走 JWT：用设备级密钥 X-Device-Key 鉴权，只放行设备入口，
            // /api/v1/ingest/staging、/api/v1/ingest/devices 是管理端接口，仍须登录
            "/api/v1/ingest/device/"
    );

    private final ReactiveStringRedisTemplate redis;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        // 服务间内部接口不对外暴露
        if (path.startsWith(INTERNAL_ONLY_PREFIX)) {
            return notFound(exchange);
        }
        if (isPublic(path, request.getMethod().name()) || "OPTIONS".equals(request.getMethod().name())) {
            // 公开路径同样剥离身份头，防止调用方伪造 X-User-Id / X-User-Role / X-User-Name
            ServerHttpRequest stripped = request.mutate().headers(headers -> {
                headers.remove("X-User-Id");
                headers.remove("X-User-Role");
                headers.remove("X-User-Name");
            }).build();
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        String token = bearer(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null || !JwtUtil.isValid(token) || !"access".equals(JwtUtil.getTokenType(token))) {
            return unauthorized(exchange, "未认证或令牌已失效");
        }

        return redis.hasKey(BLACKLIST_PREFIX + token)
                .doOnError(e -> log.error("gateway_redis_check_failed path={} err={}", path, e.toString()))
                .onErrorReturn(true)
                .flatMap(blacklisted -> {
                    if (blacklisted) return unauthorized(exchange, "未认证或令牌已失效");
                    String userId = JwtUtil.getUsername(token);
                    String role = JwtUtil.getRole(token);
                    if (userId == null || role == null) return unauthorized(exchange, "令牌声明不完整");
                    ServerHttpRequest forwarded = request.mutate().headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                        headers.remove("X-User-Name");
                        headers.set("X-User-Id", userId);
                        headers.set("X-User-Role", role);
                        // 关键环节人工动作要落到具体人头上：姓名单独透传（老令牌无该声明时退回用户ID）。
                        // 姓名可能是中文，HTTP 头只安全承载 ASCII（否则会被按 ISO-8859-1 编成 '?'，
                        // 业务表里就变成「?????」），所以这里先 URL 编码，各服务读头后解码。
                        String userName = JwtUtil.getClaim(token, "userName");
                        String displayName = userName == null || userName.isBlank() ? userId : userName;
                        headers.set("X-User-Name", OperatorHeader.encode(displayName));
                    }).build();
                    log.info("gateway_forward path={} userId={} role={}", path, userId, role);
                    return chain.filter(exchange.mutate().request(forwarded).build());
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublic(String path, String method) {
        if (PUBLIC_PATHS.stream().anyMatch(p -> path.equals(p) || path.startsWith(p))) {
            return true;
        }
        // 举报提交(POST)/列表与详情(GET)对小程序公开；handle 等写操作必须认证
        if (path.equals(COMPLAINTS_PATH)) {
            return true;
        }
        // 消费者侧只读接口（安心购 / 一键验真）对小程序公开，仅限 GET
        if ("GET".equals(method) && CONSUMER_READ_PATHS.stream().anyMatch(path::startsWith)) {
            return true;
        }
        return "GET".equals(method) && path.startsWith(COMPLAINTS_PATH + "/");
    }

    private String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        log.warn("gateway_unauthorized path={} reason={}", exchange.getRequest().getURI().getPath(), message);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null,\"traceId\":\""
                + UUID.randomUUID() + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    /** 内部接口一律按「不存在」处理：不暴露接口的存在性 */
    private Mono<Void> notFound(ServerWebExchange exchange) {
        log.warn("gateway_blocked_internal path={}", exchange.getRequest().getURI().getPath());
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":404,\"message\":\"资源不存在\",\"data\":null,\"traceId\":\""
                + UUID.randomUUID() + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
