package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * K线数据
 */
@Data
@TableName("stock_kline")
public class StockKline {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 股票代码 */
    private String code;

    /** 日期 */
    private LocalDate date;

    /** K线周期：DAY-日K，WEEK-周K，MONTH-月K */
    private String period;

    /** 开盘价 */
    private BigDecimal openPrice;

    /** 收盘价 */
    private BigDecimal closePrice;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
