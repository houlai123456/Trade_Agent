package com.quantai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.mapper.AgentAdviceMapper;
import com.quantai.model.dto.RiskCondition;
import com.quantai.model.entity.AgentAdvice;
import com.quantai.model.entity.StockKline;
import com.quantai.model.vo.MarketAnalysis;
import com.quantai.model.vo.TradeSuggestion;
import com.quantai.service.impl.StockServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent建议保存服务
 * 负责将Agent的分析建议持久化，并生成风险条件
 */
@Slf4j
@Service
public class AgentAdviceService {

    @Autowired
    private AgentAdviceMapper adviceMapper;

    @Autowired
    private StockServiceImpl stockService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 保存Agent建议
     *
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param suggestion 建议内容
     * @param marketAnalysis 市场分析数据
     * @param fullReport 完整报告（可选）
     * @return 建议ID
     */
    public Long saveAdvice(String stockCode, String stockName,
                          TradeSuggestion suggestion,
                          MarketAnalysis marketAnalysis,
                          String fullReport) {
        try {
            AgentAdvice advice = new AgentAdvice();
            advice.setStockCode(stockCode);
            advice.setStockName(stockName);

            // 建议类型
            advice.setAdviceType(suggestion.getAction()); // BUY, SELL, HOLD
            advice.setConfidenceScore(mapConfidenceToScore(suggestion.getConfidence()));

            // 价格信息
            BigDecimal currentPrice = marketAnalysis.getCurrentPrice();
            advice.setAdvicePrice(currentPrice);

            // 根据建议类型计算目标价和止损价
            if ("BUY".equals(suggestion.getAction())) {
                advice.setTargetPrice(currentPrice.multiply(new BigDecimal("1.20"))); // 目标+20%
                advice.setStopLossPrice(currentPrice.multiply(new BigDecimal("0.96"))); // 止损-4%
                advice.setExpectedReturn(new BigDecimal("20.00"));
            } else if ("SELL".equals(suggestion.getAction())) {
                advice.setTargetPrice(currentPrice.multiply(new BigDecimal("0.80"))); // 目标-20%
                advice.setStopLossPrice(currentPrice.multiply(new BigDecimal("1.04"))); // 止损+4%
                advice.setExpectedReturn(new BigDecimal("-20.00"));
            } else {
                // HOLD不设目标价
                advice.setExpectedReturn(BigDecimal.ZERO);
            }

            // 生成风险条件
            BigDecimal ma20 = marketAnalysis.getMa20();
            BigDecimal stopLossPrice = advice.getStopLossPrice();

            List<RiskCondition> riskConditions = RiskCondition.createDefault(stopLossPrice, ma20);
            advice.setRiskConditions(objectMapper.writeValueAsString(riskConditions));

            // 状态
            advice.setStatus("ACTIVE");
            advice.setFullReport(fullReport);
            advice.setAgentVersion("1.0");
            advice.setCreateTime(LocalDateTime.now());
            advice.setUpdateTime(LocalDateTime.now());

            adviceMapper.insert(advice);
            log.info("保存Agent建议成功: id={}, stock={}, action={}",
                    advice.getId(), stockCode, suggestion.getAction());

            return advice.getId();

        } catch (Exception e) {
            log.error("保存Agent建议失败: stock={}", stockCode, e);
            return null;
        }
    }

    /**
     * 查询股票的历史建议准确率
     */
    public String getAccuracyStats(String stockCode) {
        try {
            AgentAdviceMapper.AccuracyStats stats = adviceMapper.selectAccuracyStats(stockCode);
            if (stats == null || stats.total == 0) {
                return "暂无历史建议数据";
            }

            int winRate = (int) ((double) stats.correct / stats.total * 100);
            String avgReturn = stats.avgReturn != null ?
                String.format("%.2f%%", stats.avgReturn) : "N/A";

            return String.format("历史建议: 总计%d次, 准确率%d%%, 平均收益率%s",
                    stats.total, winRate, avgReturn);
        } catch (Exception e) {
            log.error("查询准确率失败: stock={}", stockCode, e);
            return "";
        }
    }

    /**
     * 关闭建议（用户手动关闭或不再需要监控）
     */
    public void closeAdvice(Long adviceId) {
        AgentAdvice advice = adviceMapper.selectById(adviceId);
        if (advice != null) {
            advice.setStatus("CLOSED");
            advice.setUpdateTime(LocalDateTime.now());
            adviceMapper.updateById(advice);
            log.info("关闭建议: id={}", adviceId);
        }
    }

    /**
     * 将置信度映射为分数
     */
    private BigDecimal mapConfidenceToScore(String confidence) {
        return switch (confidence) {
            case "HIGH" -> new BigDecimal("80.00");
            case "MEDIUM" -> new BigDecimal("60.00");
            case "LOW" -> new BigDecimal("40.00");
            default -> new BigDecimal("50.00");
        };
    }
}
