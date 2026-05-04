package com.quantai.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警记录视图对象
 */
@Data
public class AlertVO {
    private Long id;
    private String code;
    private String name;
    private String alertType;
    private String description;
    private BigDecimal currentPrice;
    private BigDecimal changePercent;
    private Long volume;
    private Integer readFlag;
    private LocalDateTime createTime;
}
