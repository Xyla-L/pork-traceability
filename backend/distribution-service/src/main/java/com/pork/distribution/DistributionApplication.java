package com.pork.distribution;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.pork.distribution.mapper")
@SpringBootApplication(scanBasePackages = "com.pork")
public class DistributionApplication {
    public static void main(String[] args) {
        SpringApplication.run(DistributionApplication.class, args);
    }
}
