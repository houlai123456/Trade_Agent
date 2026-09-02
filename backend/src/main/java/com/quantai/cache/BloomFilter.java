package com.quantai.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 布隆过滤器（基于 Redis Bitmap 实现）
 * 用于防止缓存穿透
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int[] SEEDS = {3, 5, 7, 11, 13, 17, 19, 23};
    private static final long SIZE = 1L << 32; // 2^32

    /**
     * 添加元素到布隆过滤器
     */
    public void add(String key, String value) {
        for (int seed : SEEDS) {
            long hash = hash(value, seed);
            redisTemplate.opsForValue().setBit(key, hash, true);
        }
    }

    /**
     * 判断元素是否可能存在
     * @return true-可能存在，false-一定不存在
     */
    public boolean mightContain(String key, String value) {
        for (int seed : SEEDS) {
            long hash = hash(value, seed);
            Boolean bit = redisTemplate.opsForValue().getBit(key, hash);
            if (bit == null || !bit) {
                return false;
            }
        }
        return true;
    }

    /**
     * 清空布隆过滤器
     */
    public void clear(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 计算hash值
     */
    private long hash(String value, int seed) {
        long result = 0;
        int len = value.length();
        for (int i = 0; i < len; i++) {
            result = seed * result + value.charAt(i);
        }
        return (SIZE - 1) & result;
    }
}
