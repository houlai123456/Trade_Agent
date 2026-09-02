package com.quantai.model.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 回测请求DTO
 */
@Data
public class BacktestRequest {

    /**
     * 股票代码列表（可批量回测）
     */
    private List<String> stockCodes;

    /**
     * 回测开始日期
     */
    private LocalDate startDate;

    /**
     * 回测结束日期
     */
    private LocalDate endDate;

    /**
     * 初始资金
     */
    private Double initialCapital = 100000.0;

    /**
     * 持仓周期（天）
     */
    private Integer holdingDays = 7;

    /**
     * 止盈阈值（%）
     */
    private Double takeProfitThreshold = 10.0;

    /**
     * 止损阈值（%）
     */
    private Double stopLossThreshold = -5.0;

    /**
     * 手续费率（%）
     */
    private Double commissionRate = 0.03;

    /**
     * 是否启用参数扫描
     */
    private Boolean enableParamScan = false;

    /**
     * 参数扫描范围 - 持仓天数
     */
    private List<Integer> holdingDaysList;

    /**
     * 参数扫描范围 - 止盈阈值
     */
    private List<Double> takeProfitList;

    /**
     * 参数扫描范围 - 止损阈值
     */
    private List<Double> stopLossList;
}
