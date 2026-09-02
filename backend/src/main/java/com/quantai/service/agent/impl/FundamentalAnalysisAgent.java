package com.quantai.service.agent.impl;

import com.quantai.service.DataServiceClient;
import com.quantai.service.StockService;
import com.quantai.service.IndustryComparisonService;
import com.quantai.model.entity.StockQuote;
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
import java.util.List;
import java.util.Map;

/**
 * 基本面分析 Agent（维度型）
 * 职责：自包含数据获取+分析，评估企业财务健康度、盈利能力、估值水平
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundamentalAnalysisAgent implements Agent {

    private final OpenAiChatModel chatModel;
    private final DataServiceClient dataServiceClient;
    private final StockService stockService;
    private final IndustryComparisonService industryComparisonService;

    @Override
    public String getName() {
        return "FundamentalAnalysis";
    }

    @Override
    public String getRole() {
        return "基本面分析师";
    }

    @Override
    public String getGoal() {
        return "分析企业财务健康度、盈利能力、估值水平，给出基本面评级和投资建议";
    }

    @Override
    public List<String> getToolNames() {
        return List.of("DataServiceClient.fetchFinance", "StockService.getQuote");
    }

    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String stockCode = context.getStockCode();
        log.info("[{}] 开始执行 - 股票代码: {}", getName(), stockCode);

        try {
            // 1. 获取实时行情数据（估值指标）
            StockQuote quote = stockService.getQuote(stockCode);
            if (quote == null) {
                log.warn("[{}] 获取行情数据失败: {}", getName(), stockCode);
                return AgentResult.failure("无法获取股票行情数据", System.currentTimeMillis() - start);
            }

            // 2. 获取财务数据
            List<Map<String, Object>> financeData = dataServiceClient.fetchFinance(stockCode);
            if (financeData == null || financeData.isEmpty()) {
                log.warn("[] 获取财务数据失败: {}", getName(), stockCode);
                return AgentResult.failure("无法获取财务数据", System.currentTimeMillis() - start);
            }

            // 3. 获取行业对比数据
            IndustryComparisonService.IndustryComparison industryComparison = null;
            if (quote.getIndustry() != null && !quote.getIndustry().isEmpty()) {
                industryComparison = industryComparisonService.getIndustryComparison(stockCode, quote.getIndustry());
            }

            // 4. 获取历史估值分位数
            IndustryComparisonService.ValuationPercentile percentile = null;
            if (quote.getPeRatio() != null || quote.getPbRatio() != null) {
                percentile = industryComparisonService.getHistoricalPercentile(
                    stockCode, quote.getPeRatio(), quote.getPbRatio());
            }

            // 5. 构建数据摘要（包含行业对比和历史分位数）
            String dataSummary = buildDataSummary(quote, financeData, industryComparison, percentile);

            // 6. 调用LLM分析
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(stockCode, quote.getName(), dataSummary, context.getUserQuestion());

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
            return AgentResult.failure("基本面分析失败: " + e.getMessage(), duration);
        }
    }

    /**
     * 构建数据摘要（提取关键指标）
     */
    private String buildDataSummary(StockQuote quote, List<Map<String, Object>> financeData,
                                     IndustryComparisonService.IndustryComparison industryComparison,
                                     IndustryComparisonService.ValuationPercentile percentile) {
        StringBuilder sb = new StringBuilder();

        // 实时行情摘要
        sb.append("## 实时行情\n");
        sb.append(String.format("- 股票名称：%s (%s)\n", quote.getName(), quote.getCode()));
        sb.append(String.format("- 当前价格：%.2f元\n", quote.getCurrentPrice()));
        sb.append(String.format("- 涨跌幅：%.2f%%\n", quote.getChangePercent()));
        if (quote.getPeRatio() != null && quote.getPeRatio().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("- 市盈率(PE)：%.2f\n", quote.getPeRatio()));
        }
        if (quote.getIndustry() != null) {
            sb.append(String.format("- 所属行业：%s\n", quote.getIndustry()));
        }
        sb.append("\n");

        // 行业对比数据
        if (industryComparison != null) {
            sb.append("## 行业对比（").append(industryComparison.getIndustry()).append("）\n");
            sb.append(String.format("- 数据日期：%s\n", industryComparison.getTradeDate()));
            sb.append(String.format("- 样本股票数：%d只\n\n", industryComparison.getStockCount()));

            sb.append("### PE估值对比\n");
            if (industryComparison.getPeMedian() != null) {
                sb.append(String.format("- 行业PE中位数：%.2f\n", industryComparison.getPeMedian()));
                sb.append(String.format("- 行业PE平均值：%.2f\n", industryComparison.getPeMean()));
                sb.append(String.format("- 行业PE四分位区间：[%.2f, %.2f]\n",
                    industryComparison.getPeP25(), industryComparison.getPeP75()));

                if (quote.getPeRatio() != null && quote.getPeRatio().compareTo(BigDecimal.ZERO) > 0) {
                    double peDeviation = (quote.getPeRatio().doubleValue() - industryComparison.getPeMedian().doubleValue())
                        / industryComparison.getPeMedian().doubleValue() * 100;
                    sb.append(String.format("- **本股PE相对行业：%.1f%%（%s）**\n",
                        Math.abs(peDeviation),
                        peDeviation > 0 ? "高于中位数" : "低于中位数"));
                }
            }
            sb.append("\n");

            sb.append("### ROE盈利能力对比\n");
            if (industryComparison.getRoeMedian() != null) {
                sb.append(String.format("- 行业ROE中位数：%.2f%%\n", industryComparison.getRoeMedian()));
                sb.append(String.format("- 行业ROE平均值：%.2f%%\n", industryComparison.getRoeMean()));
                sb.append(String.format("- 行业优秀水平(P75)：%.2f%%\n", industryComparison.getRoeP75()));
            }
            sb.append("\n");

            sb.append("### 成长性对比\n");
            if (industryComparison.getRevenueYoyMedian() != null) {
                sb.append(String.format("- 行业营收增速中位数：%.2f%%\n", industryComparison.getRevenueYoyMedian()));
            }
            if (industryComparison.getProfitYoyMedian() != null) {
                sb.append(String.format("- 行业净利润增速中位数：%.2f%%\n", industryComparison.getProfitYoyMedian()));
            }
            sb.append("\n");
        }

        // 历史估值分位数
        if (percentile != null) {
            sb.append("## 历史估值分位数（近3年）\n");

            if (percentile.getPePercentile() != null) {
                sb.append("### PE历史分位数\n");
                sb.append(String.format("- 历史区间：[%.2f, %.2f]\n", percentile.getPeMin(), percentile.getPeMax()));
                sb.append(String.format("- 历史中位数：%.2f\n", percentile.getPeP50()));
                sb.append(String.format("- **当前PE处于历史 %d%% 分位**\n", percentile.getPePercentile()));

                String peLevel;
                if (percentile.getPePercentile() >= 80) {
                    peLevel = "历史高位（前20%）";
                } else if (percentile.getPePercentile() >= 60) {
                    peLevel = "历史偏高（60-80%）";
                } else if (percentile.getPePercentile() >= 40) {
                    peLevel = "历史中等（40-60%）";
                } else if (percentile.getPePercentile() >= 20) {
                    peLevel = "历史偏低（20-40%）";
                } else {
                    peLevel = "历史低位（后20%）";
                }
                sb.append(String.format("- 分位判断：%s\n", peLevel));
            }

            if (percentile.getPbPercentile() != null) {
                sb.append("\n### PB历史分位数\n");
                sb.append(String.format("- 历史中位数：%.2f\n", percentile.getPbP50()));
                sb.append(String.format("- **当前PB处于历史 %d%% 分位**\n", percentile.getPbPercentile()));
            }
            sb.append("\n");
        }

        // 财务数据摘要（最近4个季度）
        sb.append("## 财务数据（最近4个报告期）\n");
        int count = Math.min(4, financeData.size());
        for (int i = 0; i < count; i++) {
            Map<String, Object> item = financeData.get(i);
            sb.append(String.format("### %s\n", item.get("report_date")));

            // 营收和利润
            appendIfExists(sb, "营业总收入", item.get("total_revenue"), "亿元");
            appendIfExists(sb, "净利润", item.get("net_profit"), "亿元");
            appendIfExists(sb, "营收同比增长", item.get("revenue_yoy"), "%");
            appendIfExists(sb, "净利润同比增长", item.get("profit_yoy"), "%");

            // 盈利能力
            appendIfExists(sb, "净资产收益率(ROE)", item.get("roe"), "%");
            appendIfExists(sb, "毛利率", item.get("gross_margin"), "%");
            appendIfExists(sb, "净利率", item.get("net_margin"), "%");

            // 财务安全
            appendIfExists(sb, "资产负债率", item.get("debt_ratio"), "%");
            appendIfExists(sb, "流动比率", item.get("current_ratio"), "");
            appendIfExists(sb, "速动比率", item.get("quick_ratio"), "");

            sb.append("\n");
        }

        return sb.toString();
    }

    private void appendIfExists(StringBuilder sb, String label, Object value, String unit) {
        if (value != null) {
            sb.append(String.format("- %s：%s%s\n", label, value, unit));
        }
    }

    private String buildSystemPrompt() {
        return """
                你是一名资深基本面分析师，擅长企业价值评估和投资分析。

                你的职责：
                1. 分析企业财务健康度（盈利能力、成长性、财务安全）
                2. 评估估值水平（PE、PB是否合理）
                3. **重点：结合行业对比和历史分位数进行相对估值分析**
                4. 识别投资机会和风险
                5. 给出明确的基本面评级和投资建议

                分析维度：
                - **盈利能力**：ROE、净利率、毛利率是否优秀，相对行业中位数的位置
                - **成长性**：营收和净利润同比增速是否强劲，是否高于行业水平
                - **财务安全**：负债率、流动比率、现金流是否健康
                - **绝对估值**：PE、PB数值本身的高低
                - **相对估值（核心）**：
                  * PE相对行业中位数：高多少或低多少？
                  * PE历史分位数：当前处于历史什么位置（高位/中位/低位）？
                  * ROE溢价：如果ROE远高于行业，PE可以适当溢价
                  * 综合判断：虽然PE绝对值高，但如果ROE优秀且处于历史中等分位，估值可能合理

                估值分析逻辑（关键）：
                1. 如果PE绝对值高但ROE显著高于行业（如高50%以上），可判断"ROE溢价合理"
                2. 如果PE处于历史低分位（<40%），即使绝对值不低，也属于"相对低估"
                3. 如果PE低于行业中位数，且ROE高于行业，属于"明显低估"
                4. 如果PE高于行业且处于历史高分位（>70%），需警惕"估值过高"

                输出要求（严格JSON格式）：
                {
                  "dimension": "FUNDAMENTAL",
                  "score": 0-100分（基本面综合评分）,
                  "suggestion": "BUY|SELL|HOLD",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "核心理由（50字以内，必须提及行业对比或历史分位）",
                  "assumptions": [
                    "关键假设1（如：假设ROE维持在当前水平30%以上）",
                    "关键假设2（如：假设行业景气度持续，营收增速保持15%+）",
                    "关键假设3（如：假设PE不突破历史80%分位）",
                    "关键假设4（如：假设财务杠杆保持稳健，负债率不超过60%）"
                  ],
                  "analysis": "详细分析报告（markdown格式，300字左右，必须包含：1.绝对估值 2.行业对比 3.历史分位数 4.综合判断）"
                }

                suggestion判断标准（结合相对估值）：
                - BUY：盈利能力强 + 成长性好 + (PE低于行业 OR PE历史低分位 OR 高ROE支撑高PE)
                - SELL：盈利下滑 + 财务风险高 + (PE高于行业 AND PE历史高分位)
                - HOLD：基本面平稳或信号矛盾

                注意：
                - **必须基于行业对比和历史分位数进行相对估值分析**
                - 如果数据缺失，明确说明并降低置信度
                - 避免绝对化表述，给出风险提示

                **置信度评估标准（数据质量驱动）**：
                - HIGH：财务数据完整（至少4个季度）+ 行业对比数据完整 + 历史分位数完整
                - MEDIUM：财务数据完整但缺少行业对比或历史分位数 OR 财务数据只有2-3个季度
                - LOW：财务数据少于2个季度 OR 关键指标（ROE/净利率/营收增速）大量缺失
                """;
    }

    private String buildUserPrompt(String stockCode, String stockName, String dataSummary, String userQuestion) {
        return String.format("""
                请对股票【%s(%s)】进行基本面分析。

                用户问题：%s

                %s

                请基于以上数据，从以下维度分析：

                1. **盈利能力评估**
                   - ROE、净利率、毛利率水平如何？
                   - 与行业中位数相比处于什么位置？是否属于行业龙头水平？

                2. **成长性评估**
                   - 营收和净利润增速如何？
                   - 相比行业增速中位数是否更强？成长是否可持续？

                3. **财务安全评估**
                   - 负债率是否合理？
                   - 现金流是否健康？

                4. **绝对估值评估**
                   - PE、PB数值本身是高是低？

                5. **相对估值评估（重点）**
                   - PE相对行业中位数：偏高还是偏低？偏离幅度多大？
                   - PE历史分位数：当前处于历史高位、中位还是低位？
                   - ROE溢价分析：如果PE高，ROE是否足够优秀来支撑估值溢价？
                   - 综合判断：结合行业对比和历史分位数，估值是合理、高估还是低估？

                6. **投资建议**
                   - 基本面评分（0-100）
                   - 建议操作（BUY/SELL/HOLD）
                   - 置信度（HIGH/MEDIUM/LOW）
                   - 核心理由（必须提及行业对比或历史分位数）
                   - **关键假设（必须输出3-4条）**：
                     * 盈利能力假设（如：假设ROE维持在30%以上）
                     * 成长性假设（如：假设营收增速保持15%+）
                     * 估值假设（如：假设PE不突破历史80%分位）
                     * 财务安全假设（如：假设负债率保持在60%以下）

                重要提示：
                - 必须结合"行业对比"和"历史分位数"进行相对估值分析
                - 例如："虽然PE=40偏高，但ROE=32%%远超行业中位数18%%，且PE处于历史60%%分位（非极端高位），估值溢价合理"
                - 不要只看绝对数值，要看相对位置
                - **必须在assumptions数组中明确列出3-4条关键假设**

                请严格按JSON格式输出，不要包含其他文字。
                """,
                stockName, stockCode,
                userQuestion != null ? userQuestion : "分析该股票的投资价值",
                dataSummary);
    }
}
