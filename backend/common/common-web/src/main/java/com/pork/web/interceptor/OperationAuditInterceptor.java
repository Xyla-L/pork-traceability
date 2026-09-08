package com.pork.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Slf4j
public class OperationAuditInterceptor implements HandlerInterceptor {
    private static final String START_NANOS = OperationAuditInterceptor.class.getName() + ".startNanos";
    private static final Set<String> AUDITED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (AUDITED_METHODS.contains(request.getMethod())) request.setAttribute(START_NANOS, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        Object started = request.getAttribute(START_NANOS);
        if (!(started instanceof Long start)) return;
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        String operation = handler instanceof HandlerMethod method
                ? method.getBeanType().getSimpleName() + "." + method.getMethod().getName()
                : handler.getClass().getSimpleName();
        log.info("operation_audit userId={} role={} method={} path={} operation={} ip={} status={} durationMs={} result={}",
                header(request, "X-User-Id", "anonymous"), header(request, "X-User-Role", "PUBLIC"),
                request.getMethod(), request.getRequestURI(), operation, clientIp(request), response.getStatus(), elapsedMs,
                exception == null ? "SUCCESS" : exception.getClass().getSimpleName());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",", 2)[0].trim();
        return request.getRemoteAddr();
    }

    private String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
