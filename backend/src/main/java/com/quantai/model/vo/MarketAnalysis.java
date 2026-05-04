package com.quantai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场分析Agent 输出 — 纯数据驱动，不调用LLM
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketAnalysis {
    private String stockCode;
    private String stockName;

    // 趋势判断
    private String trend;            // UP_TREND / DOWN_TREND / SIDEWAYS
    private String trendDescription; // 可读描述

    // MA指标
    private BigDecimal currentPrice;
    private BigDecimal ma5;
    private BigDecimal ma10;
    private BigDecimal ma20;
    private String maStatus;         // 价格在MA之上/之下/之间

    // 量能
    private Long latestVolume;
    private Double avgVolume5;
    private String volumeAnalysis;   // 放量/缩量/正常

    // 近期表现
    private BigDecimal changePercent5; // 近5日涨跌幅
    private Integer consecutiveDirection; // 连续上涨/下跌天数（正=涨，负=跌）

    // K线形态关键词
    private String candlePattern;    // 大阳线/大阴线/十字星/...
}
