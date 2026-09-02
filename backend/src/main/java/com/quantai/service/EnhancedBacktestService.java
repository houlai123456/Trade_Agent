package com.quantai.service;

import com.quantai.mapper.SuggestionTrackingMapper;
import com.quantai.mapper.StockKlineMapper;
import com.quantai.model.dto.BacktestRequest;
import com.quantai.model.dto.BacktestResult;
import com.quantai.model.entity.StockKline;
import com.quantai.model.entity.SuggestionTracking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强版回测引擎
 * 支持：多指标分析、参数扫描、策略优化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedBacktestService {

    private final SuggestionTrackingMapper trackingMapper;
    private final StockKlineMapper klineMapper;

    /**
     * 执行回测
     */
    public BacktestResult runBacktest(BacktestRequest request) {
        log.info("[增强回测] 开始回测 - 股票: {}, 周期: {} ~ {}",
                request.getStockCodes(), request.getStartDate(), request.getEndDate());

        // 如果启用参数扫描，执行批量回测
        if (Boolean.TRUE.equals(request.getEnableParamScan())) {
            return runParameterScan(request);
        }

        // 执行单次回测
        return executeSingleBacktest(request);
    }

    /**
     * 执行单次回测
     */
    private BacktestResult executeSingleBacktest(BacktestRequest request) {
        BacktestResult result = new BacktestResult();

        // 设置回测参数
        BacktestResult.BacktestParams params = new BacktestResult.BacktestParams();
        params.setStartDate(request.getStartDate());
        params.setEndDate(request.getEndDate());
        params.setInitialCapital(request.getInitialCapital());
        params.setHoldingDays(request.getHoldingDays());
        params.setTakeProfitThreshold(request.getTakeProfitThreshold());
        params.setStopLossThreshold(request.getStopLossThreshold());
        params.setCommissionRate(request.getCommissionRate());
        result.setParams(params);

        // 获取该时段的所有建议
        List<SuggestionTracking> suggestions = getSuggestionsInRange(
                request.getStockCodes(), request.getStartDate(), request.getEndDate());

        if (suggestions.isEmpty()) {
            log.warn("[增强回测] 无建议数据，返回空结果");
            return createEmptyResult(result);
        }

        // 模拟交易
        List<BacktestResult.Trade> trades = new ArrayList<>();
        double currentCapital = request.getInitialCapital();
        double maxCapital = currentCapital;
        double maxDrawdown = 0.0;
        LocalDate maxDrawdownDate = request.getStartDate();

        for (SuggestionTracking suggestion : suggestions) {
            // 只对BUY建议执行交易
            if (!"BUY".equals(suggestion.getSuggestion())) {
                continue;
            }

            BacktestResult.Trade trade = simulateTrade(suggestion, request, currentCapital);
            if (trade != null) {
                trades.add(trade);
                currentCapital += trade.getProfit();

                // 更新最大回撤
                if (currentCapital > maxCapital) {
                    maxCapital = currentCapital;
                }
                double drawdown = (maxCapital - currentCapital) / maxCapital * 100;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                    maxDrawdownDate = trade.getExitDate();
                }
            }
        }

        result.setTrades(trades);

        // 计算基础指标
        result.setBasicMetrics(calculateBasicMetrics(trades, request.getInitialCapital(), currentCapital, request));

        // 计算风险指标
        result.setRiskMetrics(calculateRiskMetrics(trades, maxDrawdown, maxDrawdownDate, request));

        // 生成收益曲线
        result.setEquityCurve(generateEquityCurve(trades, request.getInitialCapital(), request.getStartDate(), request.getEndDate()));

        return result;
    }

    /**
     * 模拟单笔交易
     */
    private BacktestResult.Trade simulateTrade(SuggestionTracking suggestion, BacktestRequest request, double currentCapital) {
        try {
            String stockCode = suggestion.getStockCode();
            LocalDate entryDate = suggestion.getSuggestedAt().toLocalDate();
            BigDecimal entryPrice = suggestion.getSuggestedPrice();

            if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[增强回测] 无效的买入价格: {}", stockCode);
                return null;
            }

            // 计算可买入股数（100股为1手）
            double availableCapital = currentCapital * 0.95; // 预留5%现金
            int shares = (int) (availableCapital / entryPrice.doubleValue() / 100) * 100;
            if (shares < 100) {
                log.debug("[增强回测] 资金不足，跳过交易: {}", stockCode);
                return null;
            }

            // 模拟持有期间的价格变动
            LocalDate exitDate = entryDate;
            BigDecimal exitPrice = entryPrice;
            String exitReason = "HOLDING_PERIOD";

            for (int day = 1; day <= request.getHoldingDays(); day++) {
                LocalDate checkDate = entryDate.plusDays(day);
                if (checkDate.isAfter(request.getEndDate())) {
                    break;
                }

                StockKline kline = klineMapper.selectByDate(stockCode, checkDate);
                if (kline == null) {
                    continue;
                }

                BigDecimal currentPrice = kline.getClosePrice();
                double returnRate = currentPrice.subtract(entryPrice)
                        .divide(entryPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();

                // 检查止盈
                if (returnRate >= request.getTakeProfitThreshold()) {
                    exitDate = checkDate;
                    exitPrice = currentPrice;
                    exitReason = "TAKE_PROFIT";
                    break;
                }

                // 检查止损
                if (returnRate <= request.getStopLossThreshold()) {
                    exitDate = checkDate;
                    exitPrice = currentPrice;
                    exitReason = "STOP_LOSS";
                    break;
                }

                // 最后一天按收盘价退出
                if (day == request.getHoldingDays()) {
                    exitDate = checkDate;
                    exitPrice = currentPrice;
                }
            }

            // 计算盈亏
            double grossProfit = (exitPrice.doubleValue() - entryPrice.doubleValue()) * shares;
            double commission = (entryPrice.doubleValue() * shares + exitPrice.doubleValue() * shares) * request.getCommissionRate() / 100;
            double netProfit = grossProfit - commission;
            double returnRate = exitPrice.subtract(entryPrice)
                    .divide(entryPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();

            BacktestResult.Trade trade = new BacktestResult.Trade();
            trade.setStockCode(stockCode);
            trade.setStockName(suggestion.getStockName());
            trade.setEntryDate(entryDate);
            trade.setExitDate(exitDate);
            trade.setEntryPrice(entryPrice);
            trade.setExitPrice(exitPrice);
            trade.setShares(shares);
            trade.setReturnRate(returnRate);
            trade.setProfit(netProfit);
            trade.setExitReason(exitReason);
            trade.setSuggestion(suggestion.getSuggestion());
            // confidence是String类型（HIGH/MEDIUM/LOW），不是数值
            trade.setConfidence(suggestion.getConfidence() != null ?
                    mapConfidenceToDouble(suggestion.getConfidence()) : null);

            return trade;

        } catch (Exception e) {
            log.error("[增强回测] 交易模拟失败: {}", suggestion.getStockCode(), e);
            return null;
        }
    }

    /**
     * 计算基础指标
     */
    private BacktestResult.BasicMetrics calculateBasicMetrics(List<BacktestResult.Trade> trades,
                                                               double initialCapital, double finalCapital,
                                                               BacktestRequest request) {
        BacktestResult.BasicMetrics metrics = new BacktestResult.BasicMetrics();

        double totalProfit = trades.stream().mapToDouble(BacktestResult.Trade::getProfit).sum();
        int winningTrades = (int) trades.stream().filter(t -> t.getProfit() > 0).count();
        int losingTrades = (int) trades.stream().filter(t -> t.getProfit() < 0).count();

        metrics.setTotalReturn((finalCapital - initialCapital) / initialCapital * 100);
        metrics.setFinalCapital(finalCapital);
        metrics.setTotalProfit(totalProfit);
        metrics.setTotalTrades(trades.size());
        metrics.setWinningTrades(winningTrades);
        metrics.setLosingTrades(losingTrades);
        metrics.setWinRate(trades.isEmpty() ? 0.0 : (double) winningTrades / trades.size() * 100);

        double averageProfit = trades.stream().filter(t -> t.getProfit() > 0)
                .mapToDouble(BacktestResult.Trade::getProfit).average().orElse(0.0);
        double averageLoss = Math.abs(trades.stream().filter(t -> t.getProfit() < 0)
                .mapToDouble(BacktestResult.Trade::getProfit).average().orElse(0.0));

        metrics.setAverageProfit(averageProfit);
        metrics.setAverageLoss(averageLoss);
        metrics.setProfitFactor(averageLoss > 0 ? averageProfit / averageLoss : 0.0);

        // 年化收益率
        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
        double years = days / 365.0;
        metrics.setAnnualizedReturn(years > 0 ? metrics.getTotalReturn() / years : 0.0);

        return metrics;
    }

    /**
     * 计算风险指标
     */
    private BacktestResult.RiskMetrics calculateRiskMetrics(List<BacktestResult.Trade> trades,
                                                             double maxDrawdown, LocalDate maxDrawdownDate,
                                                             BacktestRequest request) {
        BacktestResult.RiskMetrics metrics = new BacktestResult.RiskMetrics();

        metrics.setMaxDrawdown(maxDrawdown);
        metrics.setMaxDrawdownDate(maxDrawdownDate);

        // 计算收益波动率（年化）
        if (!trades.isEmpty()) {
            double[] returns = trades.stream().mapToDouble(BacktestResult.Trade::getReturnRate).toArray();
            double avgReturn = Arrays.stream(returns).average().orElse(0.0);
            double variance = Arrays.stream(returns).map(r -> Math.pow(r - avgReturn, 2)).average().orElse(0.0);
            double dailyVolatility = Math.sqrt(variance);
            metrics.setVolatility(dailyVolatility * Math.sqrt(252)); // 年化
        } else {
            metrics.setVolatility(0.0);
        }

        // 计算夏普比率（假设无风险利率3%）
        double riskFreeRate = 3.0;
        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
        double years = days / 365.0;
        double annualizedReturn = trades.isEmpty() ? 0.0 :
                trades.stream().mapToDouble(BacktestResult.Trade::getReturnRate).average().orElse(0.0) * 252 / request.getHoldingDays();

        metrics.setSharpeRatio(metrics.getVolatility() > 0 ?
                (annualizedReturn - riskFreeRate) / metrics.getVolatility() : 0.0);

        // 卡尔玛比率
        metrics.setCalmarRatio(maxDrawdown > 0 ? annualizedReturn / maxDrawdown : 0.0);

        // 最大连续亏损
        int maxConsecutiveLosses = 0;
        int currentLosses = 0;
        for (BacktestResult.Trade trade : trades) {
            if (trade.getProfit() < 0) {
                currentLosses++;
                maxConsecutiveLosses = Math.max(maxConsecutiveLosses, currentLosses);
            } else {
                currentLosses = 0;
            }
        }
        metrics.setMaxConsecutiveLosses(maxConsecutiveLosses);

        // 平均回撤
        double avgDrawdown = trades.stream()
                .filter(t -> t.getReturnRate() < 0)
                .mapToDouble(BacktestResult.Trade::getReturnRate)
                .map(Math::abs)
                .average()
                .orElse(0.0);
        metrics.setAverageDrawdown(avgDrawdown);

        return metrics;
    }

    /**
     * 生成收益曲线
     */
    private List<BacktestResult.EquityPoint> generateEquityCurve(List<BacktestResult.Trade> trades,
                                                                   double initialCapital,
                                                                   LocalDate startDate, LocalDate endDate) {
        List<BacktestResult.EquityPoint> curve = new ArrayList<>();

        // 按日期排序交易
        List<BacktestResult.Trade> sortedTrades = trades.stream()
                .sorted(Comparator.comparing(BacktestResult.Trade::getExitDate))
                .collect(Collectors.toList());

        double currentEquity = initialCapital;
        double maxEquity = initialCapital;
        int tradeIndex = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            BacktestResult.EquityPoint point = new BacktestResult.EquityPoint();
            point.setDate(date);

            // 累加当日完成的交易盈亏
            double dailyProfit = 0.0;
            while (tradeIndex < sortedTrades.size() &&
                    !sortedTrades.get(tradeIndex).getExitDate().isAfter(date)) {
                dailyProfit += sortedTrades.get(tradeIndex).getProfit();
                tradeIndex++;
            }

            currentEquity += dailyProfit;
            point.setEquity(currentEquity);
            point.setDailyReturn(dailyProfit / (currentEquity - dailyProfit) * 100);

            // 计算当前回撤
            if (currentEquity > maxEquity) {
                maxEquity = currentEquity;
            }
            point.setDrawdown((maxEquity - currentEquity) / maxEquity * 100);

            curve.add(point);
        }

        return curve;
    }

    /**
     * 参数扫描（批量回测不同参数组合）
     */
    private BacktestResult runParameterScan(BacktestRequest request) {
        log.info("[参数扫描] 开始扫描参数组合");

        List<Integer> holdingDaysList = request.getHoldingDaysList() != null ?
                request.getHoldingDaysList() : List.of(3, 5, 7, 10, 15, 20, 30);
        List<Double> takeProfitList = request.getTakeProfitList() != null ?
                request.getTakeProfitList() : List.of(5.0, 8.0, 10.0, 15.0, 20.0);
        List<Double> stopLossList = request.getStopLossList() != null ?
                request.getStopLossList() : List.of(-3.0, -5.0, -7.0, -10.0);

        List<BacktestResult.ParamScanResult> scanResults = new ArrayList<>();

        for (Integer holdingDays : holdingDaysList) {
            for (Double takeProfit : takeProfitList) {
                for (Double stopLoss : stopLossList) {
                    BacktestRequest scanRequest = new BacktestRequest();
                    scanRequest.setStockCodes(request.getStockCodes());
                    scanRequest.setStartDate(request.getStartDate());
                    scanRequest.setEndDate(request.getEndDate());
                    scanRequest.setInitialCapital(request.getInitialCapital());
                    scanRequest.setHoldingDays(holdingDays);
                    scanRequest.setTakeProfitThreshold(takeProfit);
                    scanRequest.setStopLossThreshold(stopLoss);
                    scanRequest.setCommissionRate(request.getCommissionRate());
                    scanRequest.setEnableParamScan(false);

                    BacktestResult scanResult = executeSingleBacktest(scanRequest);

                    BacktestResult.ParamScanResult paramResult = new BacktestResult.ParamScanResult();
                    paramResult.setHoldingDays(holdingDays);
                    paramResult.setTakeProfitThreshold(takeProfit);
                    paramResult.setStopLossThreshold(stopLoss);
                    paramResult.setTotalReturn(scanResult.getBasicMetrics().getTotalReturn());
                    paramResult.setSharpeRatio(scanResult.getRiskMetrics().getSharpeRatio());
                    paramResult.setMaxDrawdown(scanResult.getRiskMetrics().getMaxDrawdown());
                    paramResult.setWinRate(scanResult.getBasicMetrics().getWinRate());
                    paramResult.setTotalTrades(scanResult.getBasicMetrics().getTotalTrades());

                    scanResults.add(paramResult);
                }
            }
        }

        // 按总收益率降序排序
        scanResults.sort(Comparator.comparing(BacktestResult.ParamScanResult::getTotalReturn).reversed());

        BacktestResult result = new BacktestResult();
        result.setParamScanResults(scanResults);

        // 使用最优参数执行一次完整回测
        if (!scanResults.isEmpty()) {
            BacktestResult.ParamScanResult best = scanResults.get(0);
            request.setHoldingDays(best.getHoldingDays());
            request.setTakeProfitThreshold(best.getTakeProfitThreshold());
            request.setStopLossThreshold(best.getStopLossThreshold());
            request.setEnableParamScan(false);
            BacktestResult bestResult = executeSingleBacktest(request);
            result.setParams(bestResult.getParams());
            result.setBasicMetrics(bestResult.getBasicMetrics());
            result.setRiskMetrics(bestResult.getRiskMetrics());
            result.setTrades(bestResult.getTrades());
            result.setEquityCurve(bestResult.getEquityCurve());
        }

        log.info("[参数扫描] 完成 - 测试了{}组参数", scanResults.size());
        return result;
    }

    /**
     * 获取时间范围内的建议
     */
    private List<SuggestionTracking> getSuggestionsInRange(List<String> stockCodes, LocalDate startDate, LocalDate endDate) {
        // 查询数据库中的历史建议
        List<SuggestionTracking> allSuggestions = new ArrayList<>();
        for (String stockCode : stockCodes) {
            List<SuggestionTracking> suggestions = trackingMapper.findByStockCode(stockCode, 1000);
            allSuggestions.addAll(suggestions.stream()
                    .filter(s -> {
                        LocalDate date = s.getSuggestedAt().toLocalDate();
                        return !date.isBefore(startDate) && !date.isAfter(endDate);
                    })
                    .collect(Collectors.toList()));
        }
        return allSuggestions;
    }

    /**
     * 创建空结果
     */
    private BacktestResult createEmptyResult(BacktestResult result) {
        result.setBasicMetrics(new BacktestResult.BasicMetrics());
        result.setRiskMetrics(new BacktestResult.RiskMetrics());
        result.setTrades(Collections.emptyList());
        result.setEquityCurve(Collections.emptyList());
        return result;
    }

    /**
     * 将置信度字符串映射为数值（用于统计）
     */
    private Double mapConfidenceToDouble(String confidence) {
        return switch (confidence) {
            case "HIGH" -> 0.9;
            case "MEDIUM" -> 0.7;
            case "LOW" -> 0.5;
            default -> 0.5;
        };
    }
}
