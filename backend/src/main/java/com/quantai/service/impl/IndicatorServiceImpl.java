package com.quantai.service.impl;

import com.quantai.model.vo.KlineVO;
import com.quantai.service.IndicatorService;
import com.quantai.util.IndicatorCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IndicatorServiceImpl implements IndicatorService {

    private final IndicatorCalculator calculator;

    @Override
    public List<KlineVO> fillMAIndicators(List<KlineVO> data) {
        if (data == null || data.isEmpty()) return data;

        List<BigDecimal> ma5List = calculator.calculateMA(data, 5);
        List<BigDecimal> ma10List = calculator.calculateMA(data, 10);
        List<BigDecimal> ma20List = calculator.calculateMA(data, 20);

        for (int i = 0; i < data.size(); i++) {
            KlineVO vo = data.get(i);
            vo.setMa5(ma5List.get(i));
            vo.setMa10(ma10List.get(i));
            vo.setMa20(ma20List.get(i));
        }

        return data;
    }

    @Override
    public List<BigDecimal> calculateMA(List<KlineVO> data, int period) {
        return calculator.calculateMA(data, period);
    }

    @Override
    public BigDecimal getLatestMA(List<KlineVO> data, int period) {
        return calculator.getLatestMA(data, period);
    }

    @Override
    public List<BigDecimal> calculateVolumeRatio(List<KlineVO> data) {
        return calculator.calculateVolumeRatio(data);
    }
}
