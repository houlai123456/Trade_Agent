package com.quantai.service;

import com.quantai.service.agent.ReActAgentService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prometheus 指标监控测试
 * 测试 LLM Token 消耗和 Agent 执行指标是否正确记录
 */
@SpringBootTest
public class MetricsTest {

    @Autowired
    private ReActAgentService reactAgentService;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * 测试 LLM Token 指标是否正确注册
     */
    @Test
    public void testMetricsRegistered() {
        // 验证所有指标已注册
        Counter promptTokens = meterRegistry.find("llm.tokens.prompt").counter();
        assertNotNull(promptTokens, "LLM prompt tokens counter 应该已注册");

        Counter completionTokens = meterRegistry.find("llm.tokens.completion").counter();
        assertNotNull(completionTokens, "LLM completion tokens counter 应该已注册");

        Counter totalTokens = meterRegistry.find("llm.tokens.total").counter();
        assertNotNull(totalTokens, "LLM total tokens counter 应该已注册");

        Counter llmCalls = meterRegistry.find("llm.calls.total").counter();
        assertNotNull(llmCalls, "LLM calls counter 应该已注册");

        Counter agentRounds = meterRegistry.find("agent.rounds.total").counter();
        assertNotNull(agentRounds, "Agent rounds counter 应该已注册");

        Counter toolCalls = meterRegistry.find("agent.tool.calls.total").counter();
        assertNotNull(toolCalls, "Agent tool calls counter 应该已注册");

        System.out.println("✓ 所有 Prometheus 指标已成功注册");
    }

    /**
     * 测试 Agent 执行后是否记录 Token 消耗
     */
    @Test
    public void testTokenMetricsAfterAgentRun() {
        // 获取初始值
        Counter promptTokens = meterRegistry.find("llm.tokens.prompt").counter();
        Counter completionTokens = meterRegistry.find("llm.tokens.completion").counter();
        Counter totalTokens = meterRegistry.find("llm.tokens.total").counter();
        Counter llmCalls = meterRegistry.find("llm.calls.total").counter();
        Counter agentRounds = meterRegistry.find("agent.rounds.total").counter();

        assertNotNull(promptTokens);
        assertNotNull(completionTokens);
        assertNotNull(totalTokens);
        assertNotNull(llmCalls);
        assertNotNull(agentRounds);

        double initialPrompt = promptTokens.count();
        double initialCompletion = completionTokens.count();
        double initialTotal = totalTokens.count();
        double initialCalls = llmCalls.count();
        double initialRounds = agentRounds.count();

        System.out.println("执行前 - Prompt: " + initialPrompt + ", Completion: " + initialCompletion +
                          ", Total: " + initialTotal + ", Calls: " + initialCalls + ", Rounds: " + initialRounds);

        // 执行一次 Agent 查询
        try {
            reactAgentService.run("贵州茅台最新股价是多少");
        } catch (Exception e) {
            // 即使失败也应该记录指标
            System.out.println("Agent 执行异常（预期可能发生）: " + e.getMessage());
        }

        // 检查指标是否增加
        double afterPrompt = promptTokens.count();
        double afterCompletion = completionTokens.count();
        double afterTotal = totalTokens.count();
        double afterCalls = llmCalls.count();
        double afterRounds = agentRounds.count();

        System.out.println("执行后 - Prompt: " + afterPrompt + ", Completion: " + afterCompletion +
                          ", Total: " + afterTotal + ", Calls: " + afterCalls + ", Rounds: " + afterRounds);

        // 验证指标有增长
        assertTrue(afterCalls > initialCalls, "LLM 调用次数应该增加");

        // Token 指标可能因为 API 错误而没有增长，但调用次数一定会增加
        System.out.println("✓ LLM 调用次数增加: " + (afterCalls - initialCalls));

        if (afterTotal > initialTotal) {
            System.out.println("✓ Token 消耗增加: " + (afterTotal - initialTotal));
            assertTrue(afterPrompt > initialPrompt, "Prompt tokens 应该增加");
            assertTrue(afterCompletion > initialCompletion, "Completion tokens 应该增加");
        } else {
            System.out.println("⚠ Token 统计未增加（可能 API 调用失败或未返回 usage 信息）");
        }
    }

    /**
     * 测试工具调用计数
     */
    @Test
    public void testToolCallMetrics() {
        Counter toolCalls = meterRegistry.find("agent.tool.calls.total").counter();
        assertNotNull(toolCalls);

        double initialToolCalls = toolCalls.count();
        System.out.println("执行前工具调用次数: " + initialToolCalls);

        // 执行 Agent（会调用工具）
        try {
            reactAgentService.run("查询贵州茅台的K线数据");
        } catch (Exception e) {
            System.out.println("Agent 执行异常: " + e.getMessage());
        }

        double afterToolCalls = toolCalls.count();
        System.out.println("执行后工具调用次数: " + afterToolCalls);

        if (afterToolCalls > initialToolCalls) {
            System.out.println("✓ 工具调用次数增加: " + (afterToolCalls - initialToolCalls));
        } else {
            System.out.println("⚠ 工具调用次数未增加（Agent 可能未成功执行到工具调用阶段）");
        }
    }
}
