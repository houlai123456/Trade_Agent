package com.quantai.service.impl;

import com.quantai.service.LlmService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
public class LlmServiceImpl implements LlmService {

    @Autowired
    private ChatModel chatModel;

    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public String callWithTimeout(List<Message> messages, int timeoutSeconds) throws TimeoutException {
        CircuitBreaker circuitBreaker = getCircuitBreaker();

        if (circuitBreaker != null && circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.warn("LLM 熔断器已开启，直接降级");
            return fallback(messages, new RuntimeException("Circuit breaker is OPEN"));
        }

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model("deepseek-chat")
                        .temperature(0.7)
                        .maxTokens(2000)
                        .build();

                Prompt prompt = new Prompt(messages, options);
                String response = chatModel.call(prompt)
                        .getResult()
                        .getOutput()
                        .getContent();

                if (circuitBreaker != null) {
                    circuitBreaker.onSuccess(0, TimeUnit.MILLISECONDS);
                }

                return response;
            } catch (Exception e) {
                if (circuitBreaker != null) {
                    circuitBreaker.onError(0, TimeUnit.MILLISECONDS, e);
                }
                throw new RuntimeException("LLM 调用失败", e);
            }
        }, executor);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("LLM 调用超时 {}秒", timeoutSeconds);
            future.cancel(true);
            if (circuitBreaker != null) {
                circuitBreaker.onError(timeoutSeconds * 1000L, TimeUnit.MILLISECONDS, e);
            }
            return fallback(messages, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("LLM 调用被中断", e);
            return fallback(messages, e);
        } catch (ExecutionException e) {
            log.error("LLM 调用执行失败", e.getCause());
            return fallback(messages, e.getCause());
        }
    }

    @Override
    public String call(List<Message> messages) {
        try {
            return callWithTimeout(messages, DEFAULT_TIMEOUT_SECONDS);
        } catch (TimeoutException e) {
            log.warn("使用默认超时 {}秒 仍然超时", DEFAULT_TIMEOUT_SECONDS);
            return fallback(messages, e);
        }
    }

    @Override
    public String fallback(List<Message> messages, Throwable cause) {
        log.warn("LLM 调用失败，启用降级策略: {}", cause.getMessage());

        // 降级策略1: 检查是否是常见问题
        String lastUserMessage = extractLastUserMessage(messages);
        if (lastUserMessage != null) {
            if (lastUserMessage.contains("买入") || lastUserMessage.contains("卖出")) {
                return """
                        {
                          "suggestion": "HOLD",
                          "reason": "AI 分析服务暂时不可用，建议暂缓交易，谨慎观望",
                          "confidence": 0.5
                        }
                        """;
            }

            if (lastUserMessage.contains("行情") || lastUserMessage.contains("价格")) {
                return "AI 分析服务暂时不可用，请直接查看实时行情数据。您可以使用股票代码查询最新价格、涨跌幅等信息。";
            }
        }

        // 降级策略2: 通用友好提示
        return "AI 分析服务暂时不可用，请稍后重试。您可以查看实时行情数据或历史交易记录。";
    }

    private CircuitBreaker getCircuitBreaker() {
        if (circuitBreakerRegistry == null) {
            return null;
        }
        try {
            return circuitBreakerRegistry.circuitBreaker("llm");
        } catch (Exception e) {
            log.debug("未配置 LLM 熔断器");
            return null;
        }
    }

    private String extractLastUserMessage(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.getMessageType().getValue())) {
                return msg.getContent();
            }
        }
        return null;
    }
}
