package com.quantai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.mapper.AgentAdviceMapper;
import com.quantai.mapper.RiskMonitorLogMapper;
import com.quantai.model.dto.RiskCondition;
import com.quantai.model.entity.AgentAdvice;
import com.quantai.model.entity.RiskMonitorLog;
import com.quantai.model.vo.KlineVO;
import com.quantai.service.impl.StockServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 风险监控服务
 */
@Slf4j
@Service
public class RiskMonitorService {

    @Autowired
    private AgentAdviceMapper adviceMapper;

    @Autowired
    private RiskMonitorLogMapper logMapper;

    @Autowired
    private StockServiceImpl stockService;

    @Autowired
    private com.quantai.feishu.FeishuNotificationService feishuService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 每日风险检查（由定时任务调用）
     */
    public void dailyRiskCheck() {
        log.info("开始每日风险检查...");
        LocalDate today = LocalDate.now();

        List<AgentAdvice> activeAdvices = adviceMapper.selectActiveAdvices();
        log.info("当前活跃建议数量: {}", activeAdvices.size());

        for (AgentAdvice advice : activeAdvices) {
            try {
                checkSingleAdvice(advice, today);
            } catch (Exception e) {
                log.error("检查建议失败: adviceId={}, error={}", advice.getId(), e.getMessage(), e);
            }
        }

        // 发送待发送的告警
        sendPendingAlerts(today);

        log.info("每日风险检查完成");
    }

    /**
     * 检查单个建议的风险条件
     */
    private void checkSingleAdvice(AgentAdvice advice, LocalDate checkDate) {
        log.info("检查建议: id={}, stock={}", advice.getId(), advice.getStockCode());

        // 获取当日市场数据
        List<KlineVO> klines = stockService.getKlineData(advice.getStockCode(), "DAY", 30);
        if (klines.isEmpty()) {
            log.warn("无法获取K线数据: {}", advice.getStockCode());
            return;
        }

        KlineVO latestKline = klines.get(0);
        BigDecimal currentPrice = latestKline.getClose();

        // 计算均线
        BigDecimal ma5 = calculateMA(klines, 5);
        BigDecimal ma20 = calculateMA(klines, 20);

        // 解析风险条件
        List<RiskCondition> riskConditions = parseRiskConditions(advice.getRiskConditions());

        // 检查每个风险条件
        List<RiskCondition> triggeredRules = new ArrayList<>();
        String highestRiskLevel = "LOW";

        for (RiskCondition condition : riskConditions) {
            boolean triggered = checkCondition(condition, currentPrice, ma5, ma20, klines, advice);
            if (triggered) {
                triggeredRules.add(condition);
                highestRiskLevel = getHigherRiskLevel(highestRiskLevel, condition.getSeverity());
            }
        }

        // 记录监控日志
        RiskMonitorLog log = new RiskMonitorLog();
        log.setAdviceId(advice.getId());
        log.setStockCode(advice.getStockCode());
        log.setCheckDate(checkDate);
        log.setCurrentPrice(currentPrice);
        log.setMa5(ma5);
        log.setMa20(ma20);
        log.setVolume(latestKline.getVolume());
        log.setRiskTriggered(!triggeredRules.isEmpty());
        log.setRiskLevel(highestRiskLevel);
        log.setAlertSent(false);

        try {
            log.setTriggeredRules(objectMapper.writeValueAsString(triggeredRules));
        } catch (Exception e) {
            this.log.error("序列化触发规则失败", e);
        }

        logMapper.insert(log);

        // 如果触发风险，更新建议状态
        if (!triggeredRules.isEmpty()) {
            advice.setStatus("TRIGGERED");
            advice.setTriggeredCondition(triggeredRules.get(0).getDescription());
            advice.setTriggerTime(java.time.LocalDateTime.now());
            adviceMapper.updateById(advice);
        }
    }

    /**
     * 检查单个风险条件
     */
    private boolean checkCondition(RiskCondition condition, BigDecimal currentPrice,
                                     BigDecimal ma5, BigDecimal ma20,
                                     List<KlineVO> klines, AgentAdvice advice) {
        switch (condition.getType()) {
            case "STOP_LOSS":
                // 跌破止损价
                return currentPrice.compareTo(condition.getThreshold()) <= 0;

            case "MA_BREAK":
                // 跌破均线
                return currentPrice.compareTo(condition.getThreshold()) < 0;

            case "CAPITAL_OUTFLOW":
                // 连续资金流出（简化版：连续下跌天数）
                int consecutiveDown = countConsecutiveDown(klines);
                return consecutiveDown >= condition.getThreshold().intValue();

            case "VOLUME_SURGE":
                // 成交量异常放大
                BigDecimal avgVolume = calculateAvgVolume(klines, 20);
                BigDecimal currentVolume = new BigDecimal(klines.get(0).getVolume());
                BigDecimal ratio = currentVolume.divide(avgVolume, 2, RoundingMode.HALF_UP);
                return ratio.compareTo(condition.getThreshold()) >= 0;

            case "TREND_REVERSAL":
                // 趋势反转（MA5跌破MA20）
                return ma5.compareTo(ma20) < 0;

            default:
                return false;
        }
    }

    /**
     * 发送待发送的告警
     */
    private void sendPendingAlerts(LocalDate today) {
        List<RiskMonitorLog> unsentLogs = logMapper.selectUnsent(today);
        log.info("待发送告警数量: {}", unsentLogs.size());

        for (RiskMonitorLog monitorLog : unsentLogs) {
            try {
                // 获取建议详情
                AgentAdvice advice = adviceMapper.selectById(monitorLog.getAdviceId());
                if (advice == null) continue;

                // 构造告警消息
                String message = buildAlertMessage(advice, monitorLog);

                // 发送飞书告警
                feishuService.sendMessage(message);

                // 标记为已发送
                monitorLog.setAlertSent(true);
                monitorLog.setAlertChannel("FEISHU");
                logMapper.updateById(monitorLog);

                log.info("风险告警已发送: adviceId={}, stock={}", advice.getId(), advice.getStockCode());
            } catch (Exception e) {
                log.error("发送告警失败: logId={}, error={}", monitorLog.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 构造告警消息
     */
    private String buildAlertMessage(AgentAdvice advice, RiskMonitorLog monitorLog) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ 风险提醒\n\n");
        sb.append("股票：").append(advice.getStockName()).append("(").append(advice.getStockCode()).append(")\n");
        sb.append("建议类型：").append(formatAdviceType(advice.getAdviceType())).append("\n");
        sb.append("建议价格：").append(advice.getAdvicePrice()).append(" 元\n");
        sb.append("当前价格：").append(monitorLog.getCurrentPrice()).append(" 元\n");
        sb.append("风险等级：").append(formatRiskLevel(monitorLog.getRiskLevel())).append("\n\n");

        // 解析触发的规则
        try {
            List<RiskCondition> rules = objectMapper.readValue(
                monitorLog.getTriggeredRules(),
                new TypeReference<List<RiskCondition>>() {}
            );
            sb.append("触发条件：\n");
            for (RiskCondition rule : rules) {
                sb.append("  • ").append(rule.getDescription()).append("\n");
            }
        } catch (Exception e) {
            sb.append("触发条件：解析失败\n");
        }

        sb.append("\n建议：重新评估该股票的投资价值");
        return sb.toString();
    }

    /**
     * 解析风险条件JSON
     */
    private List<RiskCondition> parseRiskConditions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<RiskCondition>>() {});
        } catch (Exception e) {
            log.error("解析风险条件失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    /**
     * 计算移动平均线
     */
    private BigDecimal calculateMA(List<KlineVO> klines, int period) {
        if (klines.size() < period) return BigDecimal.ZERO;

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(klines.get(i).getClose());
        }
        return sum.divide(new BigDecimal(period), 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算平均成交量
     */
    private BigDecimal calculateAvgVolume(List<KlineVO> klines, int period) {
        if (klines.size() < period) return BigDecimal.ONE;

        long sum = 0;
        for (int i = 0; i < period; i++) {
            sum += klines.get(i).getVolume();
        }
        return new BigDecimal(sum).divide(new BigDecimal(period), 0, RoundingMode.HALF_UP);
    }

    /**
     * 统计连续下跌天数
     */
    private int countConsecutiveDown(List<KlineVO> klines) {
        int count = 0;
        for (int i = 0; i < klines.size() - 1; i++) {
            if (klines.get(i).getClose().compareTo(klines.get(i + 1).getClose()) < 0) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 获取更高的风险等级
     */
    private String getHigherRiskLevel(String level1, String level2) {
        int priority1 = getRiskPriority(level1);
        int priority2 = getRiskPriority(level2);
        return priority1 > priority2 ? level1 : level2;
    }

    private int getRiskPriority(String level) {
        switch (level) {
            case "CRITICAL": return 4;
            case "HIGH": return 3;
            case "MEDIUM": return 2;
            case "LOW": return 1;
            default: return 0;
        }
    }

    private String formatAdviceType(String type) {
        switch (type) {
            case "BUY": return "买入";
            case "SELL": return "卖出";
            case "HOLD": return "观望";
            default: return type;
        }
    }

    private String formatRiskLevel(String level) {
        switch (level) {
            case "CRITICAL": return "🔴 严重";
            case "HIGH": return "🟠 高";
            case "MEDIUM": return "🟡 中";
            case "LOW": return "🟢 低";
            default: return level;
        }
    }
}
