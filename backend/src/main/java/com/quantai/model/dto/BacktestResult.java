package com.quantai.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 回测结果DTO
 */
@Data
public class BacktestResult {

    /**
     * 回测参数
     */
    private BacktestParams params;

    /**
     * 基础指标
     */
    private BasicMetrics basicMetrics;

    /**
     * 风险指标
     */
    private RiskMetrics riskMetrics;

    /**
     * 交易记录
     */
    private List<Trade> trades;

    /**
     * 收益曲线（按日）
     */
    private List<EquityPoint> equityCurve;

    /**
     * 参数扫描结果（仅当enableParamScan=true时）
     */
    private List<ParamScanResult> paramScanResults;

    @Data
    public static class BacktestParams {
        private LocalDate startDate;
        private LocalDate endDate;
        private Double initialCapital;
        private Integer holdingDays;
        private Double takeProfitThreshold;
        private Double stopLossThreshold;
        private Double commissionRate;
    }

    @Data
    public static class BasicMetrics {
        private Double totalReturn;              // 总收益率（%）
        private Double annualizedReturn;         // 年化收益率（%）
        private Double finalCapital;             // 期末资金
        private Double totalProfit;              // 总盈利
        private Integer totalTrades;             // 总交易次数
        private Integer winningTrades;           // 盈利交易次数
        private Integer losingTrades;            // 亏损交易次数
        private Double winRate;                  // 胜率（%）
        private Double averageProfit;            // 平均盈利
        private Double averageLoss;              // 平均亏损
        private Double profitFactor;             // 盈亏比（总盈利/总亏损）
    }

    @Data
    public static class RiskMetrics {
        private Double maxDrawdown;              // 最大回撤（%）
        private LocalDate maxDrawdownDate;       // 最大回撤日期
        private Double sharpeRatio;              // 夏普比率
        private Double volatility;               // 收益波动率（年化）
        private Double calmarRatio;              // 卡尔玛比率（年化收益/最大回撤）
        private Integer maxConsecutiveLosses;    // 最大连续亏损次数
        private Double averageDrawdown;          // 平均回撤
    }

    @Data
    public static class Trade {
        private String stockCode;
        private String stockName;
        private LocalDate entryDate;             // 买入日期
        private LocalDate exitDate;              // 卖出日期
        private BigDecimal entryPrice;           // 买入价格
        private BigDecimal exitPrice;            // 卖出价格
        private Integer shares;                  // 股数
        private Double returnRate;               // 收益率（%）
        private Double profit;                   // 盈亏金额
        private String exitReason;               // 退出原因（TAKE_PROFIT/STOP_LOSS/HOLDING_PERIOD）
        private String suggestion;               // Agent建议（BUY/SELL/HOLD）
        private Double confidence;               // 置信度
    }

    @Data
    public static class EquityPoint {
        private LocalDate date;
        private Double equity;                   // 账户净值
        private Double dailyReturn;              // 当日收益率（%）
        private Double drawdown;                 // 当前回撤（%）
    }

    @Data
    public static class ParamScanResult {
        private Integer holdingDays;
        private Double takeProfitThreshold;
        private Double stopLossThreshold;
        private Double totalReturn;
        private Double sharpeRatio;
        private Double maxDrawdown;
        private Double winRate;
        private Integer totalTrades;
    }
}
