package com.quantai.service.impl;

import com.quantai.config.PromptsConfig;
import com.quantai.model.entity.AgentTrace;
import com.quantai.model.entity.StockQuote;
import com.quantai.service.AgentTraceService;
import com.quantai.service.AiAnalysisService;
import com.quantai.service.DataServiceClient;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final OpenAiChatModel chatModel;
    private final StockService stockService;
    private final DataServiceClient dataServiceClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentTraceService traceService;
    private final PromptsConfig promptsConfig;

    // ========== Redis 缓存 Key 前缀 & TTL ==========
    private static final String CACHE_PREFIX = "ai:";
    private static final long TTL_STOCK = 300;        // 行情分析 5分钟
    private static final long TTL_FINANCE = 3600;     // 财报解读 1小时
    private static final long TTL_SENTIMENT = 3600;   // 情感分析 1小时

    private String cacheGet(String key) {
        try {
            return (String) redisTemplate.opsForValue().get(CACHE_PREFIX + key);
        } catch (Exception e) {
            log.warn("Redis读取失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void cachePut(String key, String value, long ttlSec) {
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + key, value, ttlSec, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Redis不可用时静默降级
        }
    }

    @Override
    public Flux<ChatResponse> chatStream(String message, String stockCode) {
        long start = System.currentTimeMillis();
        List<Message> messages = buildMessages(message, stockCode);
        Prompt prompt = new Prompt(messages);
        return chatModel.stream(prompt)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - start;
                    AgentTrace trace = new AgentTrace();
                    trace.setTraceType("CHAT_STREAM");
                    trace.setStockCode(stockCode);
                    trace.setUserMessage(truncate(message, 100));
                    trace.setStartTime(LocalDateTime.now().minusNanos(java.time.Duration.ofMillis(duration).toNanos()));
                    trace.setEndTime(LocalDateTime.now());
                    trace.setDurationMs(duration);
                    trace.setSuccess(signalType != reactor.core.publisher.SignalType.ON_ERROR);
                    trace.setPromptTokens(message.length());
                    if (signalType == reactor.core.publisher.SignalType.ON_ERROR) {
                        trace.setErrorMessage("流式输出异常终止");
                    }
                    traceService.record(trace);
                });
    }

    @Override
    public String chat(String message, String stockCode) {
        AgentTrace trace = new AgentTrace();
        trace.setTraceType("CHAT");
        trace.setStockCode(stockCode);
        trace.setUserMessage(truncate(message, 100));
        trace.setStartTime(LocalDateTime.now());
        trace.setPromptTokens(message.length());

        long start = System.currentTimeMillis();
        try {
            List<Message> messages = buildMessages(message, stockCode);
            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatModel.call(prompt);
            String result = response.getResult().getOutput().getContent();

            trace.setEndTime(LocalDateTime.now());
            trace.setDurationMs(System.currentTimeMillis() - start);
            trace.setSuccess(true);
            trace.setCompletionTokens(result != null ? result.length() : 0);

            return result;
        } catch (Exception e) {
            trace.setEndTime(LocalDateTime.now());
            trace.setDurationMs(System.currentTimeMillis() - start);
            trace.setSuccess(false);
            trace.setErrorMessage(e.getMessage());
            trace.setSnapshotRawResponse(truncate(e.getMessage(), 500));
            throw e;
        } finally {
            traceService.record(trace);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(已截断)";
    }

    @Override
    public String analyzeStock(String code) {
        String cacheKey = "stock:" + code;
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            log.debug("Redis命中缓存 analyzeStock({})", code);
            return cached;
        }

        StockQuote quote = stockService.getQuote(code);
        if (quote == null || quote.getCurrentPrice() == null) {
            return "未能获取到股票【" + code + "】的实时行情数据。\n" +
                    "可能原因：\n" +
                    "1. 股票代码格式不正确（上证用sh前缀，深证用sz前缀，如 sh600519）\n" +
                    "2. 当前为非交易时段，新浪API无数据返回\n" +
                    "3. 网络连接问题";
        }

        String marketContext = buildMarketContext(quote);
        String prompt = promptsConfig.getTemplates().getStockAnalysis()
                .replace("{fetchTime}", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .replace("{marketContext}", marketContext)
                .replace("{stockName}", quote.getName() != null ? quote.getName() : "")
                .replace("{stockCode}", quote.getCode());

        String result = chat(prompt, code);
        cachePut(cacheKey, result, TTL_STOCK);
        return result;
    }

    @Override
    public String analyzeFinance(String code) {
        String cacheKey = "finance:" + code;
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            log.debug("Redis命中缓存 analyzeFinance({})", code);
            return cached;
        }

        StockQuote quote = stockService.getQuote(code);
        String stockName = quote != null && quote.getName() != null ? quote.getName() : code;

        java.util.List<java.util.Map<String, Object>> financeList = dataServiceClient.fetchFinance(code);
        if (financeList == null || financeList.isEmpty()) {
            return "未能获取到股票【" + stockName + "(" + code + ")】的财务数据，请稍后重试。";
        }

        int endIdx = Math.min(financeList.size(), 20);
        int startIdx = Math.max(0, endIdx - 4);
        java.util.List<java.util.Map<String, Object>> recentData = financeList.subList(startIdx, endIdx);

        StringBuilder financeText = new StringBuilder();
        for (java.util.Map<String, Object> row : recentData) {
            financeText.append("报告期：").append(row.getOrDefault("报告期", "")).append("\n");
            String[] keys = {
                "营业总收入", "营业总收入同比增长", "净利润", "净利润同比增长",
                "基本每股收益", "每股净资产", "净资产收益率",
                "资产负债率", "流动比率", "速动比率",
                "毛利率", "净利率",
                "经营活动现金流量净额", "投资活动现金流量净额", "筹资活动现金流量净额",
                "研发费用", "研发费用占营业总收入比例",
                "存货周转率", "应收账款周转率", "总资产周转率",
                "营业总收入环比增长率", "净利润环比增长率"
            };
            for (String key : keys) {
                Object val = row.get(key);
                if (val != null && !val.toString().isEmpty()) {
                    financeText.append("  ").append(key).append("：").append(val).append("\n");
                }
            }
            financeText.append("\n");
        }

        if (financeText.length() == 0) {
            return "股票【" + stockName + "(" + code + ")】的财务数据为空。";
        }

        String prompt = promptsConfig.getTemplates().getFinanceAnalysis()
                .replace("{stockName}", stockName)
                .replace("{stockCode}", code)
                .replace("{financeData}", financeText.toString());

        String result = chat(prompt, code);
        cachePut(cacheKey, result, TTL_FINANCE);
        return result;
    }

    @Override
    public String analyzeFinanceCompare(String code1, String code2) {
        String cacheKey = "compare:" + code1 + ":" + code2;
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            log.debug("Redis命中缓存 analyzeFinanceCompare({}, {})", code1, code2);
            return cached;
        }

        StockQuote q1 = stockService.getQuote(code1);
        StockQuote q2 = stockService.getQuote(code2);
        String n1 = q1 != null && q1.getName() != null ? q1.getName() : code1;
        String n2 = q2 != null && q2.getName() != null ? q2.getName() : code2;

        String f1 = buildFinanceSummary(code1, n1);
        String f2 = buildFinanceSummary(code2, n2);
        if (f1 == null || f2 == null) {
            return "未能获取财务数据，请检查股票代码。";
        }

        String prompt = promptsConfig.getTemplates().getFinanceCompare()
                .replace("{stockName1}", n1)
                .replace("{stockCode1}", code1)
                .replace("{financeData1}", f1)
                .replace("{stockName2}", n2)
                .replace("{stockCode2}", code2)
                .replace("{financeData2}", f2);

        String result = chat(prompt, code1);
        cachePut(cacheKey, result, TTL_FINANCE);
        return result;
    }

    /** 提取财报关键数据摘要，供对比使用 */
    private String buildFinanceSummary(String code, String name) {
        java.util.List<java.util.Map<String, Object>> list = dataServiceClient.fetchFinance(code);
        if (list == null || list.isEmpty()) return null;

        int idx = Math.min(list.size() - 1, 3);
        StringBuilder sb = new StringBuilder();
        java.util.Map<String, Object> latest = list.get(idx);
        sb.append("报告期：").append(latest.getOrDefault("报告期", "")).append("\n");
        String[] keys = {
            "营业总收入", "营业总收入同比增长", "净利润", "净利润同比增长",
            "基本每股收益", "每股净资产", "净资产收益率",
            "资产负债率", "毛利率", "净利率",
            "经营活动现金流量净额", "研发费用"
        };
        for (String k : keys) {
            Object v = latest.get(k);
            if (v != null && !v.toString().isEmpty()) {
                sb.append(k).append("：").append(v).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String analyzeSentiment(String title, String content) {
        String cacheKey = "sentiment:" + md5((title != null ? title : "") + (content != null ? content : ""));
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            log.debug("Redis命中缓存 analyzeSentiment");
            return cached;
        }

        String prompt = promptsConfig.getTemplates().getSentimentAnalysis()
                .replace("{title}", title != null ? title : "")
                .replace("{content}", content != null ? content : "");
        String result = chat(prompt, null);
        cachePut(cacheKey, result, TTL_SENTIMENT);
        return result;
    }

    private static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private List<Message> buildMessages(String message, String stockCode) {
        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(promptsConfig.getSystem().getChatAssistant()));

        if (stockCode != null && !stockCode.trim().isEmpty()) {
            StockQuote quote = stockService.getQuote(stockCode);
            if (quote != null && quote.getCurrentPrice() != null) {
                String context = buildMarketContext(quote);
                String template = promptsConfig.getSystem().getStockContextInject();
                String ctxMsg = template
                        .replace("{fetchTime}", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .replace("{marketData}", context);
                messages.add(new SystemMessage(ctxMsg));
            }
        }

        messages.add(new UserMessage(message));
        return messages;
    }

    private String buildMarketContext(StockQuote quote) {
        StringBuilder sb = new StringBuilder();
        sb.append("股票代码：").append(quote.getCode()).append("\n");
        sb.append("股票名称：").append(nullToEmpty(quote.getName())).append("\n");
        sb.append("当前价格：").append(nvl(quote.getCurrentPrice())).append("\n");
        sb.append("今日开盘：").append(nvl(quote.getOpenPrice())).append("\n");
        sb.append("昨日收盘：").append(nvl(quote.getYesterdayClose())).append("\n");
        sb.append("今日最高：").append(nvl(quote.getHighPrice())).append("\n");
        sb.append("今日最低：").append(nvl(quote.getLowPrice())).append("\n");
        sb.append("涨跌幅：").append(nvl(quote.getChangePercent())).append("%\n");
        sb.append("涨跌额：").append(nvl(quote.getChangeAmount())).append("\n");
        sb.append("成交量：").append(quote.getVolume() != null ? quote.getVolume() : 0).append("股\n");
        sb.append("成交额：").append(nvl(quote.getAmount())).append("元\n");
        if (quote.getTime() != null) {
            sb.append("数据时间：").append(quote.getTime()).append("\n");
        }
        sb.append("数据来源：新浪财经免费API（实时数据）");
        return sb.toString();
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private double nvl(BigDecimal val) {
        return val != null ? val.doubleValue() : 0.0;
    }
}
