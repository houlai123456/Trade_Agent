package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 持仓记录
 */
@Data
@TableName("trade_position")
public class TradePosition {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 股票代码 */
    private String code;

    /** 股票名称 */
    private String name;

    /** 持有数量 */
    private Integer quantity;

    /** 可用数量（T+1，简化处理与quantity一致） */
    private Integer availableQuantity;

    /** 成本均价 */
    private BigDecimal costPrice;

    /** 总成本 */
    private BigDecimal totalCost;

    /** 当前价格（实时更新） */
    private BigDecimal currentPrice;

    /** 最新市值 */
    private BigDecimal marketValue;

    /** 浮动盈亏 */
    private BigDecimal profitLoss;

    /** 盈亏百分比 */
    private BigDecimal plRatio;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
