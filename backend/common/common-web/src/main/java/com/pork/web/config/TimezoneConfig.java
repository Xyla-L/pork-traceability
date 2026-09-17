package com.pork.web.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 统一 JVM 默认时区。
 * LocalDateTime.now() 等使用系统默认时区，若运行环境（如 UTC 容器）时区不符，
 * 会导致实际发车/到达时间等业务时间整体偏差 8 小时，这里显式固定为 Asia/Shanghai。
 */
@Configuration
public class TimezoneConfig {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }
}
