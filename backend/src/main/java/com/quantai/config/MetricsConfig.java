package com.quantai.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus 指标配置
 * 用于监控 LLM 调用 Token 消耗和性能
 */
@Configuration
public class MetricsConfig {

    /**
     * LLM Prompt Tokens 计数器
     */
    @Bean
    public Counter llmPromptTokensCounter(MeterRegistry registry) {
        return Counter.builder("llm.tokens.prompt")
                .description("LLM prompt tokens consumed")
                .tag("model", "deepseek-chat")
                .register(registry);
    }

    /**
     * LLM Completion Tokens 计数器
     */
    @Bean
    public Counter llmCompletionTokensCounter(MeterRegistry registry) {
        return Counter.builder("llm.tokens.completion")
                .description("LLM completion tokens consumed")
                .tag("model", "deepseek-chat")
                .register(registry);
    }

    /**
     * LLM Total Tokens 计数器
     */
    @Bean
    public Counter llmTotalTokensCounter(MeterRegistry registry) {
        return Counter.builder("llm.tokens.total")
                .description("LLM total tokens consumed")
                .tag("model", "deepseek-chat")
                .register(registry);
    }

    /**
     * LLM 调用次数计数器
     */
    @Bean
    public Counter llmCallCounter(MeterRegistry registry) {
        return Counter.builder("llm.calls.total")
                .description("Total LLM API calls")
                .tag("model", "deepseek-chat")
                .register(registry);
    }

    /**
     * LLM 调用失败计数器
     */
    @Bean
    public Counter llmErrorCounter(MeterRegistry registry) {
        return Counter.builder("llm.errors.total")
                .description("Total LLM API call errors")
                .tag("model", "deepseek-chat")
                .register(registry);
    }

    /**
     * LLM 调用耗时计时器
     */
    @Bean
    public Timer llmCallTimer(MeterRegistry registry) {
        return Timer.builder("llm.call.duration")
                .description("LLM API call duration")
                .tag("model", "deepseek-chat")
                .register(registry);
    }

    /**
     * Agent 执行轮次计数器
     */
    @Bean
    public Counter agentRoundsCounter(MeterRegistry registry) {
        return Counter.builder("agent.rounds.total")
                .description("Total agent execution rounds")
                .tag("type", "react")
                .register(registry);
    }

    /**
     * Agent 工具调用计数器
     */
    @Bean
    public Counter agentToolCallCounter(MeterRegistry registry) {
        return Counter.builder("agent.tool.calls.total")
                .description("Total agent tool calls")
                .register(registry);
    }
}
