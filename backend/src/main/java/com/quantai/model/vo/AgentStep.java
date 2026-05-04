package com.quantai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 执行步骤记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStep {
    private String agentName;       // 如 "新闻舆情Agent"
    private String description;     // 如 "分析新闻情绪倾向"
    private String status;          // completed / running / error
    private String inputSummary;    // 输入摘要
    private String outputSummary;   // 输出摘要
    private Object rawOutput;       // 原始输出（用于前端展开）
    private long durationMs;        // 耗时
}
