package com.pork.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监管接口的安全配置。
 *
 * 默认开启角色校验。本地用 curl / 浏览器直连 8089 调试时没有网关注入的
 * X-User-Role 头，可以临时关掉：
 *   set AI_ROLE_CHECK=false   (PowerShell: $env:AI_ROLE_CHECK="false")
 * 但任何对外部署都必须保持开启。
 */
@Data
@ConfigurationProperties(prefix = "ai.security")
public class AdminSecurityProperties {

    /** 是否校验监管接口的角色，默认开启 */
    private boolean roleCheckEnabled = true;
}
