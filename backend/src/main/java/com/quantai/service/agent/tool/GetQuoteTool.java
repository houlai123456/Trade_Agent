package com.quantai.service.agent.tool;

import com.quantai.model.entity.StockQuote;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetQuoteTool implements Tool {

    private final StockService stockService;

    @Override
    public String getName() {
        return "get_quote";
    }

    @Override
    public String getDescription() {
        return "获取股票实时行情，包括当前价、开盘价、昨收、最高、最低、涨跌幅、成交量、成交额。参数：code=股票代码(如 sh600519)";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("code", "股票代码，如 sh600519 或 sz000001");
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String code = (String) args.get("code");
        if (code == null || code.isBlank()) return "错误：缺少code参数";

        StockQuote quote = stockService.getQuote(code);
        if (quote == null || quote.getCurrentPrice() == null) {
            return "未获取到股票【" + code + "】的行情数据";
        }

        return String.format(
                "股票：%s(%s)\n当前价：%.2f\n开盘：%.2f\n昨收：%.2f\n最高：%.2f\n最低：%.2f\n涨跌幅：%+.2f%%\n涨跌额：%+.2f\n成交量：%d股\n成交额：%.2f元",
                quote.getName() != null ? quote.getName() : code,
                quote.getCode(),
                quote.getCurrentPrice(),
                nvl(quote.getOpenPrice()),
                nvl(quote.getYesterdayClose()),
                nvl(quote.getHighPrice()),
                nvl(quote.getLowPrice()),
                nvl(quote.getChangePercent()),
                nvl(quote.getChangeAmount()),
                quote.getVolume() != null ? quote.getVolume() : 0,
                nvl(quote.getAmount())
        );
    }

    private double nvl(java.math.BigDecimal v) {
        return v != null ? v.doubleValue() : 0.0;
    }
}
