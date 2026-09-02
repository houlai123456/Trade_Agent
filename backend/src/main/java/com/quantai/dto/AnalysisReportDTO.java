package com.quantai.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 分析报告 DTO
 * 规范化 Agent 输出格式，确保结构一致性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReportDTO {

    /**
     * 分析摘要（核心结论）
     */
    private String summary;

    /**
     * 详细分析内容
     */
    private String analysis;

    /**
     * 技术指标汇总
     * 例如：{"MA5": 150.5, "MA20": 145.2, "RSI": 65.3}
     */
    private Map<String, Double> technicalIndicators;

    /**
     * 交易建议：BUY / HOLD / SELL
     */
    private Recommendation recommendation;

    /**
     * 建议理由
     */
    private String reason;

    /**
     * 置信度：HIGH / MEDIUM / LOW
     */
    private Confidence confidence;

    /**
     * 风险等级：HIGH / MEDIUM / LOW
     */
    private RiskLevel riskLevel;

    /**
     * 风险提示
     */
    private String riskWarning;

    /**
     * 数据质量评分 (0-100)
     */
    private Integer dataQualityScore;

    /**
     * 使用的数据源列表
     */
    private String[] dataSources;

    public enum Recommendation {
        BUY("买入"),
        HOLD("持有"),
        SELL("卖出");

        private final String label;

        Recommendation(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Confidence {
        HIGH("高"),
        MEDIUM("中"),
        LOW("低");

        private final String label;

        Confidence(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum RiskLevel {
        HIGH("高风险"),
        MEDIUM("中等风险"),
        LOW("低风险");

        private final String label;

        RiskLevel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 转换为用户友好的文本格式
     */
    public String toFormattedText() {
        StringBuilder sb = new StringBuilder();

        if (summary != null && !summary.isBlank()) {
            sb.append("【摘要】\n").append(summary).append("\n\n");
        }

        if (analysis != null && !analysis.isBlank()) {
            sb.append("【详细分析】\n").append(analysis).append("\n\n");
        }

        if (recommendation != null) {
            sb.append("【交易建议】").append(recommendation.getLabel());
            if (confidence != null) {
                sb.append("（置信度：").append(confidence.getLabel()).append("）");
            }
            sb.append("\n");
        }

        if (reason != null && !reason.isBlank()) {
            sb.append("理由：").append(reason).append("\n\n");
        }

        if (riskLevel != null) {
            sb.append("【风险评估】").append(riskLevel.getLabel()).append("\n");
            if (riskWarning != null && !riskWarning.isBlank()) {
                sb.append(riskWarning).append("\n");
            }
        }

        return sb.toString().trim();
    }
}
