package com.quantai.service.agent.impl;

import com.quantai.service.DataServiceClient;
import com.quantai.service.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 情绪面分析 Agent（维度型）
 * 职责：自包含新闻数据获取+舆情分析，评估市场情绪和资金动向
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentAnalysisAgent implements Agent {

    private final OpenAiChatModel chatModel;
    private final DataServiceClient dataServiceClient;

    @Override
    public String getName() {
        return "SentimentAnalysis";
    }

    @Override
    public String getRole() {
        return "情绪面分析师";
    }

    @Override
    public String getGoal() {
        return "分析市场情绪、舆论导向、资金流向，给出情绪面评级和风险提示";
    }

    @Override
    public List<String> getToolNames() {
        return List.of("DataServiceClient.fetchNews", "DataServiceClient.fetchFundFlow");
    }

    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String stockCode = context.getStockCode();
        log.info("[{}] 开始执行 - 股票代码: {}", getName(), stockCode);

        try {
            // 1. 获取新闻数据
            List<Map<String, Object>> newsData = dataServiceClient.fetchNews(stockCode);

            // 2. 获取资金流向数据
            List<Map<String, Object>> fundFlowData = dataServiceClient.fetchFundFlow(stockCode);

            // 3. 检查数据可用性
            if ((newsData == null || newsData.isEmpty()) && (fundFlowData == null || fundFlowData.isEmpty())) {
                log.warn("[{}] 新闻和资金流数据均缺失: {}", getName(), stockCode);
                return AgentResult.failure("无法获取市场情绪相关数据", System.currentTimeMillis() - start);
            }

            // 4. 构建数据摘要
            String dataSummary = buildDataSummary(newsData, fundFlowData);

            // 5. 调用LLM分析
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(stockCode, dataSummary, context.getUserQuestion());

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
            log.info("[{}] 执行完成 - 耗时: {}ms, Token: {}", getName(), duration, tokenUsed);

            return AgentResult.success(output, duration, tokenUsed, 1);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[{}] 执行失败", getName(), e);
            return AgentResult.failure("情绪面分析失败: " + e.getMessage(), duration);
        }
    }

    /**
     * 构建数据摘要
     */
    private String buildDataSummary(List<Map<String, Object>> newsData, List<Map<String, Object>> fundFlowData) {
        StringBuilder sb = new StringBuilder();

        // 新闻舆情摘要
        if (newsData != null && !newsData.isEmpty()) {
            sb.append("## 新闻舆情（最近10条）\n");
            int count = Math.min(10, newsData.size());
            for (int i = 0; i < count; i++) {
                Map<String, Object> news = newsData.get(i);
                sb.append(String.format("### %s\n", news.get("publish_time")));
                sb.append(String.format("- 标题：%s\n", news.get("title")));
                sb.append(String.format("- 来源：%s\n", news.get("source")));
                if (news.get("summary") != null) {
                    sb.append(String.format("- 摘要：%s\n", news.get("summary")));
                }
                sb.append("\n");
            }
        } else {
            sb.append("## 新闻舆情\n暂无最近新闻数据\n\n");
        }

        // 资金流向摘要
        if (fundFlowData != null && !fundFlowData.isEmpty()) {
            sb.append("## 资金流向（最近5个交易日）\n");
            sb.append("| 日期 | 主力净流入(万) | 超大单(万) | 大单(万) | 中单(万) | 小单(万) |\n");
            sb.append("|------|---------------|-----------|---------|---------|--------|\n");

            int count = Math.min(5, fundFlowData.size());
            for (int i = 0; i < count; i++) {
                Map<String, Object> flow = fundFlowData.get(i);
                sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                        flow.get("date"),
                        formatNumber(flow.get("main_net_inflow")),
                        formatNumber(flow.get("huge_order_net_inflow")),
                        formatNumber(flow.get("large_order_net_inflow")),
                        formatNumber(flow.get("medium_order_net_inflow")),
                        formatNumber(flow.get("small_order_net_inflow"))));
            }
            sb.append("\n");
        } else {
            sb.append("## 资金流向\n暂无资金流向数据\n\n");
        }

        return sb.toString();
    }

    private String formatNumber(Object value) {
        if (value == null) return "-";
        try {
            double num = Double.parseDouble(value.toString());
            return String.format("%.0f", num / 10000.0); // 转换为万元
        } catch (Exception e) {
            return value.toString();
        }
    }

    private String buildSystemPrompt() {
        return """
                你是一名资深市场情绪分析师，擅长舆情分析和资金流向研判。

                你的职责：
                1. 分析新闻舆情的倾向性（利好/利空/中性）
                2. 分析资金流向（主力是流入还是流出）
                3. 评估市场情绪（乐观/悲观/谨慎）
                4. 识别潜在风险和机会
                5. 给出明确的情绪面评级和操作建议

                分析维度：
                - **舆情倾向**：新闻报道是正面、负面还是中性
                - **资金动向**：主力资金是流入还是流出，是否持续
                - **市场情绪**：投资者情绪是乐观还是悲观
                - **行业热度**：所属行业是否受到市场关注

                输出要求（严格JSON格式）：
                {
                  "dimension": "SENTIMENT",
                  "score": 0-100分（情绪面综合评分）,
                  "suggestion": "BUY|SELL|HOLD",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "核心理由（50字以内）",
                  "assumptions": [
                    "关键假设1（如：假设主力资金流入趋势持续3-5个交易日）",
                    "关键假设2（如：假设利好舆情影响持续，无重大利空消息）",
                    "关键假设3（如：假设市场情绪保持乐观，不出现恐慌性抛售）",
                    "关键假设4（如：假设行业热度延续，板块效应持续）"
                  ],
                  "analysis": "详细分析报告（markdown格式，300字左右）"
                }

                suggestion判断标准：
                - BUY：舆情积极 + 主力资金持续流入 + 市场情绪乐观
                - SELL：负面新闻较多 + 主力资金持续流出 + 市场情绪悲观
                - HOLD：舆情平淡或资金流向不明确

                注意：
                - 舆情分析容易受短期事件影响，给出风险提示
                - 资金流向数据可能有滞后性
                - 如果数据不足，明确说明并降低置信度

                **置信度评估标准（数据质量驱动）**：
                - HIGH：新闻数据丰富（至少5条近期新闻）+ 资金流向数据完整（至少5日）+ 龙虎榜数据完整
                - MEDIUM：新闻数据2-4条 OR 资金流向数据不足5日 OR 龙虎榜数据缺失
                - LOW：新闻数据少于2条 OR 资金流向数据缺失 OR 所有情绪数据严重不足
                """;
    }

    private String buildUserPrompt(String stockCode, String dataSummary, String userQuestion) {
        return String.format("""
                请对股票【%s】进行情绪面分析。

                用户问题：%s

                %s

                请基于以上数据，从以下维度分析：

                1. **舆情分析**
                   - 最近新闻的整体倾向是什么？
                   - 是否有重大利好或利空消息？

                2. **资金分析**
                   - 主力资金是流入还是流出？
                   - 资金流向趋势是否持续？

                3. **情绪判断**
                   - 市场对该股票的整体情绪如何？
                   - 是否存在过度乐观或恐慌？

                4. **风险机会**
                   - 是否有潜在的催化剂事件？
                   - 是否有潜在的风险因素？

                5. **情绪面建议**
                   - 情绪面评分（0-100）
                   - 建议操作（BUY/SELL/HOLD）
                   - 置信度（HIGH/MEDIUM/LOW）
                   - 核心理由（50字以内）
                   - **关键假设（必须输出3-4条）**：
                     * 资金流向假设（如：假设主力资金流入趋势持续3-5日）
                     * 舆情持续性假设（如：假设利好舆情影响持续，无重大利空）
                     * 市场情绪假设（如：假设市场情绪保持乐观，不出现恐慌抛售）
                     * 板块效应假设（如：假设行业热度延续，板块联动持续）

                请严格按JSON格式输出，不要包含其他文字。
                """,
                stockCode,
                userQuestion != null ? userQuestion : "分析该股票的市场情绪",
                dataSummary);
    }
}
