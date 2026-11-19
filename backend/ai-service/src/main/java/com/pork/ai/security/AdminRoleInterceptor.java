package com.pork.ai.security;

import com.pork.ai.config.AdminSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 监管端接口的角色校验。
 *
 * 为什么需要它：
 *   /ai/admin/report 会真的调用一次千问推理（实验室服务器上的 GPU），
 *   原来没有任何校验，任何人拿到地址就能刷，既烧算力也泄露监管数据。
 *
 * 为什么校验的是 header 而不是再解析一次 JWT：
 *   网关的 AuthGlobalFilter 已经验过签名、查过黑名单，并把 X-User-Id / X-User-Role
 *   注入给下游。ai-service 只消费这两个头，不重复解析令牌，
 *   这样密钥只有网关一份，轮换时不用改多处。
 *
 * ⚠️ 前提假设：ai-service 不直接暴露公网，只接受网关转发。
 *    如果有人能直连 8089 并自带 X-User-Role: ADMIN，这套校验就形同虚设。
 *    部署时请用安全组 / 内网 ACL 限制 8089 的访问来源，或只映射网关端口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminRoleInterceptor implements HandlerInterceptor {

    /** 允许访问监管数据的角色：监管人员 + 系统管理员 */
    private static final Set<String> ALLOWED_ROLES = Set.of("SUPERVISOR", "ADMIN");

    private final AdminSecurityProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.isRoleCheckEnabled()) {
            log.warn("监管接口角色校验已关闭（ai.security.role-check-enabled=false），仅限本地调试使用");
            return true;
        }

        String role = request.getHeader("X-User-Role");
        String userId = request.getHeader("X-User-Id");

        if (role == null || role.isBlank() || !ALLOWED_ROLES.contains(role.trim().toUpperCase())) {
            log.warn("拒绝访问监管接口 path={} userId={} role={}",
                    request.getRequestURI(), userId, role);
            writeForbidden(response);
            return false;
        }

        log.info("监管接口访问放行 userId={} role={}", userId, role);
        return true;
    }

    /**
     * 直接写 JSON 而不是 SSE：
     * 拦截器在 Controller 之前执行，此时还没建立事件流，写 JSON 更直接，
     * 前端 fetch 收到 403 后按非 2xx 分支处理即可。
     */
    private void writeForbidden(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":403,\"message\":\"无权访问监管数据\",\"data\":null,"
                        + "\"timestamp\":" + System.currentTimeMillis() + "}");
        response.getWriter().flush();
    }
}
