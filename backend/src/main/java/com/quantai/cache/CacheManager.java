package com.quantai.cache;

import com.quantai.cache.BloomFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存管理器
 * 集成布隆过滤器 + 随机TTL + 缓存空值
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final BloomFilter bloomFilter;
    private final Random random = new Random();

    private static final String BLOOM_KEY_PREFIX = "bloom:";
    private static final String NULL_VALUE = "NULL";
    private static final long NULL_VALUE_TTL = 60; // 空值缓存1分钟

    /**
     * 获取缓存，带布隆过滤器防穿透
     * @param key 缓存key
     * @param baseExpireSeconds 基础过期时间（秒）
     * @param loader 数据加载函数
     * @param clazz 返回类型
     */
    public <T> T get(String key, long baseExpireSeconds, Supplier<T> loader, Class<T> clazz) {
        // 1. 先查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_VALUE.equals(cached)) {
                log.debug("命中空值缓存: {}", key);
                return null;
            }
            return clazz.cast(cached);
        }

        // 2. 布隆过滤器检查（防止查询不存在的key）
        String bloomKey = BLOOM_KEY_PREFIX + extractBloomCategory(key);
        if (!bloomFilter.mightContain(bloomKey, key)) {
            log.debug("布隆过滤器判定key不存在: {}", key);
            // 缓存空值，防止短时间内重复查询
            cacheNull(key);
            return null;
        }

        // 3. 查询数据库
        T value = loader.get();
        if (value == null) {
            // 缓存空值
            cacheNull(key);
            return null;
        }

        // 4. 写入缓存（随机TTL防止雪崩）
        long expireTime = randomExpireTime(baseExpireSeconds);
        redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.SECONDS);
        log.debug("写入缓存: key=, ttl=s", key, expireTime);

        return value;
    }

    /**
     * 设置缓存，带随机TTL
     */
    public void set(String key, Object value, long baseExpireSeconds) {
        if (value == null) {
            cacheNull(key);
            return;
        }
        long expireTime = randomExpireTime(baseExpireSeconds);
        redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.SECONDS);
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 添加到布隆过滤器
     */
    public void addToBloom(String category, String key) {
        String bloomKey = BLOOM_KEY_PREFIX + category;
        bloomFilter.add(bloomKey, key);
    }

    /**
     * 批量添加到布隆过滤器
     */
    public void batchAddToBloom(String category, Iterable<String> keys) {
        String bloomKey = BLOOM_KEY_PREFIX + category;
        for (String key : keys) {
            bloomFilter.add(bloomKey, key);
        }
    }

    /**
     * 生成随机TTL（基础时间 ± 20%）
     * 防止缓存雪崩
     */
    private long randomExpireTime(long baseSeconds) {
        // 浮动范围：基础时间的 80%-120%
        double factor = 0.8 + random.nextDouble() * 0.4;
        return (long) (baseSeconds * factor);
    }

    /**
     * 缓存空值，防止缓存穿透
     */
    private void cacheNull(String key) {
        redisTemplate.opsForValue().set(key, NULL_VALUE, NULL_VALUE_TTL, TimeUnit.SECONDS);
    }

    /**
     * 从缓存key中提取布隆过滤器分类
     * 例如: "stock:quote:600519" -> "stock:quote"
     */
    private String extractBloomCategory(String key) {
        int lastColon = key.lastIndexOf(':');
        return lastColon > 0 ? key.substring(0, lastColon) : key;
    }
}
