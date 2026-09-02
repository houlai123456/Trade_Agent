package com.quantai.service;

import com.quantai.mapper.SuggestionTrackingMapper;
import com.quantai.model.entity.SuggestionTracking;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自适应权重服务
 * 职责：
 * 1. 根据股票类型（成长股/周期股/题材股）自动分类
 * 2. 基于历史准确率动态调整权重
 * 3. 结合股票特征和Agent表现综合决策
 *
 * 权重调整策略：
 * - 成长股：基本面50% 技术20% 情绪30%（注重基本面）
 * - 周期股：基本面35% 技术35% 情绪30%（技术+基本面均衡）
 * - 题材股：基本面20% 技术30% 情绪50%（注重情绪面）
 * - 历史准确率调整：根据过去30天的准确率微调±5%
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveWeightService {

    private final SuggestionTrackingMapper trackingMapper;

    // 默认权重配置（兜底策略）
    private static final WeightProfile DEFAULT_WEIGHTS = new WeightProfile(0.40, 0.30, 0.30);

    // 预设权重配置
    private static final Map<StockType, WeightProfile> PRESET_WEIGHTS = new HashMap<>();

    static {
        PRESET_WEIGHTS.put(StockType.GROWTH, new WeightProfile(0.50, 0.20, 0.30));   // 成长股
        PRESET_WEIGHTS.put(StockType.CYCLICAL, new WeightProfile(0.35, 0.35, 0.30)); // 周期股
        PRESET_WEIGHTS.put(StockType.THEMATIC, new WeightProfile(0.20, 0.30, 0.50)); // 题材股
    }

    /**
     * 计算自适应权重（核心方法）
     * @param stockCode 股票代码
     * @param fundamentalScore 基本面评分（0-100）
     * @param technicalScore 技术面评分（0-100）
     * @param sentimentScore 情绪面评分（0-100）
     * @return 权重配置
     */
    public WeightProfile calculateWeights(String stockCode, int fundamentalScore, int technicalScore, int sentimentScore) {
        try {
            // 步骤1：分类股票类型（基于当前评分特征）
            StockType stockType = classifyStock(fundamentalScore, technicalScore, sentimentScore);
            log.debug("[自适应权重] 股票: {}, 分类: {}", stockCode, stockType);

            // 步骤2：获取预设权重
            WeightProfile baseWeights = PRESET_WEIGHTS.getOrDefault(stockType, DEFAULT_WEIGHTS);

            // 步骤3：查询历史准确率
            List<SuggestionTracking> history = trackingMapper.findByStockCode(stockCode, 10);

            if (history.isEmpty()) {
                log.debug("[自适应权重] 股票: {} 无历史数据，使用预设权重", stockCode);
                return baseWeights;
            }

            // 步骤4：计算各维度的历史准确率
            AccuracyMetrics metrics = calculateHistoryAccuracy(history);

            // 步骤5：动态调整权重（基于准确率）
            WeightProfile adjustedWeights = adjustWeights(baseWeights, metrics);

            log.info("[自适应权重] 股票: {}, 类型: {}, 基础权重: {}, 调整后: {}, 历史准确率: {}",
                    stockCode, stockType, baseWeights, adjustedWeights, metrics);

            return adjustedWeights;

        } catch (Exception e) {
            log.error("[自适应权重] 计算失败，使用默认权重", e);
            return DEFAULT_WEIGHTS;
        }
    }

    /**
     * 分类股票类型（基于评分特征）
     * 规则：
     * - 成长股：基本面评分高（≥70），技术面中等，情绪面中等
     * - 题材股：情绪面评分高（≥70），基本面偏低
     * - 周期股：其他情况
     */
    private StockType classifyStock(int fundamentalScore, int technicalScore, int sentimentScore) {
        // 成长股特征：基本面强
        if (fundamentalScore >= 70 && fundamentalScore > sentimentScore) {
            return StockType.GROWTH;
        }

        // 题材股特征：情绪面强，基本面弱
        if (sentimentScore >= 70 && fundamentalScore < 50) {
            return StockType.THEMATIC;
        }

        // 默认为周期股（均衡型）
        return StockType.CYCLICAL;
    }

    /**
     * 计算历史准确率（基于加权贡献度分配）
     */
    private AccuracyMetrics calculateHistoryAccuracy(List<SuggestionTracking> history) {
        AccuracyMetrics metrics = new AccuracyMetrics();

        double fundamentalWeightedCorrect = 0, technicalWeightedCorrect = 0, sentimentWeightedCorrect = 0;
        double fundamentalWeightSum = 0, technicalWeightSum = 0, sentimentWeightSum = 0;

        for (SuggestionTracking track : history) {
            // 只统计已完成回测的数据
            if (track.getAccuracy7d() == null) {
                continue;
            }

            boolean accurate = track.getAccuracy7d();
            Integer fScore = track.getFundamentalScore();
            Integer tScore = track.getTechnicalScore();
            Integer sScore = track.getSentimentScore();

            if (fScore == null || tScore == null || sScore == null) {
                continue;
            }

            // 根据当时的评分重新计算权重（模拟当时的分类和权重）
            StockType stockType = classifyStock(fScore, tScore, sScore);
            WeightProfile weights = PRESET_WEIGHTS.getOrDefault(stockType, DEFAULT_WEIGHTS);

            // 按权重累加准确率（每个维度按其权重贡献）
            if (accurate) {
                fundamentalWeightedCorrect += weights.fundamental;
                technicalWeightedCorrect += weights.technical;
                sentimentWeightedCorrect += weights.sentiment;
            }

            fundamentalWeightSum += weights.fundamental;
            technicalWeightSum += weights.technical;
            sentimentWeightSum += weights.sentiment;
        }

        // 计算加权准确率（至少需要2次记录才可靠）
        int totalRecords = history.size();
        metrics.fundamentalAccuracy = totalRecords >= 2 && fundamentalWeightSum > 0
                ? fundamentalWeightedCorrect / fundamentalWeightSum : 0.5;
        metrics.technicalAccuracy = totalRecords >= 2 && technicalWeightSum > 0
                ? technicalWeightedCorrect / technicalWeightSum : 0.5;
        metrics.sentimentAccuracy = totalRecords >= 2 && sentimentWeightSum > 0
                ? sentimentWeightedCorrect / sentimentWeightSum : 0.5;

        return metrics;
    }

    /**
     * 动态调整权重（基于准确率）
     * 调整策略：
     * - 准确率 > 60%：权重 +5%
     * - 准确率 < 40%：权重 -5%
     * - 40% <= 准确率 <= 60%：保持不变
     * - 调整后需要归一化（总和=1.0）
     */
    private WeightProfile adjustWeights(WeightProfile base, AccuracyMetrics metrics) {
        double f = base.fundamental;
        double t = base.technical;
        double s = base.sentiment;

        // 根据准确率调整
        if (metrics.fundamentalAccuracy > 0.6) {
            f += 0.05;
        } else if (metrics.fundamentalAccuracy < 0.4) {
            f -= 0.05;
        }

        if (metrics.technicalAccuracy > 0.6) {
            t += 0.05;
        } else if (metrics.technicalAccuracy < 0.4) {
            t -= 0.05;
        }

        if (metrics.sentimentAccuracy > 0.6) {
            s += 0.05;
        } else if (metrics.sentimentAccuracy < 0.4) {
            s -= 0.05;
        }

        // 边界保护（权重不低于10%，不高于70%）
        f = Math.max(0.10, Math.min(0.70, f));
        t = Math.max(0.10, Math.min(0.70, t));
        s = Math.max(0.10, Math.min(0.70, s));

        // 归一化
        double total = f + t + s;
        return new WeightProfile(f / total, t / total, s / total);
    }

    /**
     * 股票类型枚举
     */
    private enum StockType {
        GROWTH,     // 成长股
        CYCLICAL,   // 周期股
        THEMATIC    // 题材股
    }

    /**
     * 权重配置
     */
    @Data
    public static class WeightProfile {
        public final double fundamental;
        public final double technical;
        public final double sentiment;

        public WeightProfile(double fundamental, double technical, double sentiment) {
            this.fundamental = fundamental;
            this.technical = technical;
            this.sentiment = sentiment;
        }

        @Override
        public String toString() {
            return String.format("基本面%.0f%% 技术面%.0f%% 情绪面%.0f%%",
                    fundamental * 100, technical * 100, sentiment * 100);
        }
    }

    /**
     * 准确率指标
     */
    @Data
    private static class AccuracyMetrics {
        double fundamentalAccuracy = 0.5; // 默认50%
        double technicalAccuracy = 0.5;
        double sentimentAccuracy = 0.5;

        @Override
        public String toString() {
            return String.format("基本面%.0f%% 技术面%.0f%% 情绪面%.0f%%",
                    fundamentalAccuracy * 100, technicalAccuracy * 100, sentimentAccuracy * 100);
        }
    }
}
