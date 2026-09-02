package com.quantai.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent建议追踪表（回测系统核心实体）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionTracking {

    private Long id;

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 建议类型：BUY/SELL/HOLD
     */
    private String suggestion;

    /**
     * 置信度：HIGH/MEDIUM/LOW
     */
    private String confidence;

    /**
     * 建议时间
     */
    private LocalDateTime suggestedAt;

    /**
     * 建议时价格
     */
    private BigDecimal suggestedPrice;

    /**
     * 目标价格
     */
    private BigDecimal targetPrice;

    /**
     * 加权评分(0-100)
     */
    private Integer weightedScore;

    /**
     * 基本面评分(0-100)
     */
    private Integer fundamentalScore;

    /**
     * 技术面评分(0-100)
     */
    private Integer technicalScore;

    /**
     * 情绪面评分(0-100)
     */
    private Integer sentimentScore;

    /**
     * 风险评分(0-100)
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
     * 7天后实际价格
     */
    private BigDecimal actualPrice7d;

    /**
     * 30天后实际价格
     */
    private BigDecimal actualPrice30d;

    /**
     * 7天收益率(%)
     */
    private BigDecimal return7d;

    /**
     * 30天收益率(%)
     */
    private BigDecimal return30d;

    /**
     * 7天建议是否准确
     */
    private Boolean accuracy7d;

    /**
     * 30天建议是否准确
     */
    private Boolean accuracy30d;

    /**
     * 回测状态：PENDING-待回测 PARTIAL-7天完成 COMPLETED-全部完成
     */
    private String backtestStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
