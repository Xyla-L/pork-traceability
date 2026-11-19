package com.pork.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI 助手服务启动类。
 *
 * 职责边界：本服务不直接连数据库，只做三件事
 *   1. 聚合下游业务服务（trace-service）的溯源数据
 *   2. 把数据加工成角色化的 Prompt
 *   3. 代理 Dify（千问）的流式响应，转成 SSE 给前端
 *
 * 这样设计的好处：模型能力被隔离在一个服务里，将来换 Dify / 换成 OpenAI 兼容网关，
 * 其他微服务一行都不用改。
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
