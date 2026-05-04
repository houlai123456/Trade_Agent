package com.quantai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 交易建议Agent 输出
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSuggestion {
    private String action;           // BUY / SELL / HOLD
    private String actionLabel;      // 买入 / 卖出 / 观望
    private String confidence;       // HIGH / MEDIUM / LOW
    private String reason;           // LLM生成的建议理由（markdown格式）
    private String riskWarning;      // 风险提示
    private BigDecimal suggestedPrice; // 参考价格
    private String suggestionSummary; // 一句话总结
}
