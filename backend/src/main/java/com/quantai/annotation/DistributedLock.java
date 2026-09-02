package com.quantai.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的key，支持SpEL表达式
     * 例如: "trade:lock:#{#userId}"
     */
    String key();

    /**
     * 等待获取锁的最长时间（秒）
     * 默认10秒，超时抛出异常
     */
    long waitTime() default 10;

    /**
     * 锁自动释放时间（秒）
     * 默认30秒，防止死锁
     */
    long leaseTime() default 30;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 获取锁失败时的错误信息
     */
    String failMessage() default "获取锁失败，请稍后重试";
}
