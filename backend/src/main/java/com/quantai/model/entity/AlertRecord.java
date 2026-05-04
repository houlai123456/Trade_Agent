package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异动预警记录
 */
@Data
@TableName("alert_record")
public class AlertRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 股票代码 */
    private String code;

    /** 股票名称 */
    private String name;

    /** 预警类型：PRICE-涨跌幅异动，VOLUME-成交量异动 */
    private String alertType;

    /** 预警描述 */
    private String description;

    /** 当前价格 */
    private BigDecimal currentPrice;

    /** 涨跌幅（%） */
    private BigDecimal changePercent;

    /** 成交量 */
    private Long volume;

    /** 是否已读（0-未读，1-已读） */
    private Integer readFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
