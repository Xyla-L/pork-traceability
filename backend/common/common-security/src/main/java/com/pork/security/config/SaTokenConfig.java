package com.pork.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRouter.match("/**")
                    // 放行不需要登录的接口
                    .notMatch("/login/**", "/register/**", "/doc.html", "/webjars/**", "/swagger-resources/**", "/v3/api-docs/**")
                    .check(r -> {
                        // TODO: 登录校验，后续接入具体业务时启用
                        // StpUtil.checkLogin();
                    });
        })).addPathPatterns("/**");
    }
}