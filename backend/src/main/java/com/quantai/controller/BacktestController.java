package com.quantai.controller;

import com.quantai.model.dto.BacktestRequest;
import com.quantai.model.dto.BacktestResult;
import com.quantai.service.EnhancedBacktestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 回测控制器
 * 提供策略回测、参数扫描、历史建议分析等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final EnhancedBacktestService backtestService;

    /**
     * 执行回测
     * POST /api/backtest/run
     * {
     *   "stockCodes": ["sh600519", "sz000858"],
     *   "startDate": "2024-01-01",
     *   "endDate": "2024-12-31",
     *   "initialCapital": 100000,
     *   "holdingDays": 7,
     *   "takeProfitThreshold": 10,
     *   "stopLossThreshold": -5,
     *   "commissionRate": 0.03
     * }
     */
    @PostMapping("/run")
    public ResponseEntity<BacktestResult> runBacktest(@RequestBody BacktestRequest request) {
        log.info("[回测API] 收到回测请求 - 股票: {}, 周期: {} ~ {}",
                request.getStockCodes(), request.getStartDate(), request.getEndDate());

        try {
            BacktestResult result = backtestService.runBacktest(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[回测API] 回测执行失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 快速回测（使用默认参数）
     * GET /api/backtest/quick?stockCode=sh600519&days=90
     */
    @GetMapping("/quick")
    public ResponseEntity<BacktestResult> quickBacktest(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "90") Integer days) {

        log.info("[回测API] 快速回测 - 股票: {}, 天数: {}", stockCode, days);

        BacktestRequest request = new BacktestRequest();
        request.setStockCodes(List.of(stockCode));
        request.setEndDate(LocalDate.now());
        request.setStartDate(LocalDate.now().minusDays(days));
        request.setInitialCapital(100000.0);
        request.setHoldingDays(7);
        request.setTakeProfitThreshold(10.0);
        request.setStopLossThreshold(-5.0);
        request.setCommissionRate(0.03);

        try {
            BacktestResult result = backtestService.runBacktest(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[回测API] 快速回测失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 参数扫描（寻找最优参数组合）
     * POST /api/backtest/param-scan
     * {
     *   "stockCodes": ["sh600519"],
     *   "startDate": "2024-01-01",
     *   "endDate": "2024-12-31",
     *   "enableParamScan": true,
     *   "holdingDaysList": [5, 7, 10, 15],
     *   "takeProfitList": [8, 10, 12, 15],
     *   "stopLossList": [-3, -5, -7]
     * }
     */
    @PostMapping("/param-scan")
    public ResponseEntity<BacktestResult> parameterScan(@RequestBody BacktestRequest request) {
        log.info("[回测API] 参数扫描请求 - 股票: {}", request.getStockCodes());

        request.setEnableParamScan(true);

        try {
            BacktestResult result = backtestService.runBacktest(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[回测API] 参数扫描失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 批量回测（多个股票）
     * POST /api/backtest/batch
     * {
     *   "stockCodes": ["sh600519", "sz000858", "sh600036"],
     *   "startDate": "2024-01-01",
     *   "endDate": "2024-12-31"
     * }
     */
    @PostMapping("/batch")
    public ResponseEntity<BacktestResult> batchBacktest(@RequestBody BacktestRequest request) {
        log.info("[回测API] 批量回测 - 股票数量: {}", request.getStockCodes().size());

        // 设置默认参数
        if (request.getInitialCapital() == null) {
            request.setInitialCapital(100000.0);
        }
        if (request.getHoldingDays() == null) {
            request.setHoldingDays(7);
        }
        if (request.getTakeProfitThreshold() == null) {
            request.setTakeProfitThreshold(10.0);
        }
        if (request.getStopLossThreshold() == null) {
            request.setStopLossThreshold(-5.0);
        }
        if (request.getCommissionRate() == null) {
            request.setCommissionRate(0.03);
        }

        try {
            BacktestResult result = backtestService.runBacktest(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[回测API] 批量回测失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取回测报告摘要
     * GET /api/backtest/summary?stockCode=sh600519&days=90
     */
    @GetMapping("/summary")
    public ResponseEntity<BacktestSummary> getBacktestSummary(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "90") Integer days) {

        log.info("[回测API] 获取回测摘要 - 股票: {}", stockCode);

        BacktestRequest request = new BacktestRequest();
        request.setStockCodes(List.of(stockCode));
        request.setEndDate(LocalDate.now());
        request.setStartDate(LocalDate.now().minusDays(days));
        request.setInitialCapital(100000.0);
        request.setHoldingDays(7);
        request.setTakeProfitThreshold(10.0);
        request.setStopLossThreshold(-5.0);
        request.setCommissionRate(0.03);

        try {
            BacktestResult result = backtestService.runBacktest(request);

            BacktestSummary summary = new BacktestSummary();
            summary.setStockCode(stockCode);
            summary.setTotalReturn(result.getBasicMetrics().getTotalReturn());
            summary.setWinRate(result.getBasicMetrics().getWinRate());
            summary.setMaxDrawdown(result.getRiskMetrics().getMaxDrawdown());
            summary.setSharpeRatio(result.getRiskMetrics().getSharpeRatio());
            summary.setTotalTrades(result.getBasicMetrics().getTotalTrades());
            summary.setProfitFactor(result.getBasicMetrics().getProfitFactor());

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("[回测API] 获取摘要失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 回测摘要DTO（简化版）
     */
    public static class BacktestSummary {
        private String stockCode;
        private Double totalReturn;
        private Double winRate;
        private Double maxDrawdown;
        private Double sharpeRatio;
        private Integer totalTrades;
        private Double profitFactor;

        // Getters and Setters
        public String getStockCode() { return stockCode; }
        public void setStockCode(String stockCode) { this.stockCode = stockCode; }
        public Double getTotalReturn() { return totalReturn; }
        public void setTotalReturn(Double totalReturn) { this.totalReturn = totalReturn; }
        public Double getWinRate() { return winRate; }
        public void setWinRate(Double winRate) { this.winRate = winRate; }
        public Double getMaxDrawdown() { return maxDrawdown; }
        public void setMaxDrawdown(Double maxDrawdown) { this.maxDrawdown = maxDrawdown; }
        public Double getSharpeRatio() { return sharpeRatio; }
        public void setSharpeRatio(Double sharpeRatio) { this.sharpeRatio = sharpeRatio; }
        public Integer getTotalTrades() { return totalTrades; }
        public void setTotalTrades(Integer totalTrades) { this.totalTrades = totalTrades; }
        public Double getProfitFactor() { return profitFactor; }
        public void setProfitFactor(Double profitFactor) { this.profitFactor = profitFactor; }
    }
}
