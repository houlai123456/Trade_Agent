package com.quantai.service;

import com.quantai.model.vo.KlineVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 技术指标计算服务
 */
public interface IndicatorService {

    /**
     * 计算K线数据的MA均线并填充到KlineVO
     */
    List<KlineVO> fillMAIndicators(List<KlineVO> klineData);

    /**
     * 计算指定周期的移动平均线
     */
    List<BigDecimal> calculateMA(List<KlineVO> data, int period);

    /**
     * 获取最新MA值
     */
    BigDecimal getLatestMA(List<KlineVO> data, int period);

    /**
     * 计算成交量变化率
     */
    List<BigDecimal> calculateVolumeRatio(List<KlineVO> data);
}
