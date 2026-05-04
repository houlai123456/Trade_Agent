package com.quantai.service.agent;

import com.quantai.model.entity.StockQuote;
import com.quantai.model.vo.KlineVO;
import com.quantai.model.vo.MarketAnalysis;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 市场分析Agent — 纯数据驱动，不调用LLM
 * 分析K线趋势、MA位置、成交量变化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataAgent {

    private final StockService stockService;

    public MarketAnalysis analyze(String stockCode) {
        long start = System.currentTimeMillis();

        StockQuote quote = stockService.getQuote(stockCode);
        List<KlineVO> klineList = stockService.getKlineData(stockCode, "DAY", 20);

        MarketAnalysis analysis = MarketAnalysis.builder()
                .stockCode(stockCode)
                .stockName(quote != null ? quote.getName() : stockCode)
                .currentPrice(quote != null ? quote.getCurrentPrice() : null)
                .build();

        if (klineList == null || klineList.isEmpty()) {
            analysis.setTrend("UNKNOWN");
            analysis.setTrendDescription("K线数据不足");
            return analysis;
        }

        // 最新K线（最后一条）
        KlineVO last = klineList.get(klineList.size() - 1);
        analysis.setMa5(last.getMa5());
        analysis.setMa10(last.getMa10());
        analysis.setMa20(last.getMa20());

        // 1. 价格与MA位置关系
        BigDecimal price = analysis.getCurrentPrice() != null ? analysis.getCurrentPrice() : last.getClose();
        analysis.setMaStatus(buildMaStatus(price, last.getMa5(), last.getMa10(), last.getMa20()));

        // 2. 趋势判断 — 看最近N日收盘价方向
        analysis.setTrend(determineTrend(klineList));
        analysis.setTrendDescription(buildTrendDescription(analysis.getTrend(), last));

        // 3. 成交量分析
        analysis.setLatestVolume(last.getVolume());
        analysis.setAvgVolume5(calcAvgVolume(klineList, 5));
        analysis.setVolumeAnalysis(buildVolumeAnalysis(last.getVolume(), calcAvgVolume(klineList, 5), calcAvgVolume(klineList, 20)));

        // 4. 近期涨跌幅（近5日）
        if (klineList.size() >= 5) {
            KlineVO fiveDaysAgo = klineList.get(klineList.size() - 5);
            if (fiveDaysAgo.getClose() != null && fiveDaysAgo.getClose().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = last.getClose().subtract(fiveDaysAgo.getClose())
                        .divide(fiveDaysAgo.getClose(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                analysis.setChangePercent5(change);
            }
        }

        // 5. 连续涨跌天数
        analysis.setConsecutiveDirection(countConsecutiveDirection(klineList));

        // 6. K线形态识别
        analysis.setCandlePattern(identifyCandle(last));

        log.info("市场分析Agent完成 code={} trend={} 耗时={}ms",
                stockCode, analysis.getTrend(), System.currentTimeMillis() - start);
        return analysis;
    }

    private String buildMaStatus(BigDecimal price, BigDecimal ma5, BigDecimal ma10, BigDecimal ma20) {
        StringBuilder sb = new StringBuilder();
        int cntAbove = 0;
        if (ma5 != null && price.compareTo(ma5) >= 0) cntAbove++;
        if (ma10 != null && price.compareTo(ma10) >= 0) cntAbove++;
        if (ma20 != null && price.compareTo(ma20) >= 0) cntAbove++;

        // 计算偏离度
        if (ma5 != null && ma5.compareTo(BigDecimal.ZERO) > 0) {
            double dev = price.subtract(ma5).divide(ma5, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
            sb.append("价格在MA5").append(dev >= 0 ? "之上" : "之下").append(String.format("(%.2f%%)", dev)).append("，");
        }
        if (cntAbove >= 2) sb.append("整体处于均线上方，偏强");
        else if (cntAbove == 1) sb.append("均线附近震荡");
        else sb.append("整体处于均线下方，偏弱");
        return sb.toString();
    }

    private String determineTrend(List<KlineVO> klineList) {
        if (klineList.size() < 10) return "SIDEWAYS";
        // 用最近10日收盘价的线性趋势判断
        int n = Math.min(10, klineList.size());
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            KlineVO k = klineList.get(klineList.size() - n + i);
            double y = k.getClose().doubleValue();
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumX2 += i * i;
        }
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        // 用当前价格的百分比判断斜率大小
        double avgPrice = sumY / n;
        double slopePercent = slope / avgPrice * 100;

        if (slopePercent > 1.0) return "UP_TREND";
        if (slopePercent < -1.0) return "DOWN_TREND";
        return "SIDEWAYS";
    }

    private String buildTrendDescription(String trend, KlineVO last) {
        return switch (trend) {
            case "UP_TREND" -> "短期呈上升趋势，高点逐步抬高";
            case "DOWN_TREND" -> "短期呈下降趋势，低点逐步降低";
            case "SIDEWAYS" -> "短期横盘震荡，方向不明";
            default -> "无法判断趋势";
        };
    }

    private Double calcAvgVolume(List<KlineVO> list, int days) {
        int n = Math.min(days, list.size());
        long sum = 0;
        for (int i = list.size() - n; i < list.size(); i++) {
            sum += list.get(i).getVolume() != null ? list.get(i).getVolume() : 0;
        }
        return (double) sum / n;
    }

    private String buildVolumeAnalysis(Long latestVol, Double avg5, Double avg20) {
        if (latestVol == null || avg5 == null || avg5 == 0) return "量能数据不足";
        double ratio = latestVol / avg5;
        if (ratio > 1.5) return "近5日均量" + String.format("%.0f", avg5) + "股，今日" + latestVol + "股，明显放量";
        if (ratio < 0.6) return "近5日均量" + String.format("%.0f", avg5) + "股，今日" + latestVol + "股，明显缩量";
        return "近5日均量" + String.format("%.0f", avg5) + "股，今日" + latestVol + "股，量能正常";
    }

    private Integer countConsecutiveDirection(List<KlineVO> klineList) {
        if (klineList.size() < 3) return 0;
        int count = 0;
        boolean up = klineList.get(klineList.size() - 1).getClose()
                .compareTo(klineList.get(klineList.size() - 2).getClose()) >= 0;
        for (int i = klineList.size() - 1; i > 0; i--) {
            BigDecimal cur = klineList.get(i).getClose();
            BigDecimal prev = klineList.get(i - 1).getClose();
            boolean curUp = cur.compareTo(prev) >= 0;
            if (curUp == up) count++;
            else break;
        }
        return up ? count : -count;
    }

    private String identifyCandle(KlineVO k) {
        if (k.getOpen() == null || k.getClose() == null) return "未知";
        double body = Math.abs(k.getClose().doubleValue() - k.getOpen().doubleValue());
        double highLow = k.getHigh().doubleValue() - k.getLow().doubleValue();
        if (highLow == 0) return "一字线";

        double bodyRatio = body / highLow;
        boolean isUp = k.getClose().compareTo(k.getOpen()) > 0;
        double change = k.getChangePercent() != null ? k.getChangePercent().doubleValue() : 0;

        if (bodyRatio < 0.1) return "十字星";
        if (change > 4) return isUp ? "大阳线" : "大阴线";
        if (change > 2) return isUp ? "中阳线" : "中阴线";
        if (change > 0.5) return isUp ? "小阳线" : "小阴线";
        return isUp ? "微涨" : "微跌";
    }
}
