package com.pork.ingest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 设备/第三方系统接入服务（B 档自动化录入的唯一入口）。
 * <p>
 * 职责边界：只做「收 → 校验 → 暂存 → 分发」四件事，不写业务表。
 * 设备数据一律先落 ingest_staging 留档，校验通过后才调用业务服务写入，
 * 保证「自动采集 ≠ 自动生效」，错误数据不会被自动上链。
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.pork")
@MapperScan("com.pork.ingest.mapper")
public class IngestApplication {
    public static void main(String[] args) {
        SpringApplication.run(IngestApplication.class, args);
    }
}
