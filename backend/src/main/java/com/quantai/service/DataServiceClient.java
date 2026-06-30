package com.quantai.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.model.entity.StockKline;
import com.quantai.model.entity.StockQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AKShare数据服务客户端
 * 调用Python Flask服务（端口5000）获取A股数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataServiceClient {

    private static final String PYTHON_SERVICE_URL = "http://localhost:5000";
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 30000;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private int consecutiveFailures = 0;
    private long circuitOpenUntil = 0;

    /**
     * 带重试的 HTTP GET 请求
     */
    private String getWithRetry(String url) {
        if (consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD
                && System.currentTimeMillis() < circuitOpenUntil) {
            throw new RuntimeException("断路器已打开，Python服务连续失败" + consecutiveFailures + "次");
        }
        Exception lastEx = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) Thread.sleep(RETRY_DELAY_MS * attempt);
                String result = restTemplate.getForObject(url, String.class);
                consecutiveFailures = 0;
                return result;
            } catch (Exception e) {
                lastEx = e;
            }
        }
        consecutiveFailures++;
        if (consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS;
            log.error("断路器打开！Python服务连续{}次失败，暂停30秒", consecutiveFailures);
        }
        throw new RuntimeException("数据服务请求失败: " + url, lastEx);
    }

    /**
     * 获取A股全部股票列表
     */
    public List<Map<String, Object>> fetchStockList() {
        try {
            String url = PYTHON_SERVICE_URL + "/api/stock/list";
            String resp = getWithRetry(url);
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取股票列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取实时行情
     * @param codes 股票代码列表，如 [sh600519, sz000001]，传空列表返回全部
     */
    public List<StockQuote> fetchQuotes(List<String> codes) {
        try {
            String url = PYTHON_SERVICE_URL + "/api/stock/quote";
            if (codes != null && !codes.isEmpty()) {
                url += "?codes=" + String.join(",", codes);
            }
            String resp = getWithRetry(url);
            List<Map<String, Object>> dataList = extractDataArray(resp);
            return dataList.stream().map(this::mapToQuote).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取行情失败 codes={}", codes, e);
            return Collections.emptyList();
        }
    }

    public StockQuote fetchQuote(String code) {
        List<StockQuote> list = fetchQuotes(Collections.singletonList(code));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 获取K线数据
     * @param code   股票代码，如 600519 或 sh600519
     * @param period daily/weekly/monthly
     * @param limit  数据条数
     */
    public List<StockKline> fetchKline(String code, String period, int limit) {
        try {
            String rawCode = code.replace("sh", "").replace("sz", "");
            String url = String.format("%s/api/stock/kline?code=%s&period=%s&limit=%d",
                    PYTHON_SERVICE_URL, rawCode, period, limit);
            String resp = getWithRetry(url);
            List<Map<String, Object>> dataList = extractDataArray(resp);

            String periodCode = switch (period) {
                case "daily" -> "DAY";
                case "weekly" -> "WEEK";
                case "monthly" -> "MONTH";
                default -> "DAY";
            };

            List<StockKline> result = new ArrayList<>();
            for (Map<String, Object> item : dataList) {
                StockKline kline = new StockKline();
                kline.setCode(code);
                kline.setDate(parseDate((String) item.get("date")));
                kline.setPeriod(periodCode);
                kline.setOpenPrice(toBigDec(item.get("open")));
                kline.setClosePrice(toBigDec(item.get("close")));
                kline.setHighPrice(toBigDec(item.get("high")));
                kline.setLowPrice(toBigDec(item.get("low")));
                kline.setVolume(toLong(item.get("volume")));
                kline.setAmount(toBigDec(item.get("amount")));
                kline.setChangePercent(toBigDec(item.get("change_percent")));
                result.add(kline);
            }
            return result;
        } catch (Exception e) {
            log.error("获取K线失败 code={}", code, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取个股新闻
     */
    public List<Map<String, Object>> fetchNews(String code) {
        try {
            String rawCode = code.replace("sh", "").replace("sz", "");
            String url = PYTHON_SERVICE_URL + "/api/stock/news?code=" + rawCode;
            String resp = getWithRetry(url);
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取新闻失败 code={}", code, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取财务指标
     */
    public List<Map<String, Object>> fetchFinance(String code) {
        try {
            String rawCode = code.replace("sh", "").replace("sz", "");
            String url = PYTHON_SERVICE_URL + "/api/stock/finance?code=" + rawCode;
            String resp = getWithRetry(url);
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取财务数据失败 code={}", code, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取资金流向
     */
    public List<Map<String, Object>> fetchFundFlow(String code) {
        try {
            String rawCode = code.replace("sh", "").replace("sz", "");
            String url = PYTHON_SERVICE_URL + "/api/stock/fund-flow?code=" + rawCode;
            String resp = getWithRetry(url);
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取资金流向失败 code={}", code, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取板块股票行情（带分页和总数）
     * @param boardType main/chiNext/star/bj
     * @param page 页码（从1开始）
     * @param size 每页条数
     * @return Map 包含 data（列表）和 total（总数）
     */
    public Map<String, Object> fetchBoardStocksWithTotal(String boardType, int page, int size) {
        try {
            String url = String.format("%s/api/stock/board/%s?page=%d&size=%d",
                    PYTHON_SERVICE_URL, boardType, page, size);
            String resp = getWithRetry(url);
            if (StrUtil.isBlank(resp)) return Map.of("data", Collections.emptyList(), "total", 0);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode dataNode = root.get("data");
            List<Map<String, Object>> data = dataNode != null && dataNode.isArray()
                    ? objectMapper.convertValue(dataNode, new TypeReference<List<Map<String, Object>>>() {})
                    : Collections.emptyList();
            int total = root.has("total") ? root.get("total").asInt() : data.size();
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("data", data);
            result.put("total", total);
            return result;
        } catch (Exception e) {
            log.error("获取板块行情失败 boardType={}", boardType, e);
            return Map.of("data", Collections.emptyList(), "total", 0);
        }
    }

    /**
     * 获取热点板块排名
     */
    public List<Map<String, Object>> fetchHotBoards() {
        try {
            String resp = getWithRetry(PYTHON_SERVICE_URL + "/api/stock/hot-boards");
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取热点板块失败", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> fetchHotConcepts() {
        try {
            String resp = getWithRetry(PYTHON_SERVICE_URL + "/api/stock/hot-concepts");
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取概念板块失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取指数分时数据
     */
    public List<Map<String, Object>> fetchIndexIntraday(String code) {
        try {
            String url = PYTHON_SERVICE_URL + "/api/index/intraday/" + code;
            String resp = getWithRetry(url);
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取指数分时失败 code={}", code, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取主要指数行情
     */
    public List<Map<String, Object>> fetchIndexQuotes() {
        try {
            String resp = getWithRetry(PYTHON_SERVICE_URL + "/api/index/quote");
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取指数行情失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取市场概况
     */
    public Map<String, Object> fetchMarketOverview() {
        try {
            String resp = getWithRetry(PYTHON_SERVICE_URL + "/api/market/overview");
            if (StrUtil.isBlank(resp)) return Collections.emptyMap();
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) return Collections.emptyMap();
            return objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("获取市场概况失败", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 通过Python数据服务搜索股票（全量A股）
     * @param keyword 股票代码或名称
     * @return [{code, name}]
     */
    public List<Map<String, Object>> searchStock(String keyword) {
        try {
            String url = PYTHON_SERVICE_URL + "/api/stock/search?keyword=" + java.net.URLEncoder.encode(keyword, "UTF-8");
            String resp = getWithRetry(new java.net.URI(url).toString());
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("搜索股票失败 keyword={}", keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取龙虎榜详情
     * @param date 日期，格式 yyyy-MM-dd（可选，不传取今日）
     */
    public List<Map<String, Object>> fetchLhbDetail(String date) {
        try {
            String url = PYTHON_SERVICE_URL + "/api/stock/lhb-detail";
            if (StrUtil.isNotBlank(date)) {
                url += "?date=" + date;
            }
            String resp = getWithRetry(url);
            return extractDataArray(resp);
        } catch (Exception e) {
            log.error("获取龙虎榜失败 date={}", date, e);
            return Collections.emptyList();
        }
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        try {
            String resp = getWithRetry(PYTHON_SERVICE_URL + "/api/health");
            return resp != null && resp.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 解析辅助 ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDataArray(String resp) {
        if (StrUtil.isBlank(resp)) return Collections.emptyList();
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) return Collections.emptyList();
            return objectMapper.convertValue(data, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("解析数据服务响应失败: {}", StrUtil.sub(resp, 0, 200), e);
            return Collections.emptyList();
        }
    }

    private StockQuote mapToQuote(Map<String, Object> item) {
        StockQuote q = new StockQuote();
        q.setCode((String) item.get("code"));
        q.setName((String) item.get("name"));
        q.setCurrentPrice(toBigDec(item.get("current_price")));
        q.setOpenPrice(toBigDec(item.get("open_price")));
        q.setYesterdayClose(toBigDec(item.get("yesterday_close")));
        q.setHighPrice(toBigDec(item.get("high_price")));
        q.setLowPrice(toBigDec(item.get("low_price")));
        q.setVolume(toLong(item.get("volume")));
        q.setAmount(toBigDec(item.get("amount")));
        q.setChangePercent(toBigDec(item.get("change_percent")));
        q.setChangeAmount(toBigDec(item.get("change_amount")));
        q.setTurnoverRate(toBigDec(item.get("turnover_rate")));
        q.setPeRatio(toBigDec(item.get("pe_ratio")));
        q.setAmplitude(toBigDec(item.get("amplitude")));
        q.setUpdateTime(LocalDateTime.now());
        return q;
    }

    private BigDecimal toBigDec(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
            String s = val.toString().trim();
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number n) return n.longValue();
            String s = val.toString().trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String str) {
        if (StrUtil.isBlank(str)) return null;
        try {
            if (str.length() <= 10) return LocalDate.parse(str);
            return LocalDate.parse(str.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
