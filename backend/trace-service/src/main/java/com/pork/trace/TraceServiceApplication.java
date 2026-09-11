package com.pork.trace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 应急追溯服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.pork") // 扫描公共模块（common-web/common-security 等）
@EnableDiscoveryClient
@MapperScan("com.pork.trace.mapper") // 扫描 Mapper 接口
public class TraceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraceServiceApplication.class, args);
    }
}