package com.quantai.aspect;

import com.quantai.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式锁切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.quantai.annotation.DistributedLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock lockAnnotation = method.getAnnotation(DistributedLock.class);

        String lockKey = parseLockKey(lockAnnotation.key(), method, joinPoint.getArgs());
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(
                    lockAnnotation.waitTime(),
                    lockAnnotation.leaseTime(),
                    lockAnnotation.timeUnit()
            );

            if (!acquired) {
                log.warn("获取分布式锁失败: {}", lockKey);
                throw new RuntimeException(lockAnnotation.failMessage());
            }

            log.debug("获取分布式锁成功: {}", lockKey);
            return joinPoint.proceed();

        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁: {}", lockKey);
            }
        }
    }

    private String parseLockKey(String keyExpression, Method method, Object[] args) {
        if (!keyExpression.contains("#")) {
            return keyExpression;
        }

        EvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = signature.getParameterNames(method);
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }

    private static class signature {
        static String[] getParameterNames(Method method) {
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            String[] names = new String[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                names[i] = parameters[i].getName();
            }
            return names;
        }
    }
}
