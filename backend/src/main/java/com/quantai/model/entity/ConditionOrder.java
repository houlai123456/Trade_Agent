package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("condition_order")
public class ConditionOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String code;

    private String name;

    /** BUY-买入 SELL-卖出 */
    private String direction;

    /** ABOVE-高于 BELOW-低于 */
    private String conditionType;

    /** 触发价格 */
    private BigDecimal triggerPrice;

    /** 数量（股） */
    private Integer quantity;

    /** 限价(null=市价) */
    private BigDecimal orderPrice;

    /** PENDING-等待中 TRIGGERED-已触发 CANCELLED-已取消 EXPIRED-已过期 */
    private String status;

    /** 触发后生成的订单ID */
    private Long triggeredOrderId;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 过期时间 */
    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
