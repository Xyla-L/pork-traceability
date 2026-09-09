package com.pork.auth.security;

import com.pork.auth.entity.SysUser;
import com.pork.auth.mapper.SysUserMapper;
import com.pork.security.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BLACKLIST_PREFIX = "auth:blacklist:token:";
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redis;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = bearer(request);
        if (token != null && JwtUtil.isValid(token) && "access".equals(JwtUtil.getTokenType(token))
                && !Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + token))) {
            String subject = JwtUtil.getUsername(token);
            SysUser user = findUser(subject);
            if (user != null && Integer.valueOf(1).equals(user.getStatus())) {
                var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(subject, token, List.of(authority)));
            }
        }
        chain.doFilter(request, response);
    }

    private SysUser findUser(String subject) {
        if (subject == null || subject.isBlank()) return null;
        try {
            return userMapper.selectById(Long.valueOf(subject));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
