package com.quantai.service;

import com.quantai.mapper.SuggestionTrackingMapper;
import com.quantai.model.entity.SuggestionTracking;
import com.quantai.service.impl.StockServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回测系统服务
 * 职责：
 * 1. 保存Agent建议到数据库
 * 2. 定时任务回测建议准确率（7天/30天）
 * 3. 统计准确率（按维度/按股票/按时间窗口）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestService {

    private final SuggestionTrackingMapper trackingMapper;
    private final StockServiceImpl stockService;
    private final com.quantai.mapper.StockKlineMapper stockKlineMapper;

    /**
     * 保存建议（供InvestmentAdvisorAgent调用）
     */
    @Transactional
    public void saveSuggestion(SuggestionTracking tracking) {
        if (tracking == null || tracking.getStockCode() == null) {
            log.warn("[回测系统] 无效建议，跳过保存");
            return;
        }

        try {
            trackingMapper.insert(tracking);
            log.info("[回测系统] 保存建议成功 - ID: {}, 股票: {}, 建议: {}, 价格: {}",
                    tracking.getId(), tracking.getStockCode(), tracking.getSuggestion(), tracking.getSuggestedPrice());
        } catch (Exception e) {
            log.error("[回测系统] 保存建议失败", e);
            throw e;
        }
    }

    /**
     * 执行7天回测（定时任务调用）
     */
    @Transactional
    public void runBacktest7d() {
        log.info("[回测系统] 开始执行7天回测任务");

        try {
            // 查询7天前的待回测建议
            LocalDateTime deadline = LocalDateTime.now().minusDays(7);
            List<SuggestionTracking> pendingList = trackingMapper.findPending7dBacktest(deadline, 100);

            if (pendingList.isEmpty()) {
                log.info("[回测系统] 无待回测的7天建议");
                return;
            }

            log.info("[回测系统] 找到 {} 条待回测建议", pendingList.size());

            for (SuggestionTracking tracking : pendingList) {
                try {
                    // 获取7天后的实际价格
                    BigDecimal actualPrice = getActualPrice(tracking.getStockCode(),
                            tracking.getSuggestedAt().plusDays(7));

                    if (actualPrice == null) {
                        log.warn("[回测系统] 无法获取7天后价格 - 股票: {}", tracking.getStockCode());
                        continue;
                    }

                    // 计算收益率
                    BigDecimal returnRate = calculateReturn(tracking.getSuggestedPrice(), actualPrice);

                    // 判断建议是否准确
                    boolean accurate = isAccurate(tracking.getSuggestion(), returnRate);

                    // 更新数据库
                    tracking.setActualPrice7d(actualPrice);
                    tracking.setReturn7d(returnRate);
                    tracking.setAccuracy7d(accurate);
                    trackingMapper.update7dBacktest(tracking);

                    log.info("[回测系统] 7天回测完成 - 股票: {}, 建议: {}, 收益率: {}%, 准确: {}",
                            tracking.getStockCode(), tracking.getSuggestion(), returnRate, accurate);

                } catch (Exception e) {
                    log.error("[回测系统] 回测失败 - 股票: {}", tracking.getStockCode(), e);
                }
            }

            log.info("[回测系统] 7天回测任务完成 - 成功: {}", pendingList.size());

        } catch (Exception e) {
            log.error("[回测系统] 7天回测任务执行失败", e);
        }
    }

    /**
     * 执行30天回测（定时任务调用）
     */
    @Transactional
    public void runBacktest30d() {
        log.info("[回测系统] 开始执行30天回测任务");

        try {
            // 查询30天前的待回测建议
            LocalDateTime deadline = LocalDateTime.now().minusDays(30);
            List<SuggestionTracking> pendingList = trackingMapper.findPending30dBacktest(deadline, 100);

            if (pendingList.isEmpty()) {
                log.info("[回测系统] 无待回测的30天建议");
                return;
            }

            log.info("[回测系统] 找到 {} 条待回测建议", pendingList.size());

            for (SuggestionTracking tracking : pendingList) {
                try {
                    // 获取30天后的实际价格
                    BigDecimal actualPrice = getActualPrice(tracking.getStockCode(),
                            tracking.getSuggestedAt().plusDays(30));

                    if (actualPrice == null) {
                        log.warn("[回测系统] 无法获取30天后价格 - 股票: {}", tracking.getStockCode());
                        continue;
                    }

                    // 计算收益率
                    BigDecimal returnRate = calculateReturn(tracking.getSuggestedPrice(), actualPrice);

                    // 判断建议是否准确
                    boolean accurate = isAccurate(tracking.getSuggestion(), returnRate);

                    // 更新数据库
                    tracking.setActualPrice30d(actualPrice);
                    tracking.setReturn30d(returnRate);
                    tracking.setAccuracy30d(accurate);
                    trackingMapper.update30dBacktest(tracking);

                    log.info("[回测系统] 30天回测完成 - 股票: {}, 建议: {}, 收益率: {}%, 准确: {}",
                            tracking.getStockCode(), tracking.getSuggestion(), returnRate, accurate);

                } catch (Exception e) {
                    log.error("[回测系统] 回测失败 - 股票: {}", tracking.getStockCode(), e);
                }
            }

            log.info("[回测系统] 30天回测任务完成 - 成功: {}", pendingList.size());

        } catch (Exception e) {
            log.error("[回测系统] 30天回测任务执行失败", e);
        }
    }

    /**
     * 获取指定日期的实际价格（从历史K线数据获取）
     */
    private BigDecimal getActualPrice(String stockCode, LocalDateTime targetDate) {
        try {
            // 查询目标日期的收盘价（从stock_kline表）
            // 如果目标日期是非交易日，向后查找最近的交易日数据（最多查3天）
            for (int offset = 0; offset <= 3; offset++) {
                LocalDate queryDate = targetDate.plusDays(offset).toLocalDate();
                com.quantai.model.entity.StockKline kline = stockKlineMapper.selectByDate(stockCode, queryDate);
                if (kline != null && kline.getClosePrice() != null) {
                    if (offset > 0) {
                        log.info("[回测系统] 目标日期{}无数据，使用{}的价格", targetDate.toLocalDate(), queryDate);
                    }
                    return kline.getClosePrice();
                }
            }

            log.warn("[回测系统] 无法获取{}前后3天的价格数据", targetDate.toLocalDate());
            return null;
        } catch (Exception e) {
            log.error("获取实际价格失败", e);
            return null;
        }
    }

    /**
     * 计算收益率
     * @param basePrice 基准价格
     * @param actualPrice 实际价格
     * @return 收益率（%）
     */
    private BigDecimal calculateReturn(BigDecimal basePrice, BigDecimal actualPrice) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return actualPrice.subtract(basePrice)
                .divide(basePrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 判断建议是否准确
     * 规则：
     * - BUY: 收益率 > 3% 为准确
     * - SELL: 收益率 < -3% 为准确
     * - HOLD: -3% <= 收益率 <= 3% 为准确
     */
    private boolean isAccurate(String suggestion, BigDecimal returnRate) {
        if (returnRate == null) {
            return false;
        }

        double rate = returnRate.doubleValue();

        return switch (suggestion) {
            case "BUY" -> rate > 3.0;
            case "SELL" -> rate < -3.0;
            case "HOLD" -> rate >= -3.0 && rate <= 3.0;
            default -> false;
        };
    }

    /**
     * 统计全局准确率（用于监控）
     */
    public SuggestionTrackingMapper.AccuracyStats calculateGlobalAccuracy(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return trackingMapper.calculateAccuracy(startDate);
    }

    /**
     * 统计某股票的准确率（用于自适应权重）
     */
    public SuggestionTrackingMapper.AccuracyStats calculateStockAccuracy(String stockCode, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return trackingMapper.calculateAccuracyByStock(stockCode, startDate);
    }

    /**
     * 获取某股票的历史建议（用于自适应权重）
     */
    public List<SuggestionTracking> getHistorySuggestions(String stockCode, int limit) {
        return trackingMapper.findByStockCode(stockCode, limit);
    }
}
