package com.pork.security.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Slf4j
public class JwtUtil {

    private static final String DEFAULT_SECRET = "pork-traceability-secret-key-2026";
    private static final long ACCESS_EXPIRE_TIME = 2 * 60 * 60 * 1000L;
    private static final long REFRESH_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;

    public static String generateToken(String username) {
        return generateToken(username, null, "access", ACCESS_EXPIRE_TIME);
    }

    public static String generateRefreshToken(String username) {
        return generateToken(username, null, "refresh", REFRESH_EXPIRE_TIME);
    }

    public static String generateToken(String username, String role) {
        return generateToken(username, role, "access", ACCESS_EXPIRE_TIME);
    }

    public static String generateRefreshToken(String username, String role) {
        return generateToken(username, role, "refresh", REFRESH_EXPIRE_TIME);
    }

    private static String generateToken(String username, String role, String type, long ttl) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        if (role != null && !role.isBlank()) payload.put("role", role);
        payload.put("tokenType", type);
        payload.put("iat", new Date());
        payload.put("exp", new Date(System.currentTimeMillis() + ttl));
        return JWTUtil.createToken(payload, secret());
    }

    public static String getUsername(String token) {
        try {
            if (!isValid(token)) return null;
            JWT jwt = JWTUtil.parseToken(token);
            Object username = jwt.getPayloads().getObj("username");
            if (username == null) {
                return null;
            }
            return username.toString();
        } catch (Exception e) {
            log.error("解析 JWT token 失败: {}", e.getMessage());
            return null;
        }
    }

    public static String getTokenType(String token) {
        try {
            if (!isValid(token)) return null;
            Object type = JWTUtil.parseToken(token).getPayloads().getObj("tokenType");
            return type == null ? null : type.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String getRole(String token) {
        try {
            if (!isValid(token)) return null;
            Object role = JWTUtil.parseToken(token).getPayloads().getObj("role");
            return role == null ? null : role.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isValid(String token) {
        return token != null && JWTUtil.verify(token, secret()) && !isExpired(token);
    }

    public static boolean isExpired(String token) {
        try {
            return expirationMillis(token) <= System.currentTimeMillis();
        } catch (Exception e) {
            log.error("校验 JWT token 过期时间失败: {}", e.getMessage());
            return true;
        }
    }

    public static String refreshToken(String token) {
        try {
            if (!isValid(token)) return null;
            JWT jwt = JWTUtil.parseToken(token);
            String username = jwt.getPayloads().getObj("username").toString();
            if (username == null) {
                return null;
            }
            return generateToken(username);
        } catch (Exception e) {
            log.error("刷新 JWT token 失败: {}", e.getMessage());
            return null;
        }
    }

    public static long remainingSeconds(String token) {
        long millis = expirationMillis(token) - System.currentTimeMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private static long expirationMillis(String token) {
        JWT jwt = JWTUtil.parseToken(token);
        Object value = jwt.getPayloads().getObj("exp");
        if (value == null) return 0;
        long millis = value instanceof Date date ? date.getTime() : Long.parseLong(value.toString());
        return millis < 1_000_000_000_000L ? millis * 1000 : millis;
    }

    private static byte[] secret() {
        String configured = System.getProperty("security.jwt-secret");
        if (configured == null || configured.isBlank()) configured = System.getenv("JWT_SECRET");
        if (configured == null || configured.isBlank()) configured = DEFAULT_SECRET;
        return configured.getBytes(StandardCharsets.UTF_8);
    }
}
