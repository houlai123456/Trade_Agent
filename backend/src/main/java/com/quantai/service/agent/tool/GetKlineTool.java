package com.quantai.service.agent.tool;

import com.quantai.model.vo.KlineVO;
import com.quantai.service.IndicatorService;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetKlineTool implements Tool {

    private final StockService stockService;
    private final IndicatorService indicatorService;

    @Override
    public String getName() {
        return "get_kline";
    }

    @Override
    public String getDescription() {
        return "获取股票K线数据，支持日K/周K/月K，包含MA5/MA10/MA20均线。参数：code=股票代码, period=DAY|WEEK|MONTH(默认DAY), limit=数据条数(默认20)";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("code", "股票代码，如 sh600519");
        params.put("period", "K线周期：DAY(日K)/WEEK(周K)/MONTH(月K)，默认为DAY");
        params.put("limit", "数据条数，默认20");
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String code = (String) args.get("code");
        if (code == null || code.isBlank()) return "错误：缺少code参数";

        String period = args.getOrDefault("period", "DAY").toString();
        int limit = 20;
        if (args.get("limit") != null) {
            try { limit = Integer.parseInt(args.get("limit").toString()); } catch (Exception ignored) {}
        }

        List<KlineVO> klineList = stockService.getKlineData(code, period, limit);
        if (klineList == null || klineList.isEmpty()) {
            return "未获取到【" + code + "】的K线数据";
        }

        klineList = indicatorService.fillMAIndicators(klineList);

        StringBuilder sb = new StringBuilder();
        sb.append("股票：").append(code).append(" K线数据(").append(period).append(") 共").append(klineList.size()).append("条\n\n");

        // 最近5条详情
        int start = Math.max(0, klineList.size() - 5);
        for (int i = start; i < klineList.size(); i++) {
            KlineVO k = klineList.get(i);
            sb.append(k.getDate()).append(" 开盘").append(k.getOpen()).append(" 收盘").append(k.getClose());
            sb.append(" 最高").append(k.getHigh()).append(" 最低").append(k.getLow());
            sb.append(" 量").append(k.getVolume());
            if (k.getChangePercent() != null) sb.append(" 涨跌").append(String.format("%+.2f%%", k.getChangePercent()));
            if (k.getMa5() != null) sb.append(" MA5=").append(String.format("%.2f", k.getMa5()));
            if (k.getMa10() != null) sb.append(" MA10=").append(String.format("%.2f", k.getMa10()));
            if (k.getMa20() != null) sb.append(" MA20=").append(String.format("%.2f", k.getMa20()));
            sb.append("\n");
        }

        // 最新指标汇总
        KlineVO last = klineList.get(klineList.size() - 1);
        sb.append("\n最新指标：\n");
        sb.append("收盘价：").append(last.getClose()).append("\n");
        if (last.getMa5() != null) sb.append("MA5：").append(String.format("%.2f", last.getMa5())).append("\n");
        if (last.getMa10() != null) sb.append("MA10：").append(String.format("%.2f", last.getMa10())).append("\n");
        if (last.getMa20() != null) sb.append("MA20：").append(String.format("%.2f", last.getMa20())).append("\n");

        // 均线判断
        if (last.getMa5() != null) {
            double above5 = last.getClose().subtract(last.getMa5()).divide(last.getMa5(), 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
            sb.append("价格在MA5之").append(above5 >= 0 ? "上" : "下").append(String.format("(%.2f%%)", above5)).append("\n");
        }

        return sb.toString();
    }
}
