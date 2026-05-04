package com.quantai.model.vo;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * K线数据视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlineVO {
    private LocalDate date;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal changePercent;
    private BigDecimal ma5;
    private BigDecimal ma10;
    private BigDecimal ma20;
}
