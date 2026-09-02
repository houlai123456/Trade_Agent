package com.quantai.service.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {

    /** 执行是否成功 */
    private boolean success;

    /** Agent 输出内容（给下一个 Agent 或最终用户） */
    private String output;

    /** 错误信息（如果失败） */
    private String error;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /** Token 消耗 */
    private int tokenUsed;

    /** Agent 执行轮次 */
    private int rounds;

    /** 额外的结构化数据（可选） */
    private Map<String, Object> metadata;

    /**
     * 创建成功结果
     */
    public static AgentResult success(String output, long durationMs, int tokenUsed, int rounds) {
        return AgentResult.builder()
                .success(true)
                .output(output)
                .durationMs(durationMs)
                .tokenUsed(tokenUsed)
                .rounds(rounds)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static AgentResult failure(String error, long durationMs) {
        return AgentResult.builder()
                .success(false)
                .error(error)
                .durationMs(durationMs)
                .build();
    }
}
