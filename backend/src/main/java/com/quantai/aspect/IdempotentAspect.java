package com.quantai.aspect;

import com.quantai.annotation.Idempotent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.quantai.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        String idempotentKey = generateIdempotentKey(idempotent, method, joinPoint.getArgs());

        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                idempotentKey,
                System.currentTimeMillis(),
                idempotent.expireTime(),
                TimeUnit.SECONDS
        );

        if (success == null || !success) {
            log.warn("检测到重复提交: {}", idempotentKey);
            throw new RuntimeException(idempotent.message());
        }

        try {
            log.debug("幂等性检查通过: {}", idempotentKey);
            return joinPoint.proceed();
        } catch (Throwable e) {
            redisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    private String generateIdempotentKey(Idempotent idempotent, Method method, Object[] args) {
        String prefix = idempotent.prefix().isEmpty()
                ? "idempotent:"
                : "idempotent:" + idempotent.prefix() + ":";

        if (!idempotent.key().isEmpty()) {
            String keyExpression = idempotent.key();
            if (keyExpression.contains("#")) {
                return prefix + parseSpEL(keyExpression, method, args);
            }
            return prefix + keyExpression;
        }

        String methodSignature = method.getDeclaringClass().getName() + "." + method.getName();
        String argsHash = generateArgsHash(args);
        return prefix + methodSignature + ":" + argsHash;
    }

    private String parseSpEL(String expression, Method method, Object[] args) {
        EvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = getParameterNames(method);
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        return parser.parseExpression(expression).getValue(context, String.class);
    }

    private String generateArgsHash(Object[] args) {
        if (args == null || args.length == 0) {
            return "noargs";
        }
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg == null ? "null" : arg.toString());
        }
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String[] getParameterNames(Method method) {
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            names[i] = parameters[i].getName();
        }
        return names;
    }
}
