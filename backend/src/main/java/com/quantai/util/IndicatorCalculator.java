package com.quantai.util;

import com.quantai.model.vo.KlineVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 技术指标计算工具类
 * 纯Java实现，不依赖TA-Lib等第三方库
 */
@Slf4j
@Component
public class IndicatorCalculator {

    /**
     * 计算移动平均线（MA）
     * @param data    K线数据列表（按时间升序排列）
     * @param period  周期（5、10、20等）
     * @return MA值列表，前period-1天为null
     */
    public List<BigDecimal> calculateMA(List<KlineVO> data, int period) {
        if (data == null || data.isEmpty() || period <= 0) {
            return new ArrayList<>();
        }

        List<BigDecimal> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            if (i < period - 1) {
                result.add(null);
            } else {
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = i - period + 1; j <= i; j++) {
                    sum = sum.add(data.get(j).getClose());
                }
                result.add(sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    /**
     * 计算涨跌幅（相比前一日）
     */
    public List<BigDecimal> calculateChangePercent(List<KlineVO> data) {
        List<BigDecimal> result = new ArrayList<>();
        if (data == null || data.isEmpty()) return result;

        result.add(null); // 第一天没有涨跌幅
        for (int i = 1; i < data.size(); i++) {
            BigDecimal prevClose = data.get(i - 1).getClose();
            BigDecimal currClose = data.get(i).getClose();
            if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0 && currClose != null) {
                BigDecimal change = currClose.subtract(prevClose)
                        .divide(prevClose, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                result.add(change);
            } else {
                result.add(null);
            }
        }
        return result;
    }

    /**
     * 计算成交量变化率（相比前一日）
     */
    public List<BigDecimal> calculateVolumeRatio(List<KlineVO> data) {
        List<BigDecimal> result = new ArrayList<>();
        if (data == null || data.isEmpty()) return result;

        result.add(null);
        for (int i = 1; i < data.size(); i++) {
            Long prevVol = data.get(i - 1).getVolume();
            Long currVol = data.get(i).getVolume();
            if (prevVol != null && prevVol > 0 && currVol != null) {
                BigDecimal ratio = BigDecimal.valueOf(currVol)
                        .divide(BigDecimal.valueOf(prevVol), 2, RoundingMode.HALF_UP);
                result.add(ratio);
            } else {
                result.add(null);
            }
        }
        return result;
    }

    /**
     * 获取最新的MA值（用于实时行情显示）
     */
    public BigDecimal getLatestMA(List<KlineVO> data, int period) {
        List<BigDecimal> mas = calculateMA(data, period);
        if (mas.isEmpty()) return null;
        return mas.get(mas.size() - 1);
    }
}
