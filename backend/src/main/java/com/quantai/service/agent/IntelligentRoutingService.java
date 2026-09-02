package com.quantai.service.agent;

import com.quantai.model.dto.AnalysisSnapshot;
import com.quantai.service.AnalysisMemoryService;
import com.quantai.service.agent.impl.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能路由服务 - 三档路由策略 + 会话记忆
 * 根据用户问题复杂度，选择不同的分析路径：
 * - 简单查询：ReAct Agent（5秒）
 * - 单维度分析：调用单个维度Agent（20秒）
 * - 完整分析：调用所有维度Agent + 投票决策（60秒）
 *
 * 会话记忆优化：
 * - 缓存完整分析结果（TTL=1小时）
 * - 避免短时间内重复分析同一股票
 * - 节省Token成本，提升响应速度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntelligentRoutingService {

    private final ReActAgentService reActAgentService;
    private final FundamentalAnalysisAgent fundamentalAnalysisAgent;
    private final TechnicalAnalysisAgent technicalAnalysisAgent;
    private final SentimentAnalysisAgent sentimentAnalysisAgent;
    private final RiskAssessmentAgent riskAssessmentAgent;
    private final InvestmentAdvisorAgent investmentAdvisorAgent;
    private final AnalysisMemoryService memoryService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    /**
     * 智能路由入口（带会话记忆）
     */
    public String analyze(String stockCode, String userQuestion) {
        long start = System.currentTimeMillis();

        // 1. 问题分类
        QueryType queryType = classifyQuery(userQuestion);
        log.info("问题分类结果: {} - 问题: {}", queryType, userQuestion);

        // 2. 尝试从缓存中召回（仅完整分析支持缓存，简单查询和单维度实时性要求高）
        if (queryType == QueryType.COMPREHENSIVE) {
            Optional<AnalysisSnapshot> cached = memoryService.recall(stockCode, "FULL");
            if (cached.isPresent()) {
                AnalysisSnapshot snapshot = cached.get();
                log.info("[会话记忆命中] 股票: {}, 缓存年龄: {}秒", stockCode, snapshot.getAgeInSeconds());
                return buildCachedResponse(snapshot);
            }
        }

        String result;
        try {
            // 3. 根据分类路由
            switch (queryType) {
                case SIMPLE_QUERY -> result = handleSimpleQuery(stockCode, userQuestion);
                case FUNDAMENTAL_ONLY -> result = handleSingleDimension(stockCode, userQuestion, "FUNDAMENTAL");
                case TECHNICAL_ONLY -> result = handleSingleDimension(stockCode, userQuestion, "TECHNICAL");
                case SENTIMENT_ONLY -> result = handleSingleDimension(stockCode, userQuestion, "SENTIMENT");
                case COMPREHENSIVE -> result = handleComprehensiveAnalysis(stockCode, userQuestion);
                default -> result = handleComprehensiveAnalysis(stockCode, userQuestion);
            }

            long duration = System.currentTimeMillis() - start;
            log.info("分析完成 - 路由类型: {}, 耗时: {}ms", queryType, duration);
            return result;

        } catch (Exception e) {
            log.error("分析失败", e);
            return "分析失败: " + e.getMessage();
        }
    }

    /**
     * 问题分类
     */
    private QueryType classifyQuery(String question) {
        if (question == null || question.isEmpty()) {
            return QueryType.COMPREHENSIVE;
        }

        String q = question.toLowerCase();

        // 简单查询关键词
        if (q.contains("价格") || q.contains("多少") || q.contains("pe") || q.contains("市盈率")
                || q.contains("市值") || q.contains("涨跌") || q.matches(".*\\d+.*")) {
            return QueryType.SIMPLE_QUERY;
        }

        // 基本面分析关键词
        if (q.contains("财务") || q.contains("盈利") || q.contains("营收") || q.contains("净利润")
                || q.contains("roe") || q.contains("负债") || q.contains("估值") || q.contains("基本面")) {
            return QueryType.FUNDAMENTAL_ONLY;
        }

        // 技术面分析关键词
        if (q.contains("技术") || q.contains("k线") || q.contains("趋势") || q.contains("均线")
                || q.contains("macd") || q.contains("支撑") || q.contains("阻力") || q.contains("突破")) {
            return QueryType.TECHNICAL_ONLY;
        }

        // 情绪面分析关键词
        if (q.contains("情绪") || q.contains("舆情") || q.contains("新闻") || q.contains("资金")
                || q.contains("流向") || q.contains("主力") || q.contains("龙虎榜")) {
            return QueryType.SENTIMENT_ONLY;
        }

        // 完整分析关键词
        if (q.contains("投资") || q.contains("价值") || q.contains("分析") || q.contains("建议")
                || q.contains("买") || q.contains("卖") || q.contains("持有")) {
            return QueryType.COMPREHENSIVE;
        }

        // 默认：完整分析
        return QueryType.COMPREHENSIVE;
    }

    /**
     * 处理简单查询（ReAct）
     */
    private String handleSimpleQuery(String stockCode, String userQuestion) {
        log.info("[简单查询路由] 使用ReAct Agent - 股票: {}", stockCode);
        ReActResult result = reActAgentService.run(userQuestion + " (股票代码: " + stockCode + ")");
        return result.getFinalAnswer() != null ? result.getFinalAnswer() : "查询失败";
    }

    /**
     * 处理单维度分析
     */
    private String handleSingleDimension(String stockCode, String userQuestion, String dimension) {
        log.info("[单维度路由] 维度: {} - 股票: ", dimension, stockCode);

        AgentContext context = new AgentContext();
        context.setStockCode(stockCode);
        context.setUserQuestion(userQuestion);

        AgentResult result = switch (dimension) {
            case "FUNDAMENTAL" -> fundamentalAnalysisAgent.execute(context);
            case "TECHNICAL" -> technicalAnalysisAgent.execute(context);
            case "SENTIMENT" -> sentimentAnalysisAgent.execute(context);
            default -> throw new IllegalArgumentException("未知维度: " + dimension);
        };

        if (result.isSuccess()) {
            return result.getOutput();
        } else {
            return "分析失败: " + result.getError();
        }
    }

    /**
     * 处理完整分析（四维度并行 + 投资决策）
     */
    private String handleComprehensiveAnalysis(String stockCode, String userQuestion) {
        log.info("[完整分析路由] 四维度并行分析 - 股票: {}", stockCode);

        AgentContext context = new AgentContext();
        context.setStockCode(stockCode);
        context.setUserQuestion(userQuestion);

        try {
            // 1. 并行执行四个维度的分析
            CompletableFuture<AgentResult> fundamentalFuture = CompletableFuture.supplyAsync(
                    () -> fundamentalAnalysisAgent.execute(context), executorService);

            CompletableFuture<AgentResult> technicalFuture = CompletableFuture.supplyAsync(
                    () -> technicalAnalysisAgent.execute(context), executorService);

            CompletableFuture<AgentResult> sentimentFuture = CompletableFuture.supplyAsync(
                    () -> sentimentAnalysisAgent.execute(context), executorService);

            CompletableFuture<AgentResult> riskFuture = CompletableFuture.supplyAsync(
                    () -> riskAssessmentAgent.execute(context), executorService);

            // 等待所有分析完成
            CompletableFuture.allOf(fundamentalFuture, technicalFuture, sentimentFuture, riskFuture).join();

            // 2. 收集结果
            AgentResult fundamentalResult = fundamentalFuture.get();
            AgentResult technicalResult = technicalFuture.get();
            AgentResult sentimentResult = sentimentFuture.get();
            AgentResult riskResult = riskFuture.get();

            // 将结果存入context
            if (fundamentalResult.isSuccess()) {
                context.addPreviousResult("FundamentalAnalysis", fundamentalResult);
            }
            if (technicalResult.isSuccess()) {
                context.addPreviousResult("TechnicalAnalysis", technicalResult);
            }
            if (sentimentResult.isSuccess()) {
                context.addPreviousResult("SentimentAnalysis", sentimentResult);
            }
            if (riskResult.isSuccess()) {
                context.addPreviousResult("RiskAssessment", riskResult);
            }

            // 3. 综合投资决策
            AgentResult advisorResult = investmentAdvisorAgent.execute(context);

            if (advisorResult.isSuccess()) {
                // 保存完整分析快照到缓存
                saveAnalysisSnapshot(stockCode, context, advisorResult);
                return advisorResult.getOutput();
            } else {
                return "投资决策失败: " + advisorResult.getError();
            }

        } catch (Exception e) {
            log.error("完整分析失败", e);
            return "完整分析失败: " + e.getMessage();
        }
    }

    /**
     * 保存完整分析快照到缓存
     */
    private void saveAnalysisSnapshot(String stockCode, AgentContext context, AgentResult advisorResult) {
        try {
            // 构建维度输出Map
            Map<String, String> dimensionOutputs = new HashMap<>();
            context.getPreviousResults().forEach((key, result) -> {
                if (result.isSuccess()) {
                    dimensionOutputs.put(key, result.getOutput());
                }
            });

            // 从投资决策结果中提取关键信息（假设输出是JSON格式）
            String finalSuggestion = extractField(advisorResult.getOutput(), "suggestion", "UNKNOWN");
            String confidence = extractField(advisorResult.getOutput(), "confidence", "UNKNOWN");
            Integer weightedScore = extractIntField(advisorResult.getOutput(), "weighted_score", 50);
            Integer riskScore = extractIntField(advisorResult.getOutput(), "risk_score", 50);
            Boolean riskOverride = extractBoolField(advisorResult.getOutput(), "risk_override", false);
            String originalSuggestion = extractField(advisorResult.getOutput(), "original_suggestion", null);

            AnalysisSnapshot snapshot = AnalysisSnapshot.builder()
                    .stockCode(stockCode)
                    .stockName(context.getStockName())
                    .analysisType("FULL")
                    .analyzedAt(LocalDateTime.now())
                    .dimensionOutputs(dimensionOutputs)
                    .finalSuggestion(finalSuggestion)
                    .confidence(confidence)
                    .weightedScore(weightedScore)
                    .riskScore(riskScore)
                    .riskOverride(riskOverride)
                    .originalSuggestion(originalSuggestion)
                    .fullReport(advisorResult.getOutput())
                    .build();

            memoryService.save(snapshot);
            log.info("[会话记忆保存] 股票: {}, 建议: {}, 置信度: {}", stockCode, finalSuggestion, confidence);

        } catch (Exception e) {
            log.warn("[会话记忆保存失败] 股票: {}, 原因: {}", stockCode, e.getMessage());
        }
    }

    /**
     * 构建缓存命中的响应
     */
    private String buildCachedResponse(AnalysisSnapshot snapshot) {
        long ageSeconds = snapshot.getAgeInSeconds();
        long ageMinutes = ageSeconds / 60;

        return String.format("""
                📋 **会话记忆命中**（缓存于 %d 分钟前）

                %s

                ---
                💡 提示：此结果来自缓存，若需最新分析请稍后重试或使用"强制刷新"指令。
                """, ageMinutes, snapshot.getFullReport());
    }

    /**
     * 从JSON字符串中提取字段（简单实现）
     */
    private String extractField(String json, String fieldName, String defaultValue) {
        try {
            int start = json.indexOf("\"" + fieldName + "\"");
            if (start == -1) return defaultValue;

            int valueStart = json.indexOf(":", start) + 1;
            int valueEnd = json.indexOf(",", valueStart);
            if (valueEnd == -1) valueEnd = json.indexOf("}", valueStart);

            String value = json.substring(valueStart, valueEnd).trim();
            return value.replaceAll("\"", "");
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Integer extractIntField(String json, String fieldName, Integer defaultValue) {
        try {
            String value = extractField(json, fieldName, String.valueOf(defaultValue));
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Boolean extractBoolField(String json, String fieldName, Boolean defaultValue) {
        try {
            String value = extractField(json, fieldName, String.valueOf(defaultValue));
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 问题类型枚举
     */
    private enum QueryType {
        SIMPLE_QUERY,       // 简单查询（ReAct，5秒）
        FUNDAMENTAL_ONLY,   // 仅基本面分析（20秒）
        TECHNICAL_ONLY,     // 仅技术面分析（20秒）
        SENTIMENT_ONLY,     // 仅情绪面分析（20秒）
        COMPREHENSIVE       // 完整分析（60秒）
    }
}
