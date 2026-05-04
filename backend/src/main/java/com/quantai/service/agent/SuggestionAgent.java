package com.quantai.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.model.vo.MarketAnalysis;
import com.quantai.model.vo.TradeSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

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

    private static final String SYSTEM_PROMPT = """
            你是一位专业的中国A股市场分析师，正在参与一个多Agent协作分析流程。

            你的输入包含两部分：
            1. 【新闻舆情分析】— 最新相关新闻的情绪倾向
            2. 【市场技术面分析】— 基于实时K线数据的趋势、均线、量能分析

            请综合两部分信息，给出对该股票的交易参考建议。

            要求：
            - 分析理由不超过300字，简洁专业
            - 区分短期（1-5天）和中长期（1-3个月）视角
            - 明确指出不确定性
            - 不承诺收益

            请严格按以下JSON格式返回（不要包含其他文字）：
            {
              "action": "BUY|SELL|HOLD",
              "confidence": "HIGH|MEDIUM|LOW",
              "reason": "综合新闻情绪与市场数据的分析理由（markdown格式）",
              "riskWarning": "风险提示",
              "suggestionSummary": "一句话总结操作建议"
            }

            action取值说明：
            - BUY：新闻偏利好 + 技术面偏强 → 可关注买入
            - SELL：新闻偏利空 + 技术面偏弱 → 注意风险
            - HOLD：信号矛盾或方向不明 → 观望
            """;

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
            log.info("交易建议Agent完成 code={} action={} 耗时={}ms",
                    stockCode, suggestion.getAction(), System.currentTimeMillis() - start);
            return suggestion;
        } catch (Exception e) {
            log.error("交易建议Agent调用失败 code={}", stockCode, e);
            return fallbackSuggestion(marketAnalysis);
        }
    }

    private String callLlm(String userMessage) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
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
