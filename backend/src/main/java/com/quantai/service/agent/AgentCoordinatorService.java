package com.quantai.service.agent;

import com.quantai.model.vo.*;
import com.quantai.service.AiAnalysisService;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 多Agent协作编排服务
 * 流水线：新闻舆情Agent → 市场分析Agent → 交易建议Agent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCoordinatorService {

    private final MarketDataAgent marketDataAgent;
    private final SuggestionAgent suggestionAgent;
    private final AiAnalysisService aiAnalysisService;
    private final StockService stockService;

    /**
     * 运行完整的多Agent协同分析
     */
    public CollaborationResult collaborate(String stockCode, String newsTitle, String newsContent) {
        long overallStart = System.currentTimeMillis();
        List<AgentStep> steps = new ArrayList<>();

        String stockName = resolveStockName(stockCode);

        // ===== Step 1 & 2 并行执行（舆情 + 市场分析互不依赖） =====
        long tParallelStart = System.currentTimeMillis();
        CompletableFuture<String> newsFuture = CompletableFuture.supplyAsync(() -> {
            if (newsTitle == null || newsTitle.isEmpty()) return null;
            try {
                return aiAnalysisService.analyzeSentiment(newsTitle, newsContent != null ? newsContent : "");
            } catch (Exception e) {
                log.warn("新闻舆情分析失败", e);
                return "舆情分析暂不可用";
            }
        });

        CompletableFuture<MarketAnalysis> marketFuture = CompletableFuture.supplyAsync(() ->
                marketDataAgent.analyze(stockCode));

        String newsAnalysis;
        MarketAnalysis marketAnalysis;
        try {
            CompletableFuture.allOf(newsFuture, marketFuture).join();
            newsAnalysis = newsFuture.get();
            marketAnalysis = marketFuture.get();
        } catch (Exception e) {
            log.error("并行Agent执行失败", e);
            newsAnalysis = null;
            marketAnalysis = new MarketAnalysis();
        }
        long parallelDuration = System.currentTimeMillis() - tParallelStart;

        // 按顺序添加 Step 1 & 2（并行执行，共用耗时）
        steps.add(AgentStep.builder()
                .agentName("新闻舆情Agent")
                .description("分析新闻情绪倾向（利好/利空/中性）")
                .status(newsAnalysis != null ? "completed" : "skipped")
                .inputSummary(newsTitle != null ? newsTitle.substring(0, Math.min(30, newsTitle.length())) + "..." : "无输入")
                .outputSummary(newsAnalysis != null ? extractSummary(newsAnalysis, 50) : "无新闻数据，跳过")
                .rawOutput(newsAnalysis)
                .durationMs(parallelDuration)
                .build());

        steps.add(AgentStep.builder()
                .agentName("市场分析Agent")
                .description("分析K线趋势、均线位置、量能变化")
                .status("completed")
                .inputSummary("股票 " + stockName + "(" + stockCode + ") 近20日K线数据")
                .outputSummary(buildMarketSummary(marketAnalysis))
                .rawOutput(marketAnalysis)
                .durationMs(parallelDuration)
                .build());

        // ===== Step 3: 交易建议Agent（串行，依赖前两步结果） =====
        long t3 = System.currentTimeMillis();
        TradeSuggestion suggestion = suggestionAgent.suggest(stockCode, stockName, newsAnalysis, marketAnalysis);
        steps.add(AgentStep.builder()
                .agentName("交易建议Agent")
                .description("综合舆情与市场数据，生成操作建议")
                .status("completed")
                .inputSummary("舆情分析 + " + stockName + "市场数据")
                .outputSummary(suggestion.getSuggestionSummary() != null ?
                        suggestion.getSuggestionSummary() : suggestion.getActionLabel() + "建议")
                .rawOutput(suggestion)
                .durationMs(System.currentTimeMillis() - t3)
                .build());

        long totalMs = System.currentTimeMillis() - overallStart;

        log.info("多Agent协同分析完成 code={} action={} 总耗时={}ms",
                stockCode, suggestion.getAction(), totalMs);

        return CollaborationResult.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .steps(steps)
                .suggestion(suggestion)
                .totalDurationMs(totalMs)
                .build();
    }

    private String resolveStockName(String code) {
        try {
            var quote = stockService.getQuote(code);
            if (quote != null && quote.getName() != null) return quote.getName();
        } catch (Exception ignored) {}
        return code;
    }

    private String extractSummary(String text, int maxLen) {
        if (text == null) return "";
        String search = "\"summary\":\"";
        int idx = text.indexOf(search);
        if (idx >= 0) {
            int start = idx + search.length();
            int end = text.indexOf("\"", start);
            if (end > start) {
                String s = text.substring(start, end);
                return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
            }
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String buildMarketSummary(MarketAnalysis m) {
        return m.getTrendDescription() + "，" + m.getVolumeAnalysis()
                + "，" + (m.getCandlePattern() != null ? m.getCandlePattern() : "");
    }
}
