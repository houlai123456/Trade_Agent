package com.quantai.annotation;

import java.lang.annotation.*;

/**
 * 幂等性注解
 * 用于防止接口重复提交
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等性key的前缀
     * 默认使用方法签名作为key
     */
    String prefix() default "";

    /**
     * 幂等性key，支持SpEL表达式
     * 例如: "#{#request.orderId}"
     * 如果为空，则使用 prefix + 方法签名 + 参数hash
     */
    String key() default "";

    /**
     * 幂等性保护时间（秒）
     * 在此时间内相同请求会被拒绝
     */
    long expireTime() default 10;

    /**
     * 重复提交时的错误信息
     */
    String message() default "请勿重复提交";
}
