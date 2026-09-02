package com.quantai.service.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 执行上下文 - 在多个 Agent 之间传递数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

    /** 用户原始问题 */
    private String userQuestion;

    /** 股票代码（如果适用） */
    private String stockCode;

    /** 股票名称（如果适用） */
    private String stockName;

    /** 元数据（可存储任意额外信息，如当前价格、市值等） */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /** 前序 Agent 的输出结果（key=AgentName, value=AgentResult） */
    @Builder.Default
    private Map<String, AgentResult> previousResults = new HashMap<>();

    /** 共享数据池（Agent 之间可以存取任意数据） */
    @Builder.Default
    private Map<String, Object> sharedData = new HashMap<>();

    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * 添加前序 Agent 的结果
     */
    public void addPreviousResult(String agentName, AgentResult result) {
        if (previousResults == null) {
            previousResults = new HashMap<>();
        }
        previousResults.put(agentName, result);
    }

    /**
     * 获取前序 Agent 的输出
     */
    public String getPreviousOutput(String agentName) {
        if (previousResults == null || !previousResults.containsKey(agentName)) {
            return null;
        }
        return previousResults.get(agentName).getOutput();
    }

    /**
     * 检查是否有前序 Agent 执行失败
     */
    public boolean hasFailed() {
        if (previousResults == null || previousResults.isEmpty()) {
            return false;
        }
        return previousResults.values().stream().anyMatch(r -> !r.isSuccess());
    }
}
