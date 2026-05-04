package com.quantai.service.agent.tool;

import com.quantai.service.AiAnalysisService;
import com.quantai.service.DataServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetNewsSentimentTool implements Tool {

    private final DataServiceClient dataServiceClient;
    private final AiAnalysisService aiAnalysisService;

    @Override
    public String getName() {
        return "get_news_sentiment";
    }

    @Override
    public String getDescription() {
        return "获取股票最新新闻并进行情绪分析（利好/利空/中性）。参数：code=股票代码(如 sh600519)";
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
        List<Map<String, Object>> newsList = dataServiceClient.fetchNews(rawCode);

        if (newsList == null || newsList.isEmpty()) {
            return "未找到【" + code + "】的相关新闻，原因可能是：今日该股没有新闻，或者非交易时段数据源暂不可用";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共找到").append(Math.min(5, newsList.size())).append("条相关新闻（展示前5条）：\n\n");

        int count = 0;
        for (Map<String, Object> item : newsList) {
            if (count >= 5) break;
            String title = (String) item.get("title");
            String summary = (String) item.get("source");
            sb.append("【新闻").append(++count).append("】").append(title).append("\n");
            sb.append("来源：").append(summary != null ? summary : "未知").append("\n");

            // AI情绪分析
            try {
                String sentiment = aiAnalysisService.analyzeSentiment(title, (String) item.get("content"));
                sb.append("情绪分析：").append(sentiment != null ? sentiment : "分析失败").append("\n");
            } catch (Exception e) {
                sb.append("情绪分析：暂不可用\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
