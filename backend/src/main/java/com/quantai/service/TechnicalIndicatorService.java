package com.quantai.service;

import com.quantai.model.entity.StockKline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * 技术指标计算服务（基于TA4j专业库）
 * 职责：提供准确的技术指标计算，让LLM专注于解读而非计算
 */
@Slf4j
@Service
public class TechnicalIndicatorService {

    /**
     * 计算完整的技术指标集合
     */
    public TechnicalIndicators calculateIndicators(List<StockKline> klineData) {
        if (klineData == null || klineData.size() < 20) {
            log.warn("K线数据不足，无法计算技术指标");
            return new TechnicalIndicators();
        }

        try {
            // 转换为TA4j的BarSeries
            BarSeries series = convertToBarSeries(klineData);

            TechnicalIndicators indicators = new TechnicalIndicators();

            // 1. 移动平均线 (MA)
            indicators.setMa5(calculateMA(series, 5));
            indicators.setMa10(calculateMA(series, 10));
            indicators.setMa20(calculateMA(series, 20));
            indicators.setMa60(calculateMA(series, 60));

            // 2. MACD
            MacdIndicators macd = calculateMACD(series);
            indicators.setMacd(macd);

            // 3. RSI
            indicators.setRsi6(calculateRSI(series, 6));
            indicators.setRsi12(calculateRSI(series, 12));
            indicators.setRsi24(calculateRSI(series, 24));

            // 4. KDJ
            KdjIndicators kdj = calculateKDJ(series, 9, 3, 3);
            indicators.setKdj(kdj);

            // 5. 布林带
            BollingerBands boll = calculateBollingerBands(series, 20, 2);
            indicators.setBollingerBands(boll);

            // 6. ATR (平均真实波幅)
            indicators.setAtr(calculateATR(series, 14));

            // 7. OBV (能量潮)
            indicators.setObv(calculateOBV(series));

            // 8. 成交量MA
            indicators.setVolumeMa5(calculateVolumeMA(series, 5));
            indicators.setVolumeMa10(calculateVolumeMA(series, 10));

            // 9. 当前价格
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            indicators.setCurrentPrice(closePrice.getValue(series.getEndIndex()).doubleValue());

            // 10. 趋势判断
            indicators.setTrend(analyzeTrend(indicators));

            log.info("技术指标计算完成，数据点数: {}", series.getBarCount());
            return indicators;

        } catch (Exception e) {
            log.error("计算技术指标失败", e);
            return new TechnicalIndicators();
        }
    }

    /**
     * 转换K线数据为TA4j BarSeries
     */
    private BarSeries convertToBarSeries(List<StockKline> klineData) {
        BarSeries series = new BaseBarSeries("stock");

        // K线数据从新到旧，需要反转
        List<StockKline> reversed = new ArrayList<>(klineData);
        Collections.reverse(reversed);

        for (StockKline k : reversed) {
            if (k.getOpenPrice() == null || k.getClosePrice() == null) continue;

            ZonedDateTime time = k.getDate().atStartOfDay(ZoneId.systemDefault());
            Num open = DecimalNum.valueOf(k.getOpenPrice().doubleValue());
            Num high = DecimalNum.valueOf(k.getHighPrice().doubleValue());
            Num low = DecimalNum.valueOf(k.getLowPrice().doubleValue());
            Num close = DecimalNum.valueOf(k.getClosePrice().doubleValue());
            Num volume = DecimalNum.valueOf(k.getVolume() != null ? k.getVolume() : 0);

            series.addBar(time, open, high, low, close, volume);
        }

        return series;
    }

    /**
     * 计算移动平均线
     */
    private double calculateMA(BarSeries series, int period) {
        if (series.getBarCount() < period) return 0.0;
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        return sma.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * 计算成交量移动平均
     */
    private double calculateVolumeMA(BarSeries series, int period) {
        if (series.getBarCount() < period) return 0.0;
        VolumeIndicator volume = new VolumeIndicator(series);
        SMAIndicator sma = new SMAIndicator(volume, period);
        return sma.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * 计算MACD指标
     */
    private MacdIndicators calculateMACD(BarSeries series) {
        MacdIndicators macd = new MacdIndicators();
        if (series.getBarCount() < 26) return macd;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // MACD = EMA(12) - EMA(26)
        EMAIndicator ema12 = new EMAIndicator(closePrice, 12);
        EMAIndicator ema26 = new EMAIndicator(closePrice, 26);

        int endIndex = series.getEndIndex();
        double dif = ema12.getValue(endIndex).doubleValue() - ema26.getValue(endIndex).doubleValue();

        // DEA = EMA(DIF, 9)
        MACDIndicator macdIndicator = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator dea = new EMAIndicator(macdIndicator, 9);
        double deaValue = dea.getValue(endIndex).doubleValue();

        // MACD柱 = 2 * (DIF - DEA)
        double bar = 2 * (dif - deaValue);

        macd.setDif(dif);
        macd.setDea(deaValue);
        macd.setBar(bar);
        macd.setSignal(bar > 0 ? "金叉" : "死叉");

        return macd;
    }

    /**
     * 计算RSI指标
     */
    private double calculateRSI(BarSeries series, int period) {
        if (series.getBarCount() < period + 1) return 50.0;
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);
        return rsi.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * 计算KDJ指标
     */
    private KdjIndicators calculateKDJ(BarSeries series, int period, int kPeriod, int dPeriod) {
        KdjIndicators kdj = new KdjIndicators();
        if (series.getBarCount() < period) return kdj;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        HighPriceIndicator highPrice = new HighPriceIndicator(series);
        LowPriceIndicator lowPrice = new LowPriceIndicator(series);

        StochasticOscillatorKIndicator stochK = new StochasticOscillatorKIndicator(series, period);
        SMAIndicator stochD = new SMAIndicator(stochK, dPeriod);

        int endIndex = series.getEndIndex();
        double k = stochK.getValue(endIndex).doubleValue();
        double d = stochD.getValue(endIndex).doubleValue();
        double j = 3 * k - 2 * d;

        kdj.setK(k);
        kdj.setD(d);
        kdj.setJ(j);

        // 判断信号
        if (k < 20 && d < 20) {
            kdj.setSignal("超卖");
        } else if (k > 80 && d > 80) {
            kdj.setSignal("超买");
        } else if (k > d) {
            kdj.setSignal("金叉");
        } else {
            kdj.setSignal("死叉");
        }

        return kdj;
    }

    /**
     * 计算布林带
     */
    private BollingerBands calculateBollingerBands(BarSeries series, int period, int multiplier) {
        BollingerBands boll = new BollingerBands();
        if (series.getBarCount() < period) return boll;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(new SMAIndicator(closePrice, period));
        StandardDeviationIndicator std = new StandardDeviationIndicator(closePrice, period);
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, std, DecimalNum.valueOf(multiplier));
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, std, DecimalNum.valueOf(multiplier));

        int endIndex = series.getEndIndex();
        double currentPrice = closePrice.getValue(endIndex).doubleValue();
        double upperValue = upper.getValue(endIndex).doubleValue();
        double middleValue = middle.getValue(endIndex).doubleValue();
        double lowerValue = lower.getValue(endIndex).doubleValue();

        boll.setUpper(upperValue);
        boll.setMiddle(middleValue);
        boll.setLower(lowerValue);
        boll.setWidth((upperValue - lowerValue) / middleValue * 100);

        // 判断位置
        if (currentPrice >= upperValue) {
            boll.setPosition("触及上轨");
        } else if (currentPrice <= lowerValue) {
            boll.setPosition("触及下轨");
        } else if (currentPrice > middleValue) {
            boll.setPosition("中轨上方");
        } else {
            boll.setPosition("中轨下方");
        }

        return boll;
    }

    /**
     * 计算ATR（平均真实波幅）
     */
    private double calculateATR(BarSeries series, int period) {
        if (series.getBarCount() < period) return 0.0;
        ATRIndicator atr = new ATRIndicator(series, period);
        return atr.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * 计算OBV（能量潮）
     */
    private double calculateOBV(BarSeries series) {
        if (series.getBarCount() < 2) return 0.0;

        double obv = 0.0;
        for (int i = 1; i <= series.getEndIndex(); i++) {
            Bar currentBar = series.getBar(i);
            Bar previousBar = series.getBar(i - 1);

            if (currentBar.getClosePrice().isGreaterThan(previousBar.getClosePrice())) {
                obv += currentBar.getVolume().doubleValue();
            } else if (currentBar.getClosePrice().isLessThan(previousBar.getClosePrice())) {
                obv -= currentBar.getVolume().doubleValue();
            }
        }

        return obv;
    }

    /**
     * 分析趋势方向
     */
    private String analyzeTrend(TechnicalIndicators indicators) {
        double ma5 = indicators.getMa5();
        double ma10 = indicators.getMa10();
        double ma20 = indicators.getMa20();
        double currentPrice = indicators.getCurrentPrice();

        if (ma5 > ma10 && ma10 > ma20 && currentPrice > ma5) {
            return "强势上涨";
        } else if (ma5 < ma10 && ma10 < ma20 && currentPrice < ma5) {
            return "弱势下跌";
        } else if (ma5 > ma10 && ma10 > ma20) {
            return "多头排列";
        } else if (ma5 < ma10 && ma10 < ma20) {
            return "空头排列";
        } else {
            return "震荡整理";
        }
    }

    // ========== 数据模型 ==========

    @lombok.Data
    public static class TechnicalIndicators {
        // 移动平均线
        private double ma5;
        private double ma10;
        private double ma20;
        private double ma60;

        // MACD
        private MacdIndicators macd;

        // RSI
        private double rsi6;
        private double rsi12;
        private double rsi24;

        // KDJ
        private KdjIndicators kdj;

        // 布林带
        private BollingerBands bollingerBands;

        // ATR
        private double atr;

        // OBV
        private double obv;

        // 成交量均线
        private double volumeMa5;
        private double volumeMa10;

        // 当前价格
        private double currentPrice;

        // 趋势判断
        private String trend;
    }

    @lombok.Data
    public static class MacdIndicators {
        private double dif;
        private double dea;
        private double bar;
        private String signal; // "金叉" or "死叉"
    }

    @lombok.Data
    public static class KdjIndicators {
        private double k;
        private double d;
        private double j;
        private String signal; // "超买"/"超卖"/"金叉"/"死叉"
    }

    @lombok.Data
    public static class BollingerBands {
        private double upper;
        private double middle;
        private double lower;
        private double width; // 带宽百分比
        private String position; // "触及上轨"/"中轨上方"/"中轨下方"/"触及下轨"
    }
}
