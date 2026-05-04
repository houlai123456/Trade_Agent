package com.quantai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 多Agent协同分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationResult {
    private String stockCode;
    private String stockName;
    private List<AgentStep> steps;   // Agent执行流水线
    private TradeSuggestion suggestion; // 最终建议
    private long totalDurationMs;    // 总耗时
}
