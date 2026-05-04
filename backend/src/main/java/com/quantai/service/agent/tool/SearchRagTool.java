package com.quantai.service.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRagTool implements Tool {

    private static final String PYTHON_SERVICE_URL = "http://localhost:5000";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "search_news_knowledge";
    }

    @Override
    public String getDescription() {
        return "搜索新闻知识库并回答问题。适用于询问股票新闻、消息面、近期事件等。参数：question=你的问题(如 '茅台最近的新闻')";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("question", "问题内容，如 '贵州茅台最近有什么利空消息'");
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String question = (String) args.get("question");
        if (question == null || question.isBlank()) {
            return "错误：缺少 question 参数";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("question", question);
            body.put("top_k", 5);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = PYTHON_SERVICE_URL + "/api/rag/ask";
            String resp = restTemplate.postForObject(url, request, String.class);

            if (resp == null || resp.isBlank()) {
                return "知识库查询无返回，可能是 Qdrant 服务未运行";
            }

            JsonNode root = objectMapper.readTree(resp);
            if (!root.has("success") || !root.get("success").asBoolean()) {
                return "知识库查询失败：" + root.path("error").asText("未知错误");
            }

            JsonNode data = root.get("data");
            if (data == null) {
                return "知识库查询返回为空";
            }

            String answer = data.path("answer").asText("");
            JsonNode sources = data.get("sources");

            StringBuilder sb = new StringBuilder();
            sb.append("知识库回答：").append(answer);

            if (sources != null && sources.isArray() && sources.size() > 0) {
                sb.append("\n\n参考来源：");
                for (JsonNode src : sources) {
                    String stockName = src.path("stock_name").asText("");
                    String title = src.path("title").asText("");
                    double score = src.path("score").asDouble();
                    if (!stockName.isBlank() && !title.isBlank()) {
                        sb.append("\n- ").append(stockName).append("：").append(title)
                                .append("（相似度 ").append(String.format("%.2f", score)).append("）");
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("知识库查询失败 question={}", question, e);
            return "知识库查询暂不可用（" + e.getMessage() + "）";
        }
    }
}
