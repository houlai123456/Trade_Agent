package com.quantai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.config.PromptsConfig;
import com.quantai.model.dto.TradeIntent;
import com.quantai.model.entity.StockInfo;
import com.quantai.model.entity.StockQuote;
import com.quantai.model.entity.TradeOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI交易指令Agent
 * 解析自然语言 → 执行模拟交易
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeAgentService {

    private final StockService stockService;
    private final TradeService tradeService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final PromptsConfig promptsConfig;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String model;

    /**
     * 解析自然语言消息，提取交易意图
     */
    public TradeIntent parseIntent(String message) {
        TradeIntent intent = new TradeIntent();
        intent.setRawMessage(message);

        try {
            // 1. 调用DeepSeek解析
            String content = callDeepSeek(message);
            if (content == null) {
                intent.setTrade(false);
                intent.setDisplayMessage("AI解析服务暂时不可用，请稍后重试");
                return intent;
            }

            content = extractJson(content);
            if (content == null) {
                intent.setTrade(false);
                return intent;
            }

            // 兼容LLM可能输出 is_trade 而非 trade
            content = content.replace("\"is_trade\"", "\"trade\"");

            // 2. 解析JSON
            TradeIntent parsed = objectMapper.readValue(content, TradeIntent.class);
            intent.setTrade(parsed.getTrade());

            if (!Boolean.TRUE.equals(parsed.getTrade())) {
                return intent;
            }

            intent.setAction(parsed.getAction());
            intent.setStockName(parsed.getStockName());
            intent.setQuantity(parsed.getQuantity() != null ? parsed.getQuantity() : 0);
            intent.setPrice(parsed.getPrice());

            // 3. 解析股票名称 → 代码
            String stockCode = resolveStockCode(parsed.getStockName());
            if (stockCode == null) {
                intent.setTrade(false);
                intent.setDisplayMessage("未找到股票【" + parsed.getStockName() + "】，请确认股票名称是否正确");
                return intent;
            }
            intent.setStockCode(stockCode);

            // 4. 获取现价，计算预估金额
            StockQuote quote = stockService.getQuote(stockCode);
            BigDecimal currentPrice = (quote != null) ? quote.getCurrentPrice() : null;

            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                intent.setTrade(false);
                intent.setDisplayMessage("无法获取【" + intent.getStockName() + "】的实时行情，请稍后再试");
                return intent;
            }

            intent.setEstimatedAmount(currentPrice.multiply(BigDecimal.valueOf(intent.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));

            // 5. 构造显示文案
            String actionText = "BUY".equals(intent.getAction()) ? "买入" : "卖出";
            String priceText = intent.getPrice() != null ?
                    "指定价 " + intent.getPrice() + " 元" :
                    "市价 " + currentPrice + " 元";
            intent.setDisplayMessage(String.format("%s %d 股 %s(%s)，%s，预估金额 %.2f 元",
                    actionText, intent.getQuantity(), intent.getStockName(),
                    intent.getStockCode(), priceText, intent.getEstimatedAmount()));

            log.info("解析交易指令成功: {} -> {}", message, intent.getDisplayMessage());

        } catch (Exception e) {
            log.error("解析交易指令失败: message={}", message, e);
            intent.setTrade(false);
            intent.setDisplayMessage("解析交易指令时发生错误，请稍后重试");
        }

        return intent;
    }

    /**
     * 执行已确认的交易指令
     */
    public TradeOrder executeTrade(TradeIntent intent) {
        if (!Boolean.TRUE.equals(intent.getTrade()) || intent.getStockCode() == null) {
            throw new IllegalArgumentException("无效的交易指令");
        }

        int quantity = intent.getQuantity();
        BigDecimal price = intent.getPrice();

        TradeOrder order;
        if ("BUY".equals(intent.getAction())) {
            order = tradeService.buyStock(intent.getStockCode(), quantity, price);
        } else if ("SELL".equals(intent.getAction())) {
            order = tradeService.sellStock(intent.getStockCode(), quantity, price);
        } else {
            throw new IllegalArgumentException("未知交易类型: " + intent.getAction());
        }

        return order;
    }

    /**
     * 直接调用DeepSeek API（避免Spring AI的编码问题）
     */
    private String callDeepSeek(String userMessage) {
        try {
            String url = baseUrl + "/v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", promptsConfig.getSystem().getTradeParser()),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.1
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("DeepSeek API返回异常: {}", response.getStatusCode());
                return null;
            }

            // 解析DeepSeek响应
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText(null);

        } catch (Exception e) {
            log.error("调用DeepSeek失败", e);
            return null;
        }
    }

    /**
     * 从LLM回复中提取JSON内容
     */
    private String extractJson(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.startsWith("{")) {
            int end = text.lastIndexOf("}");
            return end > 0 ? text.substring(0, end + 1) : text;
        }
        int start = text.indexOf("```json");
        if (start >= 0) {
            start += 7;
            int end = text.indexOf("```", start);
            return end > start ? text.substring(start, end).trim() : null;
        }
        start = text.indexOf("```");
        if (start >= 0) {
            start += 3;
            int nl = text.indexOf('\n', start);
            if (nl > start) start = nl + 1;
            int end = text.indexOf("```", start);
            return end > start ? text.substring(start, end).trim() : null;
        }
        return null;
    }

    /**
     * 按股票名称搜索代码
     * 先查本地H2数据库，未找到则从Python数据服务（全量A股）兜底
     */
    private String resolveStockCode(String stockName) {
        if (stockName == null || stockName.isBlank()) return null;

        // 1. 搜索本地H2数据库
        List<StockInfo> stocks = stockService.searchStock(stockName.trim());
        if (!stocks.isEmpty()) return stocks.get(0).getCode();

        // 2. 兜底：从Python数据服务搜索全量A股
        try {
            String encoded = java.net.URLEncoder.encode(stockName.trim(), "UTF-8");
            java.net.URI uri = new java.net.URI("http://127.0.0.1:5000/api/stock/search?keyword=" + encoded);
            String resp = restTemplate.getForObject(uri, String.class);
            if (resp != null) {
                JsonNode root = objectMapper.readTree(resp);
                JsonNode data = root.get("data");
                if (data != null && data.isArray() && data.size() > 0) {
                    String code = data.get(0).get("code").asText();
                    return code.startsWith("6") ? "sh" + code : "sz" + code;
                }
            }
        } catch (Exception e) {
            log.warn("Python数据服务搜索股票失败: {}", e.getMessage());
        }

        return null;
    }
}
