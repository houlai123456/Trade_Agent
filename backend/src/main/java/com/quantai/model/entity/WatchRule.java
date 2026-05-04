package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("watch_rule")
public class WatchRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String code;

    private String name;

    /** ABOVE-高于 BELOW-低于 */
    private String conditionType;

    private BigDecimal targetPrice;

    /** 1-启用 0-禁用 */
    private Integer enabled;

    /** 上次触发时间 */
    private LocalDateTime lastTriggeredTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
