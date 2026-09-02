package com.quantai.service.agent.impl;

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

/**
 * 风险评估师 Agent
 * 职责：计算风险指标（波动率、回撤、Beta），评估风险等级
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAssessmentAgent implements Agent {

    private final OpenAiChatModel chatModel;

    @Override
    public String getName() {
        return "RiskAssessment";
    }

    @Override
    public String getRole() {
        return "风险评估师";
    }

    @Override
    public String getGoal() {
        return "评估投资风险，包括市场风险、财务风险、流动性风险，给出风险等级和风险提示";
    }

    @Override
    public List<String> getToolNames() {
        // 风险评估主要基于前置数据，可选用K线数据计算波动率
        return List.of("get_kline");
    }

    @Override
    public AgentResult execute(AgentContext context) {
        long start = System.currentTimeMillis();
        log.info("[{}] 开始执行 - 股票代码: {}", getName(), context.getStockCode());

        try {
            // 获取前置Agent的输出
            String technicalData = context.getPreviousOutput("TechnicalAnalyst");
            String financialData = context.getPreviousOutput("FinancialDataExpert");
            String marketData = context.getPreviousOutput("MarketResearcher");
            String fundamentalAnalysis = context.getPreviousOutput("FundamentalAnalyst");

            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(context, technicalData, financialData,
                    marketData, fundamentalAnalysis);

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
            return AgentResult.failure("风险评估师执行失败: " + e.getMessage(), duration);
        }
    }

    private String buildSystemPrompt() {
        return """
                你是一名资深风险评估师，专注于投资风险量化和风险管理。

                你的职责：
                1. 评估市场风险（价格波动、技术形态风险）
                2. 评估财务风险（负债、现金流、盈利稳定性）
                3. 评估流动性风险（成交量、市值）
                4. 评估数据风险（数据质量、时效性）
                5. 综合给出风险等级（高/中/低）和风险提示

                风险评估框架：
                - 市场风险：技术面是否恶化、是否处于下跌趋势
                - 财务风险：负债率是否过高、盈利是否下滑
                - 流动性风险：成交量是否萎缩、资金是否流出
                - 数据风险：数据是否过时、是否有异常信号

                输出要求（严格JSON格式）：
                {
                  "dimension": "RISK",
                  "score": 0-100分（风险评分，分数越低风险越高）,
                  "suggestion": "BUY|SELL|HOLD",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "核心理由（50字以内）",
                  "assumptions": [
                    "关键假设1（如：假设止损位20.5元有效，跌破立即退出）",
                    "关键假设2（如：假设财务风险可控，负债率不超过70%）",
                    "关键假设3（如：假设流动性充足，日均成交量不低于XX万手）",
                    "关键假设4（如：假设无重大黑天鹅事件发生）"
                  ],
                  "analysis": "详细风险分析报告（markdown格式，包含：市场风险、财务风险、流动性风险、建议止损位）"
                }

                注意：
                - 量化风险（用数据说话）
                - 分级风险（高/中/低）
                - 给出风险提示和建议止损位
                - 必须输出3-4条关键假设

                **置信度评估标准（数据质量驱动）**：
                - HIGH：技术数据完整 + 财务数据完整 + 市场数据完整，可全面评估风险
                - MEDIUM：技术数据或财务数据缺失其一 OR 数据时效性一般（财务数据超过1个季度）
                - LOW：技术数据和财务数据同时缺失 OR 数据严重过时（财务数据超过2个季度）OR 关键风险指标无法计算
                """;
    }

    private String buildUserPrompt(AgentContext context, String technicalData,
                                   String financialData, String marketData,
                                   String fundamentalAnalysis) {
        return String.format("""
                请对股票 %s 进行全面风险评估：

                【技术分析师的分析】
                %s

                【财务数据专家的分析】
                %s

                【市场研究员的分析】
                %s

                【基本面分析师的评估】
                %s

                请综合以上信息，完成风险评估：

                1. 市场风险评估
                   - 技术面风险（是否看空、是否有破位风险）
                   - 价格波动风险（根据K线数据质量和形态判断）
                   - 趋势风险（上升/下跌/震荡趋势的风险）

                2. 财务风险评估
                   - 负债风险（负债率是否过高）
                   - 盈利稳定性（利润是否波动大）
                   - 现金流风险（是否有现金流压力）

                3. 流动性风险评估
                   - 资金流向风险（主力是否流出）
                   - 成交量风险（是否萎缩）
                   - 市场情绪风险（负面情绪占比）

                4. 数据风险评估
                   - 数据时效性（财务数据是否过时）
                   - 数据异常（是否有异常信号需警惕）
                   - 数据可靠性（数据质量评分）

                5. 综合风险评级
                   风险等级：[高/中/低]
                   主要风险：[列出2-3项最大风险]
                   风险提示：[给出具体的风险警示]
                   建议止损位：[根据技术分析给出止损位，如无技术数据则说明"需补充技术数据"]
                   **关键假设（必须输出3-4条）**：
                     * 止损假设（如：假设止损位20.5元有效，跌破立即退出）
                     * 财务风险假设（如：假设负债率不超过70%，财务安全可控）
                     * 流动性假设（如：假设日均成交量不低于XX万手，流动性充足）
                     * 黑天鹅假设（如：假设无重大黑天鹅事件发生）

                请严格按JSON格式输出，不要包含其他文字。

                注意：
                1. 如果技术面看空、财务数据过时、资金大量流出，风险等级应为"高"
                2. 如果只有部分风险因素，风险等级为"中"
                3. 如果各项指标健康，风险等级为"低"
                """,
                context.getStockCode(),
                technicalData != null ? technicalData : "无技术数据",
                financialData != null ? financialData : "无财务数据",
                marketData != null ? marketData : "无市场数据",
                fundamentalAnalysis != null ? fundamentalAnalysis : "无基本面分析");
    }
}
