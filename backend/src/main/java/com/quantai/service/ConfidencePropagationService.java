package com.quantai.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 置信度传播服务
 * 职责：
 * 1. 根据各维度Agent的置信度（HIGH/MEDIUM/LOW）调整权重
 * 2. 低置信度维度权重降低40%，高置信度维度权重提升20%
 * 3. 计算加权置信度作为最终建议的置信度
 *
 * 设计理念：
 * - 数据质量影响决策权重：数据不完整的维度应该降低影响力
 * - 传播规则：置信度 → 权重调整 → 最终置信度
 */
@Slf4j
@Service
public class ConfidencePropagationService {

    // 置信度权重调整因子
    private static final double HIGH_CONFIDENCE_BOOST = 1.20;   // 高置信度提升20%
    private static final double MEDIUM_CONFIDENCE_FACTOR = 1.0; // 中等置信度保持不变
    private static final double LOW_CONFIDENCE_PENALTY = 0.60;  // 低置信度降低40%

    /**
     * 根据置信度调整权重
     * @param baseWeights 基础权重（来自自适应权重服务）
     * @param confidences 各维度置信度 Map<"FUNDAMENTAL"|"TECHNICAL"|"SENTIMENT", "HIGH"|"MEDIUM"|"LOW">
     * @return 调整后的权重
     */
    public AdjustedWeights adjustWeightsByConfidence(
            AdaptiveWeightService.WeightProfile baseWeights,
            Map<String, String> confidences) {

        AdjustedWeights adjusted = new AdjustedWeights();

        // 获取置信度
        String fundamentalConfidence = confidences.getOrDefault("FUNDAMENTAL", "MEDIUM");
        String technicalConfidence = confidences.getOrDefault("TECHNICAL", "MEDIUM");
        String sentimentConfidence = confidences.getOrDefault("SENTIMENT", "MEDIUM");

        // 应用置信度调整因子
        double fundamentalAdjusted = baseWeights.fundamental * getConfidenceFactor(fundamentalConfidence);
        double technicalAdjusted = baseWeights.technical * getConfidenceFactor(technicalConfidence);
        double sentimentAdjusted = baseWeights.sentiment * getConfidenceFactor(sentimentConfidence);

        // 归一化（总和=1.0）
        double total = fundamentalAdjusted + technicalAdjusted + sentimentAdjusted;
        adjusted.fundamental = fundamentalAdjusted / total;
        adjusted.technical = technicalAdjusted / total;
        adjusted.sentiment = sentimentAdjusted / total;

        log.info("[置信度传播] 基础权重: 基本面{:.0f}% 技术面{:.0f}% 情绪面{:.0f}%",
                baseWeights.fundamental * 100, baseWeights.technical * 100, baseWeights.sentiment * 100);
        log.info("[置信度传播] 调整后权重: 基本面{:.0f}%(置信度:{}) 技术面{:.0f}%(置信度:{}) 情绪面{:.0f}%(置信度:{})",
                adjusted.fundamental * 100, fundamentalConfidence,
                adjusted.technical * 100, technicalConfidence,
                adjusted.sentiment * 100, sentimentConfidence);

        return adjusted;
    }

    /**
     * 计算加权置信度（作为最终建议的置信度）
     * @param confidences 各维度置信度
     * @param weights 调整后的权重
     * @return 加权置信度（HIGH/MEDIUM/LOW）
     */
    public String calculateWeightedConfidence(Map<String, String> confidences, AdjustedWeights weights) {
        // 将置信度转换为数值（HIGH=3, MEDIUM=2, LOW=1）
        int fundamentalScore = confidenceToScore(confidences.getOrDefault("FUNDAMENTAL", "MEDIUM"));
        int technicalScore = confidenceToScore(confidences.getOrDefault("TECHNICAL", "MEDIUM"));
        int sentimentScore = confidenceToScore(confidences.getOrDefault("SENTIMENT", "MEDIUM"));

        // 加权平均
        double weightedScore = fundamentalScore * weights.fundamental
                             + technicalScore * weights.technical
                             + sentimentScore * weights.sentiment;

        // 转换回置信度等级
        if (weightedScore >= 2.5) {
            return "HIGH";
        } else if (weightedScore >= 1.5) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 获取置信度调整因子
     */
    private double getConfidenceFactor(String confidence) {
        return switch (confidence) {
            case "HIGH" -> HIGH_CONFIDENCE_BOOST;
            case "LOW" -> LOW_CONFIDENCE_PENALTY;
            default -> MEDIUM_CONFIDENCE_FACTOR;
        };
    }

    /**
     * 置信度转数值
     */
    private int confidenceToScore(String confidence) {
        return switch (confidence) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 2;
        };
    }

    /**
     * 调整后的权重
     */
    @Data
    public static class AdjustedWeights {
        public double fundamental;
        public double technical;
        public double sentiment;

        @Override
        public String toString() {
            return String.format("基本面%.0f%% 技术面%.0f%% 情绪面%.0f%%",
                    fundamental * 100, technical * 100, sentiment * 100);
        }
    }
}
