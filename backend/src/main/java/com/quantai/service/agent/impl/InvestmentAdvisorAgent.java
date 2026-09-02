package com.quantai.service.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.model.entity.SuggestionTracking;
import com.quantai.service.AdaptiveWeightService;
import com.quantai.service.BacktestService;
import com.quantai.service.ConfidencePropagationService;
import com.quantai.service.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 投资决策 Agent（维度型架构 + 自适应权重 + 回测追踪）
 * 职责：
 * 1. 综合基本面、技术面、情绪面的分析结果
 * 2. 通过自适应权重加权投票给出最终投资建议
 * 3. 保存建议到回测系统，支持准确率追踪
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentAdvisorAgent implements Agent {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final AdaptiveWeightService adaptiveWeightService;
    private final BacktestService backtestService;
    private final ConfidencePropagationService confidencePropagationService;

    @Override
    public String getName() {
        return "InvestmentAdvisor";
    }

    @Override
    public String getRole() {
        return "投资决策专家";
    }

    @Override
    public String getGoal() {
        return "综合基本面、技术面、情绪面的分析结果，通过自适应权重加权投票给出最终投资建议";
    }

    @Override
    public List<String> getToolNames() {
        return List.of();
    }

    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        log.info("[{}] 开始执行 - 股票代码: {}", getName(), context.getStockCode());

        try {
            // 获取四个维度Agent的输出
            String fundamentalOutput = context.getPreviousOutput("FundamentalAnalysis");
            String technicalOutput = context.getPreviousOutput("TechnicalAnalysis");
            String sentimentOutput = context.getPreviousOutput("SentimentAnalysis");
            String riskOutput = context.getPreviousOutput("RiskAssessment");

            // 检查前置数据
            if (fundamentalOutput == null && technicalOutput == null && sentimentOutput == null) {
                log.warn("[{}] 缺少所有维度的分析结果", getName());
                return AgentResult.failure("缺少前置分析结果", System.currentTimeMillis() - start);
            }

            // 解析各维度的建议（包含自适应权重 + 风险干预）
            VotingResult votingResult = performVoting(fundamentalOutput, technicalOutput, sentimentOutput, riskOutput, context.getStockCode());

            // 调用LLM生成最终建议
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(context, fundamentalOutput, technicalOutput, sentimentOutput, riskOutput, votingResult);

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));

            ChatResponse response = chatModel.call(prompt);
            String output = response.getResult().getOutput().getContent();

            int tokenUsed = 0;
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                tokenUsed = response.getMetadata().getUsage().getTotalTokens().intValue();
            }

            long duration = System.currentTimeMillis() - start;
            log.info("[{}] 执行完成 - 耗时: {}ms, Token: {}, 最终建议: {}",
                    getName(), duration, tokenUsed, votingResult.finalSuggestion);

            // ========== 保存到回测系统 ==========
            saveSuggestionForBacktest(context, votingResult, output);

            return AgentResult.success(output, duration, tokenUsed, 1);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[{}] 执行失败", getName(), e);
            return AgentResult.failure("投资决策失败: " + e.getMessage(), duration);
        }
    }

    /**
     * 加权投票机制（自适应权重 + 置信度传播 + 风险干预）
     */
    private VotingResult performVoting(String fundamentalOutput, String technicalOutput, String sentimentOutput, String riskOutput, String stockCode) {
        VotingResult result = new VotingResult();

        try {
            // 解析各维度的建议和评分
            DimensionResult fundamental = parseDimensionResult(fundamentalOutput);
            DimensionResult technical = parseDimensionResult(technicalOutput);
            DimensionResult sentiment = parseDimensionResult(sentimentOutput);
            DimensionResult risk = parseDimensionResult(riskOutput);

            // ========== 自适应权重计算 ==========
            int fundamentalScore = fundamental != null ? fundamental.score : 0;
            int technicalScore = technical != null ? technical.score : 0;
            int sentimentScore = sentiment != null ? sentiment.score : 0;

            AdaptiveWeightService.WeightProfile baseWeights = adaptiveWeightService.calculateWeights(
                    stockCode, fundamentalScore, technicalScore, sentimentScore);

            // 保存基础权重（用于对比）
            result.baseWeightFundamental = baseWeights.fundamental;
            result.baseWeightTechnical = baseWeights.technical;
            result.baseWeightSentiment = baseWeights.sentiment;

            log.info("[自适应权重] 股票: {}, 基础权重: 基本面{:.0f}% 技术面{:.0f}% 情绪面{:.0f}%",
                    stockCode, baseWeights.fundamental * 100, baseWeights.technical * 100, baseWeights.sentiment * 100);

            // ========== 置信度传播：调整权重 ==========
            Map<String, String> confidences = new HashMap<>();
            if (fundamental != null) confidences.put("FUNDAMENTAL", fundamental.confidence);
            if (technical != null) confidences.put("TECHNICAL", technical.confidence);
            if (sentiment != null) confidences.put("SENTIMENT", sentiment.confidence);

            ConfidencePropagationService.AdjustedWeights adjustedWeights =
                    confidencePropagationService.adjustWeightsByConfidence(baseWeights, confidences);

            result.fundamentalWeight = adjustedWeights.fundamental;
            result.technicalWeight = adjustedWeights.technical;
            result.sentimentWeight = adjustedWeights.sentiment;
            result.fundamentalConfidence = fundamental != null ? fundamental.confidence : "MEDIUM";
            result.technicalConfidence = technical != null ? technical.confidence : "MEDIUM";
            result.sentimentConfidence = sentiment != null ? sentiment.confidence : "MEDIUM";
            result.confidenceApplied = true;

            log.info("[置信度传播] 股票: {}, 调整后权重: 基本面{:.0f}%({}置信) 技术面{:.0f}%({}置信) 情绪面{:.0f}%({}置信)",
                    stockCode,
                    adjustedWeights.fundamental * 100, result.fundamentalConfidence,
                    adjustedWeights.technical * 100, result.technicalConfidence,
                    adjustedWeights.sentiment * 100, result.sentimentConfidence);

            // 计算加权评分（使用置信度调整后的权重）
            double totalScore = 0;
            double totalWeight = 0;

            if (fundamental != null) {
                totalScore += fundamental.score * adjustedWeights.fundamental;
                totalWeight += adjustedWeights.fundamental;
                result.fundamentalVote = fundamental.suggestion;
                result.fundamentalScore = fundamental.score;
            }
            if (technical != null) {
                totalScore += technical.score * adjustedWeights.technical;
                totalWeight += adjustedWeights.technical;
                result.technicalVote = technical.suggestion;
                result.technicalScore = technical.score;
            }
            if (sentiment != null) {
                totalScore += sentiment.score * adjustedWeights.sentiment;
                totalWeight += adjustedWeights.sentiment;
                result.sentimentVote = sentiment.suggestion;
                result.sentimentScore = sentiment.score;
            }

            result.weightedScore = totalWeight > 0 ? (int) (totalScore / totalWeight) : 0;

            // 解析风险评分（注意：score越低风险越高）
            int riskScore = risk != null ? risk.score : 50;
            result.riskScore = riskScore;

            // 投票决策（基于加权评分）
            if (result.weightedScore >= 70) {
                result.finalSuggestion = "BUY";
            } else if (result.weightedScore <= 30) {
                result.finalSuggestion = "SELL";
            } else {
                result.finalSuggestion = "HOLD";
            }

            // 检测矛盾（当各维度建议不一致时）
            String[] votes = {result.fundamentalVote, result.technicalVote, result.sentimentVote};
            long uniqueVotes = java.util.Arrays.stream(votes)
                    .filter(v -> v != null)
                    .distinct()
                    .count();
            result.hasConflict = uniqueVotes >= 2;

            // ========== 风险干预机制 ==========
            if (riskScore < 30) {
                // 极端高风险（0-29分）：强制降级
                if ("BUY".equals(result.finalSuggestion)) {
                    result.originalSuggestion = result.finalSuggestion;
                    result.finalSuggestion = "HOLD";
                    result.riskOverride = true;
                    result.overrideReason = String.format("风险评分仅%d分，存在极端风险，建议观望", riskScore);
                    log.warn("[风险干预] 极端高风险，强制 BUY → HOLD，风险评分: {}", riskScore);
                }
            } else if (riskScore < 50) {
                // 中等偏高风险（30-49分）：降低置信度
                if (risk != null && "HIGH".equals(risk.confidence)) {
                    result.riskAdjusted = true;
                    result.adjustReason = String.format("风险评分%d分，存在一定风险，降低置信度", riskScore);
                    log.info("[风险干预] 中高风险，降低置信度，风险评分: {}", riskScore);
                }
            }

        } catch (Exception e) {
            log.error("投票机制执行失败", e);
            result.finalSuggestion = "HOLD";
            result.weightedScore = 50;
        }

        return result;
    }

    /**
     * 解析维度分析结果
     */
    private DimensionResult parseDimensionResult(String output) {
        if (output == null || output.isEmpty()) return null;

        try {
            // 尝试解析JSON格式
            JsonNode node = objectMapper.readTree(output);
            DimensionResult result = new DimensionResult();
            result.score = node.has("score") ? node.get("score").asInt() : 50;
            result.suggestion = node.has("suggestion") ? node.get("suggestion").asText() : "HOLD";
            result.confidence = node.has("confidence") ? node.get("confidence").asText() : "MEDIUM";
            return result;
        } catch (Exception e) {
            log.warn("解析维度结果失败，使用默认值", e);
            // 如果解析失败，返回默认值
            DimensionResult result = new DimensionResult();
            result.score = 50;
            result.suggestion = "HOLD";
            result.confidence = "LOW";
            return result;
        }
    }

    private String buildSystemPrompt() {
        return """
                你是一名资深投资决策专家，负责综合多维度分析结果，给出最终投资建议。

                你的职责：
                1. 综合基本面、技术面、情绪面的分析结果
                2. 识别各维度之间的一致性和矛盾点
                3. 给出明确的投资建议和风险提示
                4. 说明决策依据和置信度

                决策原则：
                - 基本面是长期价值的基础（权重40%）
                - 技术面反映短期趋势和时机（权重30%）
                - 情绪面提供辅助参考和风险警示（权重30%）
                - 当三个维度一致时，置信度HIGH
                - 当维度之间有矛盾时，重点分析矛盾原因，置信度MEDIUM或LOW

                输出要求（JSON格式）：
                {
                  "finalSuggestion": "BUY|SELL|HOLD",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "targetPrice": "预期目标价（可选）",
                  "stopLoss": "止损价（可选）",
                  "reason": "决策理由（100字以内）",
                  "assumptions": [
                    "关键假设1（如：假设基本面持续改善，ROE保持30%+）",
                    "关键假设2（如：假设技术面上升趋势延续，不跌破MA20）",
                    "关键假设3（如：假设市场情绪保持乐观，资金持续流入）",
                    "关键假设4（如：假设无重大黑天鹅事件）"
                  ],
                  "riskWarning": "风险提示（50字以内）"
                }

                注意：必须输出3-4条关键假设，这些假设是投资决策成立的前提条件。
                """;
    }

    private String buildUserPrompt(AgentContext context, String fundamentalOutput,
                                   String technicalOutput, String sentimentOutput, String riskOutput, VotingResult votingResult) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(String.format("""
                请综合以下四个维度的分析结果，给出最终投资建议。

                股票代码：%s
                用户问题：%s

                ## 投票结果
                - 基本面建议：%s（置信度：%s）
                - 技术面建议：%s（置信度：%s）
                - 情绪面建议：%s（置信度：%s）
                - 加权综合评分：%d分
                - 投票结果：%s
                - 是否存在矛盾：%s

                ## 置信度传播（数据质量调整）
                - 基础权重：基本面%.0f%% 技术面%.0f%% 情绪面%.0f%%
                - 调整后权重：基本面%.0f%%(置信度:%s) 技术面%.0f%%(置信度:%s) 情绪面%.0f%%(置信度:%s)
                - 说明：低置信度维度权重降低40%%，高置信度维度权重提升20%%
                """,
                context.getStockCode(),
                context.getUserQuestion() != null ? context.getUserQuestion() : "分析投资价值",
                votingResult.fundamentalVote != null ? votingResult.fundamentalVote : "未分析",
                votingResult.fundamentalConfidence != null ? votingResult.fundamentalConfidence : "MEDIUM",
                votingResult.technicalVote != null ? votingResult.technicalVote : "未分析",
                votingResult.technicalConfidence != null ? votingResult.technicalConfidence : "MEDIUM",
                votingResult.sentimentVote != null ? votingResult.sentimentVote : "未分析",
                votingResult.sentimentConfidence != null ? votingResult.sentimentConfidence : "MEDIUM",
                votingResult.weightedScore,
                votingResult.finalSuggestion,
                votingResult.hasConflict ? "是" : "否",
                votingResult.baseWeightFundamental * 100,
                votingResult.baseWeightTechnical * 100,
                votingResult.baseWeightSentiment * 100,
                votingResult.fundamentalWeight * 100, votingResult.fundamentalConfidence,
                votingResult.technicalWeight * 100, votingResult.technicalConfidence,
                votingResult.sentimentWeight * 100, votingResult.sentimentConfidence));

        // 风险干预信息
        if (votingResult.riskOverride) {
            prompt.append(String.format("""

                ## ⚠️ 风险干预
                - 原始建议：%s
                - 风险评分：%d分（极端高风险）
                - 干预结果：强制降级为 %s
                - 干预原因：%s
                """,
                    votingResult.originalSuggestion,
                    votingResult.riskScore,
                    votingResult.finalSuggestion,
                    votingResult.overrideReason));
        } else if (votingResult.riskAdjusted) {
            prompt.append(String.format("""

                ## ⚠️ 风险提示
                - 风险评分：%d分（中等偏高风险）
                - 建议：%s
                """,
                    votingResult.riskScore,
                    votingResult.adjustReason));
        } else {
            prompt.append(String.format("""

                ## 风险评估
                - 风险评分：%d分
                """,
                    votingResult.riskScore));
        }

        prompt.append(String.format("""

                ## 基本面分析
                %s

                ## 技术面分析
                %s

                ## 情绪面分析
                %s

                ## 风险评估详情
                %s

                请完成以下任务：

                1. **一致性分析**
                   - 四个维度的结论是否一致？
                   - 如果不一致，主要矛盾在哪里？

                2. **综合判断**
                   - 基于加权投票结果（%s）和综合评分（%d分）
                   - 风险评分：%d分
                   - 给出最终建议：BUY/SELL/HOLD
                   - 说明决策依据

                3. **风险提示**
                   - 当前主要风险是什么？
                   - 需要关注哪些指标或事件？

                4. **操作建议**
                   - 如果是BUY，建议买入时机和目标价
                   - 如果是SELL，建议卖出理由和止损价
                   - 如果是HOLD，建议观察等待的理由

                5. **关键假设（必须输出3-4条）**
                   - 基本面假设（如：假设ROE保持30%%+，营收增速15%%+）
                   - 技术面假设（如：假设上升趋势延续，不跌破MA20支撑）
                   - 情绪面假设（如：假设市场情绪保持乐观，资金持续流入）
                   - 风险假设（如：假设风险可控，设定止损位）
                   - 外部假设（如：假设无重大黑天鹅事件，行业政策稳定）

                请严格按JSON格式输出。
                """,
                fundamentalOutput != null ? fundamentalOutput : "未分析",
                technicalOutput != null ? technicalOutput : "未分析",
                sentimentOutput != null ? sentimentOutput : "未分析",
                riskOutput != null ? riskOutput : "未分析",
                votingResult.finalSuggestion,
                votingResult.weightedScore,
                votingResult.riskScore));

        // 如果发生了风险干预，特别提醒LLM
        if (votingResult.riskOverride) {
            prompt.append("""

                ⚠️ 重要提示：系统已基于极端高风险将建议从BUY强制降级为HOLD。
                请在最终输出中明确说明风险干预逻辑，并建议用户谨慎观望或轻仓试探。
                """);
        }

        return prompt.toString();
    }

    /**
     * 保存建议到回测系统
     */
    private void saveSuggestionForBacktest(AgentContext context, VotingResult votingResult, String fullReport) {
        try {
            // 解析当前价格（从context或市场数据获取）
            BigDecimal currentPrice = getCurrentPrice(context);
            if (currentPrice == null) {
                log.warn("[回测追踪] 无法获取当前价格，跳过保存");
                return;
            }

            SuggestionTracking tracking = new SuggestionTracking();
            tracking.setStockCode(context.getStockCode());
            tracking.setStockName(context.getStockName() != null ? context.getStockName() : context.getStockCode());
            tracking.setSuggestion(votingResult.finalSuggestion);
            tracking.setConfidence(determineConfidence(votingResult));
            tracking.setSuggestedAt(LocalDateTime.now());
            tracking.setSuggestedPrice(currentPrice);

            // 维度评分
            tracking.setWeightedScore(votingResult.weightedScore);
            tracking.setFundamentalScore(votingResult.fundamentalScore);
            tracking.setTechnicalScore(votingResult.technicalScore);
            tracking.setSentimentScore(votingResult.sentimentScore);
            tracking.setRiskScore(votingResult.riskScore);

            // 风险干预记录
            tracking.setRiskOverride(votingResult.riskOverride);
            tracking.setOriginalSuggestion(votingResult.originalSuggestion);

            // 保存到数据库
            backtestService.saveSuggestion(tracking);

            log.info("[回测追踪] 保存成功 - 股票: {}, 建议: {}, 价格: {}, 加权分: {}",
                    context.getStockCode(), votingResult.finalSuggestion, currentPrice, votingResult.weightedScore);

        } catch (Exception e) {
            log.error("[回测追踪] 保存失败", e);
        }
    }

    /**
     * 获取当前价格
     */
    private BigDecimal getCurrentPrice(AgentContext context) {
        try {
            // 从上下文元数据中获取
            if (context.getMetadata() != null && context.getMetadata().containsKey("currentPrice")) {
                Object price = context.getMetadata().get("currentPrice");
                if (price instanceof BigDecimal) {
                    return (BigDecimal) price;
                } else if (price instanceof Number) {
                    return BigDecimal.valueOf(((Number) price).doubleValue());
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("获取当前价格失败", e);
            return null;
        }
    }

    /**
     * 确定置信度（考虑置信度传播）
     */
    private String determineConfidence(VotingResult votingResult) {
        // 如果有风险干预，置信度降低
        if (votingResult.riskOverride) {
            return "LOW";
        }

        // 如果有矛盾，置信度降低
        if (votingResult.hasConflict) {
            return "MEDIUM";
        }

        // ========== 置信度传播：计算加权置信度 ==========
        if (votingResult.confidenceApplied) {
            Map<String, String> confidences = new HashMap<>();
            confidences.put("FUNDAMENTAL", votingResult.fundamentalConfidence);
            confidences.put("TECHNICAL", votingResult.technicalConfidence);
            confidences.put("SENTIMENT", votingResult.sentimentConfidence);

            com.quantai.service.ConfidencePropagationService.AdjustedWeights weights =
                    new com.quantai.service.ConfidencePropagationService.AdjustedWeights();
            weights.fundamental = votingResult.fundamentalWeight;
            weights.technical = votingResult.technicalWeight;
            weights.sentiment = votingResult.sentimentWeight;

            String weightedConfidence = confidencePropagationService.calculateWeightedConfidence(confidences, weights);
            log.info("[置信度传播] 加权置信度: {}", weightedConfidence);
            return weightedConfidence;
        }

        // 兜底：基于加权评分判断
        if (votingResult.weightedScore >= 80 || votingResult.weightedScore <= 20) {
            return "HIGH";
        } else if (votingResult.weightedScore >= 60 || votingResult.weightedScore <= 40) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    // 内部类：投票结果
    private static class VotingResult {
        String fundamentalVote;
        String technicalVote;
        String sentimentVote;
        int weightedScore;
        String finalSuggestion;
        boolean hasConflict;

        // 维度评分（用于回测追踪）
        Integer fundamentalScore;
        Integer technicalScore;
        Integer sentimentScore;

        // 基础权重（自适应权重，未应用置信度调整）
        Double baseWeightFundamental;
        Double baseWeightTechnical;
        Double baseWeightSentiment;

        // 最终权重（应用置信度传播后）
        Double fundamentalWeight;
        Double technicalWeight;
        Double sentimentWeight;

        // 置信度传播相关
        String fundamentalConfidence;
        String technicalConfidence;
        String sentimentConfidence;
        boolean confidenceApplied;

        // 风险干预字段
        int riskScore;                    // 风险评分（0-100，越低风险越高）
        boolean riskOverride;             // 是否发生风险干预（极端高风险强制降级）
        String originalSuggestion;        // 原始建议（被干预前）
        String overrideReason;            // 干预原因
        boolean riskAdjusted;             // 是否发生风险调整（置信度降低）
        String adjustReason;              // 调整原因
    }

    // 内部类：维度分析结果
    private static class DimensionResult {
        int score;
        String suggestion;
        String confidence;
    }
}
