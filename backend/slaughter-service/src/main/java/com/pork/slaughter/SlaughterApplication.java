package com.pork.slaughter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.pork")
@MapperScan("com.pork.slaughter.mapper")
public class SlaughterApplication {
    public static void main(String[] args) {
        SpringApplication.run(SlaughterApplication.class, args);
    }
}