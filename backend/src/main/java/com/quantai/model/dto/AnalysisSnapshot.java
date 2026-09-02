package com.quantai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 分析快照 - 用于会话记忆
 * 缓存完整分析结果，避免短时间内重复分析同一股票
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisSnapshot {

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 分析类型（FULL-完整分析, FUNDAMENTAL-仅基本面, TECHNICAL-仅技术面, SENTIMENT-仅情绪面）
     */
    private String analysisType;

    /**
     * 分析时间
     */
    private LocalDateTime analyzedAt;

    /**
     * 各维度输出（key: 维度名, value: 分析结果）
     */
    private Map<String, String> dimensionOutputs;

    /**
     * 最终投资建议（BUY/SELL/HOLD）
     */
    private String finalSuggestion;

    /**
     * 置信度（HIGH/MEDIUM/LOW）
     */
    private String confidence;

    /**
     * 加权评分（0-100）
     */
    private Integer weightedScore;

    /**
     * 风险评分（0-100）
     */
    private Integer riskScore;

    /**
     * 是否发生风险干预
     */
    private Boolean riskOverride;

    /**
     * 风险干预前的原始建议
     */
    private String originalSuggestion;

    /**
     * 完整报告（用于返回给用户）
     */
    private String fullReport;

    /**
     * 获取快照年龄（秒）
     */
    public long getAgeInSeconds() {
        return Duration.between(analyzedAt, LocalDateTime.now()).getSeconds();
    }

    /**
     * 判断快照是否过期（默认1小时）
     */
    public boolean isExpired(long ttlSeconds) {
        return getAgeInSeconds() > ttlSeconds;
    }
}
