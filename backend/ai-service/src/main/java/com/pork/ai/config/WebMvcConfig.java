package com.pork.ai.config;

import com.pork.ai.security.AdminRoleInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 只拦监管路径，消费者端保持匿名可访问。
 *
 * 为什么不拦 /ai/**：
 *   小程序扫码是匿名场景，没有 token，拦了就用不了。
 *   角色边界在网关上还有一层（/api/v1/ai/consumer/** 在 PUBLIC_PATHS 里，
 *   /api/v1/ai/admin/** 必须带有效 JWT），这里只是第二道闸。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminRoleInterceptor adminRoleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/ai/admin/**");
    }
}
