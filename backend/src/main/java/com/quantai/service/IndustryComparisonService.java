package com.quantai.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业对比分析服务
 * 提供行业估值对比、历史分位数计算等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryComparisonService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取行业估值对比数据
     */
    public IndustryComparison getIndustryComparison(String stockCode, String industry) {
        if (industry == null || industry.isEmpty()) {
            log.warn("行业信息为空，无法进行行业对比: {}", stockCode);
            return null;
        }

        try {
            // 查询行业最新统计数据
            String sql = """
                SELECT industry, trade_date, pe_median, pe_mean, pe_p25, pe_p75,
                       pb_median, pb_mean, roe_median, roe_mean, roe_p75,
                       revenue_yoy_median, profit_yoy_median, stock_count
                FROM industry_valuation_stats
                WHERE industry = ?
                ORDER BY trade_date DESC
                LIMIT 1
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, industry);

            if (results.isEmpty()) {
                log.warn("未找到行业统计数据: {}", industry);
                return null;
            }

            Map<String, Object> row = results.get(0);
            IndustryComparison comparison = new IndustryComparison();
            comparison.setIndustry(industry);
            comparison.setTradeDate((LocalDate) row.get("trade_date"));
            comparison.setPeMedian(getBigDecimal(row, "pe_median"));
            comparison.setPeMean(getBigDecimal(row, "pe_mean"));
            comparison.setPeP25(getBigDecimal(row, "pe_p25"));
            comparison.setPeP75(getBigDecimal(row, "pe_p75"));
            comparison.setPbMedian(getBigDecimal(row, "pb_median"));
            comparison.setPbMean(getBigDecimal(row, "pb_mean"));
            comparison.setRoeMedian(getBigDecimal(row, "roe_median"));
            comparison.setRoeMean(getBigDecimal(row, "roe_mean"));
            comparison.setRoeP75(getBigDecimal(row, "roe_p75"));
            comparison.setRevenueYoyMedian(getBigDecimal(row, "revenue_yoy_median"));
            comparison.setProfitYoyMedian(getBigDecimal(row, "profit_yoy_median"));
            comparison.setStockCount((Integer) row.get("stock_count"));

            return comparison;

        } catch (Exception e) {
            log.error("查询行业对比数据失败: industry={}", industry, e);
            return null;
        }
    }

    /**
     * 计算股票历史估值分位数
     */
    public ValuationPercentile getHistoricalPercentile(String stockCode, BigDecimal currentPe, BigDecimal currentPb) {
        if (currentPe == null && currentPb == null) {
            return null;
        }

        try {
            // 计算过去3年的PE分位数
            String peSql = """
                SELECT
                    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY pe_ratio) as pe_p25,
                    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY pe_ratio) as pe_p50,
                    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY pe_ratio) as pe_p75,
                    MIN(pe_ratio) as pe_min,
                    MAX(pe_ratio) as pe_max
                FROM stock_fundamental
                WHERE stock_code = ?
                  AND trade_date >= DATE_SUB(CURDATE(), INTERVAL 3 YEAR)
                  AND pe_ratio > 0
                """;

            ValuationPercentile percentile = new ValuationPercentile();

            if (currentPe != null && currentPe.compareTo(BigDecimal.ZERO) > 0) {
                List<Map<String, Object>> peResults = jdbcTemplate.queryForList(peSql, stockCode);
                if (!peResults.isEmpty()) {
                    Map<String, Object> peRow = peResults.get(0);
                    percentile.setPeP25(getBigDecimal(peRow, "pe_p25"));
                    percentile.setPeP50(getBigDecimal(peRow, "pe_p50"));
                    percentile.setPeP75(getBigDecimal(peRow, "pe_p75"));
                    percentile.setPeMin(getBigDecimal(peRow, "pe_min"));
                    percentile.setPeMax(getBigDecimal(peRow, "pe_max"));
                    percentile.setPePercentile(calculatePercentile(currentPe, percentile.getPeP25(),
                        percentile.getPeP50(), percentile.getPeP75()));
                }
            }

            // 计算PB分位数
            if (currentPb != null && currentPb.compareTo(BigDecimal.ZERO) > 0) {
                String pbSql = """
                    SELECT
                        PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY pb_ratio) as pb_p25,
                        PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY pb_ratio) as pb_p50,
                        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY pb_ratio) as pb_p75
                    FROM stock_fundamental
                    WHERE stock_code = ?
                      AND trade_date >= DATE_SUB(CURDATE(), INTERVAL 3 YEAR)
                      AND pb_ratio > 0
                    """;

                List<Map<String, Object>> pbResults = jdbcTemplate.queryForList(pbSql, stockCode);
                if (!pbResults.isEmpty()) {
                    Map<String, Object> pbRow = pbResults.get(0);
                    percentile.setPbP25(getBigDecimal(pbRow, "pb_p25"));
                    percentile.setPbP50(getBigDecimal(pbRow, "pb_p50"));
                    percentile.setPbP75(getBigDecimal(pbRow, "pb_p75"));
                    percentile.setPbPercentile(calculatePercentile(currentPb, percentile.getPbP25(),
                        percentile.getPbP50(), percentile.getPbP75()));
                }
            }

            return percentile;

        } catch (Exception e) {
            log.error("计算历史分位数失败: stockCode={}", stockCode, e);
            return null;
        }
    }

    /**
     * 根据四分位数估算当前值的分位数位置
     */
    private Integer calculatePercentile(BigDecimal current, BigDecimal p25, BigDecimal p50, BigDecimal p75) {
        if (current == null || p25 == null || p50 == null || p75 == null) {
            return null;
        }

        if (current.compareTo(p25) < 0) {
            return 20; // 低于25%分位，估算为20%
        } else if (current.compareTo(p50) < 0) {
            return 35; // 25%-50%之间，估算为35%
        } else if (current.compareTo(p75) < 0) {
            return 60; // 50%-75%之间，估算为60%
        } else {
            return 85; // 高于75%分位，估算为85%
        }
    }

    /**
     * 获取行业内同类股票对比
     */
    public List<Map<String, Object>> getIndustryPeers(String stockCode, String industry, int limit) {
        if (industry == null || industry.isEmpty()) {
            return List.of();
        }

        try {
            String sql = """
                SELECT s.code, s.name, f.pe_ratio, f.pb_ratio, f.roe, f.revenue_yoy, f.profit_yoy
                FROM stock_info s
                LEFT JOIN (
                    SELECT stock_code, pe_ratio, pb_ratio, roe, revenue_yoy, profit_yoy
                    FROM stock_fundamental
                    WHERE (stock_code, trade_date) IN (
                        SELECT stock_code, MAX(trade_date)
                        FROM stock_fundamental
                        GROUP BY stock_code
                    )
                ) f ON s.code = f.stock_code
                WHERE s.industry = ? AND s.code != ?
                ORDER BY f.pe_ratio
                LIMIT ?
                """;

            return jdbcTemplate.queryForList(sql, industry, stockCode, limit);

        } catch (Exception e) {
            log.error("查询行业同类股票失败: industry={}", industry, e);
            return List.of();
        }
    }

    private BigDecimal getBigDecimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    /**
     * 行业对比数据
     */
    @Data
    public static class IndustryComparison {
        private String industry;
        private LocalDate tradeDate;

        // PE统计
        private BigDecimal peMedian;
        private BigDecimal peMean;
        private BigDecimal peP25;
        private BigDecimal peP75;

        // PB统计
        private BigDecimal pbMedian;
        private BigDecimal pbMean;

        // ROE统计
        private BigDecimal roeMedian;
        private BigDecimal roeMean;
        private BigDecimal roeP75; // 行业优秀水平

        // 成长性统计
        private BigDecimal revenueYoyMedian;
        private BigDecimal profitYoyMedian;

        private Integer stockCount;
    }

    /**
     * 估值分位数
     */
    @Data
    public static class ValuationPercentile {
        // PE分位数
        private BigDecimal peP25;
        private BigDecimal peP50;
        private BigDecimal peP75;
        private BigDecimal peMin;
        private BigDecimal peMax;
        private Integer pePercentile; // 当前PE所处分位数（0-100）

        // PB分位数
        private BigDecimal pbP25;
        private BigDecimal pbP50;
        private BigDecimal pbP75;
        private Integer pbPercentile; // 当前PB所处分位数（0-100）
    }
}
