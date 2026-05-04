package com.quantai.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票实时行情（Redis缓存，不存数据库）
 */
@Data
public class StockQuote {
    /** 股票代码 */
    private String code;

    /** 股票名称 */
    private String name;

    /** 当前价格 */
    private BigDecimal currentPrice;

    /** 今日开盘价 */
    private BigDecimal openPrice;

    /** 昨日收盘价 */
    private BigDecimal yesterdayClose;

    /** 最高价 */
    private BigDecimal highPrice;

    /** 最低价 */
    private BigDecimal lowPrice;

    /** 成交量（股） */
    private Long volume;

    /** 成交额（元） */
    private BigDecimal amount;

    /** 涨跌幅（%） */
    private BigDecimal changePercent;

    /** 涨跌额 */
    private BigDecimal changeAmount;

    /** 换手率（%） */
    private BigDecimal turnoverRate;

    /** 市盈率 */
    private BigDecimal peRatio;

    /** 振幅（%） */
    private BigDecimal amplitude;

    /** 数据时间 */
    private LocalDateTime time;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
