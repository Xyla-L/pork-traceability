package com.pork.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 智能助手服务。
 * <p>
 * 定位：**只读问答与归纳**。它把别的服务已经算好的结论翻译成人话，
 * 不参与任何业务判定——瘦肉精、免疫前置、批次唯一性这些硬闸门仍由各业务服务的判定器负责，
 * 这里不会、也不允许给出「能不能过」的第二种答案。
 */
@SpringBootApplication(scanBasePackages = "com.pork")
@EnableDiscoveryClient
@MapperScan("com.pork.agent.mapper")
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
