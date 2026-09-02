package com.quantai.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置（分布式锁）
 */
@Configuration
@ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;

        if (password != null && !password.isEmpty()) {
            config.useSingleServer()
                    .setAddress(address)
                    .setPassword(password)
                    .setConnectionPoolSize(8)
                    .setConnectionMinimumIdleSize(2);
        } else {
            config.useSingleServer()
                    .setAddress(address)
                    .setConnectionPoolSize(8)
                    .setConnectionMinimumIdleSize(2);
        }

        return Redisson.create(config);
    }
}
