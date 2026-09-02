package com.quantai.service.agent.tool;

import com.quantai.model.vo.KlineVO;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据质量检查工具
 * 检查K线数据完整度、时间连续性、缺失字段，给出质量评分和分析建议
 */
@Component
@RequiredArgsConstructor
public class DataQualityTool implements Tool {

    private final StockService stockService;

    @Override
    public String getName() {
        return "check_data_quality";
    }

    @Override
    public String getDescription() {
        return "检查股票K线数据质量，评估数据完整度、时间连续性、缺失字段。返回质量评分(0-100)和分析建议。参数：code=股票代码, period=DAY|WEEK|MONTH(默认DAY)";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("code", "股票代码，如 sh600519");
        params.put("period", "K线周期：DAY(日K)/WEEK(周K)/MONTH(月K)，默认为DAY");
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String code = (String) args.get("code");
        if (code == null || code.isBlank()) {
            return "错误：缺少code参数";
        }

        String period = args.getOrDefault("period", "DAY").toString();

        // 获取最近90天数据进行质量检查
        List<KlineVO> klineList = stockService.getKlineData(code, period, 90);

        if (klineList == null || klineList.isEmpty()) {
            return buildResult(code, 0, "无数据", "数据源无此股票数据，可能是代码错误或次新股", "跳过技术分析");
        }

        int dataCount = klineList.size();
        int missingFields = 0;
        int gapDays = 0;

        // 检查缺失字段
        for (KlineVO k : klineList) {
            if (k.getOpen() == null) missingFields++;
            if (k.getClose() == null) missingFields++;
            if (k.getHigh() == null) missingFields++;
            if (k.getLow() == null) missingFields++;
            if (k.getVolume() == null) missingFields++;
        }

        // 检查时间连续性（只对日K检查）
        if ("DAY".equals(period) && klineList.size() >= 2) {
            for (int i = 1; i < klineList.size(); i++) {
                LocalDate prev = klineList.get(i - 1).getDate();
                LocalDate curr = klineList.get(i).getDate();
                long daysBetween = ChronoUnit.DAYS.between(prev, curr);
                // 交易日间隔超过5天视为数据缺口（排除周末和节假日）
                if (daysBetween > 5) {
                    gapDays++;
                }
            }
        }

        // 计算质量评分 (0-100)
        int score = 100;
        String quality;
        String issues = "";
        String recommendation;

        // 数据量扣分
        if (dataCount < 20) {
            score -= 40;
            issues += "数据量不足20条；";
        } else if (dataCount < 60) {
            score -= 20;
            issues += "数据量偏少；";
        }

        // 缺失字段扣分
        if (missingFields > 0) {
            score -= Math.min(30, missingFields * 5);
            issues += "存在" + missingFields + "个缺失字段；";
        }

        // 数据缺口扣分
        if (gapDays > 3) {
            score -= 20;
            issues += "存在" + gapDays + "个明显数据缺口；";
        } else if (gapDays > 0) {
            score -= 10;
            issues += "存在少量数据缺口；";
        }

        score = Math.max(0, score);

        // 质量评级
        if (score >= 80) {
            quality = "优秀";
            recommendation = "可进行深度技术分析（MA、MACD、布林带等）";
        } else if (score >= 60) {
            quality = "良好";
            recommendation = "可进行常规技术分析，但需注意数据缺口的影响";
        } else if (score >= 40) {
            quality = "一般";
            recommendation = "建议仅做基础趋势分析，避免依赖复杂技术指标";
        } else {
            quality = "较差";
            recommendation = "数据质量不足，建议跳过技术分析或仅参考基本面";
        }

        if (issues.isEmpty()) {
            issues = "无明显问题";
        }

        return buildResult(code, score, quality, issues, recommendation);
    }

    private String buildResult(String code, int score, String quality, String issues, String recommendation) {
        return String.format(
            "【%s 数据质量报告】\n" +
            "质量评分：%d/100 (%s)\n" +
            "问题：%s\n" +
            "建议：%s",
            code, score, quality, issues, recommendation
        );
    }
}
