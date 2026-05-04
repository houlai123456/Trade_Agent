package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易流水
 */
@Data
@TableName("trade_order")
public class TradeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 股票代码 */
    private String code;

    /** 股票名称 */
    private String name;

    /** BUY-买入 SELL-卖出 */
    private String tradeType;

    /** 成交价格 */
    private BigDecimal price;

    /** 成交数量 */
    private Integer quantity;

    /** 成交金额 */
    private BigDecimal amount;

    /** 盈亏（卖出时计算） */
    private BigDecimal profitLoss;

    /** DONE-已完成 PENDING-挂单中 CANCELLED-已撤单 */
    private String status;

    /** MARKET-市价单 LIMIT-限价单 */
    private String orderType;

    /** 交易时间 */
    private LocalDateTime tradeTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
