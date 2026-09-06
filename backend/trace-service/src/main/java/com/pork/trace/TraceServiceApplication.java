package com.pork.trace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应急追溯服务启动类
 */
@SpringBootApplication
@MapperScan("com.pork.trace.mapper") // 扫描 Mapper 接口
public class TraceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TraceServiceApplication.class, args);
    }
}