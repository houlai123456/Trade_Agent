package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 虚拟账户
 */
@Data
@TableName("trade_account")
public class TradeAccount {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（单用户模式，固定为1） */
    private Long userId;

    /** 总资产（可用资金 + 冻结资金 + 持仓市值） */
    private BigDecimal totalAssets;

    /** 可用资金 */
    private BigDecimal availableBalance;

    /** 冻结资金 */
    private BigDecimal frozenBalance;

    /** 持仓市值 */
    private BigDecimal marketValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
