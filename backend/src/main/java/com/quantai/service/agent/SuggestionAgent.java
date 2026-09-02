package com.quantai.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.config.PromptsConfig;
import com.quantai.model.vo.MarketAnalysis;
import com.quantai.model.vo.TradeSuggestion;
import com.quantai.service.AgentAdviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 交易建议Agent — 调用LLM综合新闻舆情 + 市场分析，生成买卖建议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionAgent {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final PromptsConfig promptsConfig;
    private final AgentAdviceService agentAdviceService;

    public TradeSuggestion suggest(String stockCode, String stockName,
                                    String newsAnalysis, MarketAnalysis marketAnalysis) {
        long start = System.currentTimeMillis();

        StringBuilder userInput = new StringBuilder();
        userInput.append("股票：").append(stockName).append("(").append(stockCode).append(")\n\n");

        userInput.append("【新闻舆情分析】\n");
        userInput.append(newsAnalysis != null && !newsAnalysis.isEmpty() ? newsAnalysis : "无相关新闻数据\n");

        userInput.append("\n【市场技术面分析】\n");
        userInput.append("当前价格：").append(marketAnalysis.getCurrentPrice()).append("\n");
        userInput.append("趋势判断：").append(marketAnalysis.getTrendDescription()).append("\n");
        userInput.append("均线状态：").append(marketAnalysis.getMaStatus()).append("\n");
        if (marketAnalysis.getMa5() != null) userInput.append("MA5：").append(marketAnalysis.getMa5()).append("\n");
        if (marketAnalysis.getMa10() != null) userInput.append("MA10：").append(marketAnalysis.getMa10()).append("\n");
        if (marketAnalysis.getMa20() != null) userInput.append("MA20：").append(marketAnalysis.getMa20()).append("\n");
        userInput.append("量能分析：").append(marketAnalysis.getVolumeAnalysis()).append("\n");
        userInput.append("K线形态：").append(marketAnalysis.getCandlePattern()).append("\n");
        if (marketAnalysis.getChangePercent5() != null) {
            userInput.append("近5日涨跌幅：").append(String.format("%.2f%%", marketAnalysis.getChangePercent5())).append("\n");
        }
        if (marketAnalysis.getConsecutiveDirection() != null && marketAnalysis.getConsecutiveDirection() != 0) {
            String dir = marketAnalysis.getConsecutiveDirection() > 0 ? "连涨" : "连跌";
            userInput.append("连续").append(dir).append(Math.abs(marketAnalysis.getConsecutiveDirection())).append("日\n");
        }

        try {
            String response = callLlm(userInput.toString());
            log.debug("LLM原始响应: {}", response);
            TradeSuggestion suggestion = parseResponse(response);
            suggestion = validateSuggestion(suggestion);

            // 保存建议并启用风险监控
            try {
                Long adviceId = agentAdviceService.saveAdvice(
                    stockCode,
                    stockName,
                    suggestion,
                    marketAnalysis,
                    response
                );
                if (adviceId != null) {
                    log.info("已保存Agent建议并启用风险监控: adviceId={}", adviceId);
                }
            } catch (Exception saveEx) {
                log.error("保存Agent建议失败（不影响返回结果）: stock={}", stockCode, saveEx);
            }

            long duration = System.currentTimeMillis() - start;
            log.info("交易建议Agent完成 code={} action={} 耗时={}ms",
                    stockCode, suggestion.getAction(), duration);
            return suggestion;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("交易建议Agent调用失败 code={} 耗时={}ms", stockCode, duration, e);
            return fallbackSuggestion(marketAnalysis);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(已截断)";
    }

    private String callLlm(String userMessage) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(promptsConfig.getSystem().getSuggestionAgent()),
                new UserMessage(userMessage)
        ));
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getContent();
    }

    private TradeSuggestion parseResponse(String content) {
        // 提取JSON部分
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            content = content.substring(start, end + 1);
        }

        try {
            Map<String, Object> map = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});

            TradeSuggestion.TradeSuggestionBuilder builder = TradeSuggestion.builder();

            String action = valueAsString(map.get("action"));
            if (action == null) action = "HOLD";
            builder.action(action);
            builder.actionLabel(switch (action) {
                case "BUY" -> "买入";
                case "SELL" -> "卖出";
                default -> "观望";
            });

            builder.confidence(valueAsString(map.get("confidence")));
            builder.reason(valueAsString(map.get("reason")));
            builder.riskWarning(valueAsString(map.get("riskWarning")));
            builder.suggestionSummary(valueAsString(map.get("suggestionSummary")));

            return builder.build();
        } catch (Exception e) {
            log.warn("解析LLM响应失败，原始内容: {}", content, e);
            return TradeSuggestion.builder()
                    .action("HOLD").actionLabel("观望").confidence("LOW")
                    .reason("分析结果解析失败")
                    .suggestionSummary("分析异常")
                    .build();
        }
    }

    private String valueAsString(Object val) {
        if (val == null) return null;
        if (val instanceof String s) return s;
        return val.toString();
    }

    /**
     * 校验Agent输出，防止LLM乱回答
     */
    private TradeSuggestion validateSuggestion(TradeSuggestion s) {
        Set<String> validActions = Set.of("BUY", "SELL", "HOLD");
        Set<String> validConfidences = Set.of("HIGH", "MEDIUM", "LOW");

        if (s.getAction() == null || !validActions.contains(s.getAction())) {
            log.warn("LLM返回了无效的action: {}，已重置为HOLD", s.getAction());
            s.setAction("HOLD");
            s.setActionLabel("观望");
        }
        if (s.getConfidence() == null || !validConfidences.contains(s.getConfidence())) {
            log.warn("LLM返回了无效的confidence: {}，已重置为LOW", s.getConfidence());
            s.setConfidence("LOW");
        }
        return s;
    }

    /**
     * LLM挂了时的兜底方案：用市场数据做简单规则判断
     * 不调用LLM，纯if-else逻辑
     */
    private TradeSuggestion fallbackSuggestion(MarketAnalysis market) {
        String action = "HOLD";
        String label = "观望";
        String reason = "AI分析不可用，基于市场数据做简单判断";

        if (market.getTrend() != null && market.getCurrentPrice() != null) {
            if ("UP_TREND".equals(market.getTrend())) {
                boolean aboveMa5 = market.getMa5() != null
                        && market.getCurrentPrice().compareTo(market.getMa5()) > 0;
                if (aboveMa5) {
                    action = "BUY";
                    label = "买入";
                    reason = "短期趋势向上且价格在MA5之上，但未经过AI深度分析，仅供参考";
                } else {
                    reason = "短期趋势向上但价格在MA5之下，可能是回调，观望为宜";
                }
            } else if ("DOWN_TREND".equals(market.getTrend())) {
                action = "SELL";
                label = "卖出";
                reason = "短期趋势向下，建议注意风险（AI不可用时的规则兜底）";
            }
        }

        return TradeSuggestion.builder()
                .action(action)
                .actionLabel(label)
                .confidence("LOW")
                .reason(reason)
                .riskWarning("AI分析服务暂不可用，此建议仅基于简单规则，可靠性较低")
                .suggestionSummary(action.equals("HOLD") ? "暂建议观望" : label + "建议")
                .build();
    }
}
