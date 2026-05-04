package com.quantai.service.agent.tool;

import com.quantai.service.DataServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class GetFundFlowTool implements Tool {

    private final DataServiceClient dataServiceClient;

    @Override
    public String getName() {
        return "get_fund_flow";
    }

    @Override
    public String getDescription() {
        return "获取股票近5个交易日资金流向，包括主力、超大单、大单、中单、小单的净流入金额和占比。参数：code=股票代码(如 sh600519)";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("code", "股票代码，如 sh600519");
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String code = (String) args.get("code");
        if (code == null || code.isBlank()) return "错误：缺少code参数";

        String rawCode = code.replace("sh", "").replace("sz", "");
        List<Map<String, Object>> flowList = dataServiceClient.fetchFundFlow(rawCode);

        if (flowList == null || flowList.isEmpty()) {
            return "未获取到【" + code + "】的资金流向数据";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("股票：").append(code).append(" 近5日资金流向\n\n");

        // 格式化表头
        sb.append(String.format("%-12s %-14s %-14s %-14s", "日期", "主力净流入", "超大单净流入", "大单净流入")).append("\n");

        for (int i = 0; i < Math.min(5, flowList.size()); i++) {
            Map<String, Object> day = flowList.get(i);
            String date = String.valueOf(day.getOrDefault("date", ""));
            double mainForce = toDouble(day.get("主力净流入-净额"));
            double superLarge = toDouble(day.get("超大单净流入-净额"));
            double large = toDouble(day.get("大单净流入-净额"));

            sb.append(String.format("%-12s %+12.2f %+12.2f %+12.2f",
                    date.length() > 8 ? date.substring(5) : date,
                    mainForce / 10000, superLarge / 10000, large / 10000)).append("\n");
        }

        // 汇总
        if (!flowList.isEmpty()) {
            double totalMain = 0;
            for (Map<String, Object> day : flowList) {
                totalMain += toDouble(day.get("主力净流入-净额"));
            }
            sb.append("\n近5日主力净流入合计：").append(String.format("%+.2f万元", totalMain / 10000));
            sb.append(totalMain > 0 ? "（主力资金整体流入）" : "（主力资金整体流出）");
        }

        return sb.toString();
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }
}
