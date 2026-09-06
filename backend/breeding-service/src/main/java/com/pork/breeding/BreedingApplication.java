package com.pork.breeding;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 养殖免疫模块启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.pork.breeding.mapper")
public class BreedingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BreedingApplication.class, args);
    }
}