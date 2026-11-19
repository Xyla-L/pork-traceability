package com.pork.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dify 连接配置。
 *
 * 为什么用 ConfigurationProperties 而不是直接 @Value：
 * 参数超过 3 个、且有分组含义时，集中成一个对象更好维护，
 * 也方便以后加多套 App（消费者/监管各一个 Key）时扩展成 Map。
 */
@Data
@ConfigurationProperties(prefix = "dify")
public class DifyProperties {

    /** Dify 服务根地址，不带 /v1，例如 http://127.0.0.1:9090 */
    private String baseUrl;

    /** 应用 API Key（app- 开头） */
    private String apiKey;

    /** 单次推理超时（秒） */
    private int timeoutSeconds = 120;

    /** SSE 推送超时（毫秒） */
    private long sseTimeoutMillis = 180_000L;
}
