package com.quantai.model.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 行情数据视图对象
 */
@Data
public class StockQuoteVO {
    private String code;
    private String name;
    private BigDecimal currentPrice;
    private BigDecimal openPrice;
    private BigDecimal yesterdayClose;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal changePercent;
    private BigDecimal changeAmount;
    private BigDecimal turnoverRate;
    private BigDecimal peRatio;
    private BigDecimal amplitude;
}
