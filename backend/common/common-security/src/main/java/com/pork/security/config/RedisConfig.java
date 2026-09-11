package com.pork.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置，为 Sa-Token 提供 RedisTemplate
 */
@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisConfig {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)  // 只有容器里有这个 Bean 才注册
    // 命名为 saTokenRedisTemplate，避免与 Redisson starter 自动配置的 redisTemplate 重名冲突
    public RedisTemplate<String, Object> saTokenRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        return template;
    }
}
