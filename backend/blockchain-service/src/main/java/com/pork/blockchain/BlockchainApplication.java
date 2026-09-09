package com.pork.blockchain;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.pork.blockchain.mapper")
@SpringBootApplication(scanBasePackages = "com.pork")
public class BlockchainApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlockchainApplication.class, args);
    }
}
