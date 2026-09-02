package com.quantai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 风险条件DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskCondition {

    /**
     * 风险类型
     * MA_BREAK - 均线破位
     * STOP_LOSS - 止损触发
     * VOLUME_SURGE - 成交量异常
     * CAPITAL_OUTFLOW - 资金流出
     * TREND_REVERSAL - 趋势反转
     */
    private String type;

    /**
     * 阈值（根据类型不同含义不同）
     * MA_BREAK: 均线价格
     * STOP_LOSS: 止损价格
     * VOLUME_SURGE: 成交量倍数
     * CAPITAL_OUTFLOW: 连续天数
     */
    private BigDecimal threshold;

    /**
     * 描述
     */
    private String description;

    /**
     * 严重程度: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String severity;

    /**
     * 创建预设风险条件
     */
    public static List<RiskCondition> createDefault(BigDecimal stopLossPrice, BigDecimal ma20) {
        return List.of(
            new RiskCondition("STOP_LOSS", stopLossPrice,
                "跌破止损价 " + stopLossPrice + " 元", "CRITICAL"),
            new RiskCondition("MA_BREAK", ma20,
                "跌破MA20均线 " + ma20 + " 元", "HIGH"),
            new RiskCondition("CAPITAL_OUTFLOW", new BigDecimal("5"),
                "主力资金连续5天净流出", "MEDIUM"),
            new RiskCondition("VOLUME_SURGE", new BigDecimal("3"),
                "成交量异常放大(>3倍均量)", "LOW")
        );
    }
}
