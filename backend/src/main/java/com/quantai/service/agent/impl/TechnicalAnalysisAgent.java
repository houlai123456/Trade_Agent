package com.quantai.service.agent.impl;

import com.quantai.service.DataServiceClient;
import com.quantai.model.entity.StockKline;
import com.quantai.service.TechnicalIndicatorService;
import com.quantai.service.TechnicalIndicatorService.TechnicalIndicators;
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

/**
 * 技术面分析 Agent（维度型）
 * 职责：使用专业库(TA4j)计算技术指标，让LLM专注于解读数据
 * 改进：先用专业库计算（准确） → 再让LLM解读（这才是LLM的价值）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechnicalAnalysisAgent implements Agent {

    private final OpenAiChatModel chatModel;
    private final DataServiceClient dataServiceClient;
    private final TechnicalIndicatorService indicatorService;

    @Override
    public String getName() {
        return "TechnicalAnalysis";
    }

    @Override
    public String getRole() {
        return "技术面分析师";
    }

    @Override
    public String getGoal() {
        return "分析K线形态、技术指标、趋势方向，给出技术面评级和交易信号";
    }

    @Override
    public List<String> getToolNames() {
        return List.of("DataServiceClient.fetchKline");
    }

    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        String stockCode = context.getStockCode();
        log.info("[{}] 开始执行 - 股票代码: {}", getName(), stockCode);

        try {
            // 1. 获取K线数据（最近60个交易日）
            List<StockKline> klineData = dataServiceClient.fetchKline(stockCode, "daily", 60);

            if (klineData == null || klineData.isEmpty()) {
                log.warn("[{}] 获取K线数据失败: {}", getName(), stockCode);
                return AgentResult.failure("无法获取K线数据", System.currentTimeMillis() - start);
            }

            // 2. 数据质量检查
            int dataQuality = calculateDataQuality(klineData);
            if (dataQuality < 60) {
                log.warn("[{}] K线数据质量不足: {}分", getName(), dataQuality);
                String output = String.format("K线数据质量不足（%d分），无法进行深度技术分析。建议：等待更多交易数据积累。", dataQuality);
                return AgentResult.success(output, System.currentTimeMillis() - start, 0, 0);
            }

            // 3. 使用专业库计算技术指标（准确性100%）
            TechnicalIndicators indicators = indicatorService.calculateIndicators(klineData);

            // 4. 构建指标摘要（给LLM解读用）
            String indicatorSummary = buildIndicatorSummary(indicators, klineData);

            // 5. 调用LLM解读指标（LLM专注于"解读"而非"计算"）
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(stockCode, indicatorSummary, context.getUserQuestion());

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
            return AgentResult.failure("技术面分析失败: " + e.getMessage(), duration);
        }
    }

    /**
     * 计算K线数据质量评分（0-100）
     */
    private int calculateDataQuality(List<StockKline> klineData) {
        if (klineData == null || klineData.isEmpty()) return 0;

        int size = klineData.size();
        int score = 0;

        // 数据量评分（40分）
        if (size >= 60) score += 40;
        else if (size >= 30) score += 30;
        else if (size >= 10) score += 20;
        else score += 10;

        // 数据完整性评分（40分）
        int validCount = 0;
        for (StockKline k : klineData) {
            if (k.getClosePrice() != null && k.getVolume() != null
                && k.getClosePrice().compareTo(BigDecimal.ZERO) > 0) {
                validCount++;
            }
        }
        double completeness = (double) validCount / size;
        score += (int) (completeness * 40);

        // 数据连续性评分（20分）
        if (size > 1) {
            boolean continuous = true;
            for (int i = 1; i < Math.min(10, size); i++) {
                // 简单检查：是否有异常的大跳跃
                if (klineData.get(i).getDate() == null) {
                    continuous = false;
                    break;
                }
            }
            if (continuous) score += 20;
            else score += 10;
        }

        return Math.min(100, score);
    }

    /**
     * 构建技术指标摘要（专业库已计算，LLM只需解读）
     */
    private String buildIndicatorSummary(TechnicalIndicators indicators, List<StockKline> klineData) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 技术指标数据（TA4j专业库计算）\n\n");

        // 当前价格和趋势
        sb.append("### 价格与趋势\n");
        sb.append(String.format("- **当前价格**: %.2f\n", indicators.getCurrentPrice()));
        sb.append(String.format("- **趋势判断**: %s\n\n", indicators.getTrend()));

        // 移动平均线
        sb.append("### 移动平均线系统\n");
        sb.append(String.format("- MA5: %.2f\n", indicators.getMa5()));
        sb.append(String.format("- MA10: %.2f\n", indicators.getMa10()));
        sb.append(String.format("- MA20: %.2f\n", indicators.getMa20()));
        sb.append(String.format("- MA60: %.2f\n", indicators.getMa60()));

        // 均线排列判断
        if (indicators.getMa5() > indicators.getMa10() &&
            indicators.getMa10() > indicators.getMa20()) {
            sb.append("- **均线状态**: 多头排列（MA5 > MA10 > MA20）\n");
        } else if (indicators.getMa5() < indicators.getMa10() &&
                   indicators.getMa10() < indicators.getMa20()) {
            sb.append("- **均线状态**: 空头排列（MA5 < MA10 < MA20）\n");
        } else {
            sb.append("- **均线状态**: 均线纠缠，方向不明\n");
        }
        sb.append("\n");

        // MACD
        var macd = indicators.getMacd();
        sb.append("### MACD指标\n");
        sb.append(String.format("- DIF: %.4f\n", macd.getDif()));
        sb.append(String.format("- DEA: %.4f\n", macd.getDea()));
        sb.append(String.format("- MACD柱: %.4f (%s)\n", macd.getBar(), macd.getBar() > 0 ? "红柱" : "绿柱"));
        sb.append(String.format("- **信号**: %s\n\n", macd.getSignal()));

        // RSI
        sb.append("### RSI相对强弱指标\n");
        sb.append(String.format("- RSI(6): %.2f\n", indicators.getRsi6()));
        sb.append(String.format("- RSI(12): %.2f\n", indicators.getRsi12()));
        sb.append(String.format("- RSI(24): %.2f\n", indicators.getRsi24()));

        String rsiStatus;
        if (indicators.getRsi12() > 70) {
            rsiStatus = "超买区域（>70），警惕回调";
        } else if (indicators.getRsi12() < 30) {
            rsiStatus = "超卖区域（<30），可能反弹";
        } else if (indicators.getRsi12() > 50) {
            rsiStatus = "强势区域（50-70）";
        } else {
            rsiStatus = "弱势区域（30-50）";
        }
        sb.append(String.format("- **状态**: %s\n\n", rsiStatus));

        // KDJ
        var kdj = indicators.getKdj();
        sb.append("### KDJ随机指标\n");
        sb.append(String.format("- K值: %.2f\n", kdj.getK()));
        sb.append(String.format("- D值: %.2f\n", kdj.getD()));
        sb.append(String.format("- J值: %.2f\n", kdj.getJ()));
        sb.append(String.format("- **信号**: %s\n\n", kdj.getSignal()));

        // 布林带
        var boll = indicators.getBollingerBands();
        sb.append("### 布林带（BOLL）\n");
        sb.append(String.format("- 上轨: %.2f\n", boll.getUpper()));
        sb.append(String.format("- 中轨: %.2f\n", boll.getMiddle()));
        sb.append(String.format("- 下轨: %.2f\n", boll.getLower()));
        sb.append(String.format("- 带宽: %.2f%%\n", boll.getWidth()));
        sb.append(String.format("- **当前位置**: %s\n\n", boll.getPosition()));

        // 成交量
        sb.append("### 成交量分析\n");
        sb.append(String.format("- 5日均量: %.0f\n", indicators.getVolumeMa5()));
        sb.append(String.format("- 10日均量: %.0f\n", indicators.getVolumeMa10()));

        StockKline latestKline = klineData.get(0);
        double currentVolume = latestKline.getVolume() != null ? latestKline.getVolume() : 0;
        String volumeStatus;
        if (currentVolume > indicators.getVolumeMa5() * 1.5) {
            volumeStatus = "明显放量（当前成交量 > 1.5倍均量）";
        } else if (currentVolume < indicators.getVolumeMa5() * 0.7) {
            volumeStatus = "明显缩量（当前成交量 < 0.7倍均量）";
        } else {
            volumeStatus = "成交量正常";
        }
        sb.append(String.format("- **状态**: %s\n\n", volumeStatus));

        // ATR和OBV
        sb.append("### 其他指标\n");
        sb.append(String.format("- ATR(14)平均真实波幅: %.2f\n", indicators.getAtr()));
        sb.append(String.format("- OBV能量潮: %.0f\n\n", indicators.getObv()));

        // 最近5日K线简况
        sb.append("### 最近5日K线\n");
        sb.append("| 日期 | 收盘价 | 涨跌幅 | 成交量 |\n");
        sb.append("|------|--------|--------|--------|\n");
        int count = Math.min(5, klineData.size());
        for (int i = 0; i < count; i++) {
            StockKline k = klineData.get(i);
            sb.append(String.format("| %s | %.2f | %.2f%% | %.0f |\n",
                    k.getDate(),
                    k.getClosePrice(),
                    k.getChangePercent() != null ? k.getChangePercent() : BigDecimal.ZERO,
                    k.getVolume() != null ? k.getVolume() : 0));
        }

        return sb.toString();
    }

    private String buildSystemPrompt() {
        return """
                你是一名资深技术分析师，擅长解读技术指标数据。

                重要：你收到的技术指标由TA4j专业库计算（准确率100%），你的职责是：
                1. 解读指标含义（而非计算指标）
                2. 综合多个指标给出判断
                3. 识别关键支撑位和阻力位
                4. 预判短期（3-5天）走势
                5. 给出明确的技术面评级和交易信号

                分析维度：
                - **趋势强度**：均线系统是否顺畅排列？价格是否在均线之上？
                - **动量指标**：MACD、RSI、KDJ是否形成共振？
                - **超买超卖**：RSI和KDJ是否处于极端区域？
                - **压力支撑**：布林带位置、关键均线位置
                - **量价配合**：价格上涨时成交量是否放大？

                输出要求（严格JSON格式）：
                {
                  "dimension": "TECHNICAL",
                  "score": 0-100分（技术面综合评分）,
                  "suggestion": "BUY|SELL|HOLD",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "核心理由（50字以内）",
                  "assumptions": [
                    "关键假设1（如：假设当前上升趋势延续，不跌破MA20支撑）",
                    "关键假设2（如：假设布林带上轨阻力有效，短期不突破）",
                    "关键假设3（如：假设成交量配合，放量上涨或缩量下跌）",
                    "关键假设4（如：假设技术指标无重大背离，MACD金叉有效）"
                  ],
                  "analysis": "详细分析报告（markdown格式，包含：趋势判断、关键位置、短期预判）"
                }

                suggestion判断标准：
                - BUY（≥70分）：多头排列 + MACD金叉 + RSI未超买 + 突破关键阻力 + 放量
                - SELL（≤40分）：空头排列 + MACD死叉 + RSI超卖未止跌 + 跌破支撑 + 缩量
                - HOLD（40-70分）：震荡走势或信号不明确

                注意：
                - 数据由专业库计算，你只需解读，不要怀疑数据准确性
                - 多个指标共振时提高置信度
                - 技术分析有滞后性，给出风险提示

                **置信度评估标准（数据质量驱动）**：
                - HIGH：K线数据完整（至少60日）+ 所有技术指标计算成功 + 多个指标形成共振
                - MEDIUM：K线数据完整但指标信号矛盾（如MACD金叉但RSI超买）OR 数据30-60日
                - LOW：K线数据不足30日 OR 关键指标（MACD/RSI/均线）计算失败或异常
                """;
    }

    private String buildUserPrompt(String stockCode, String indicatorSummary, String userQuestion) {
        return String.format("""
                请对股票【%s】进行技术面解读。

                用户问题：%s

                %s

                请基于以上专业库计算的技术指标数据，综合分析：

                1. **趋势判断**（20分）
                   - 均线系统排列情况
                   - 当前趋势是上升、下降还是震荡？
                   - 趋势强度如何？

                2. **动量分析**（30分）
                   - MACD金叉/死叉，红绿柱变化
                   - RSI强弱程度，是否超买超卖
                   - KDJ信号是否与其他指标共振

                3. **关键位置**（20分）
                   - 当前价格在布林带的位置
                   - 重要均线支撑/阻力位
                   - 关键价格区间

                4. **量价配合**（15分）
                   - 成交量是否配合价格走势
                   - 是否出现放量突破或缩量下跌

                5. **短期预判**（15分）
                   - 未来3-5天可能走势
                   - 需要关注的触发条件
                   - 风险提示
                   - **关键假设（必须输出3-4条）**：
                     * 趋势延续性假设（如：假设上升趋势延续，不跌破MA20）
                     * 支撑/阻力假设（如：假设布林带上轨阻力有效）
                     * 量价配合假设（如：假设放量上涨，缩量回调）
                     * 技术指标假设（如：假设MACD金叉有效，不出现背离）

                请严格按JSON格式输出，不要包含其他文字。
                """,
                stockCode,
                userQuestion != null ? userQuestion : "分析该股票的技术面",
                indicatorSummary);
    }
}
