package com.quantai.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.quantai.model.entity.StockKline;
import com.quantai.model.entity.StockQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 新浪财经API工具类（已弃用，改用AKShare Python服务）
 * 保留仅作参考
 */
@Slf4j
// @Component（已弃用，使用DataServiceClient替代）
public class SinaStockUtil {

    private static final String SINA_QUOTE_URL = "http://hq.sinajs.cn/list=";
    private static final String SINA_KLINE_URL = "http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/OHLC_GetKLineData";
    private static final String REFERER = "https://finance.sina.com.cn";

    private long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL = 500;

    // ==================== 实时行情 ====================

    public synchronized List<StockQuote> fetchQuotes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        List<StockQuote> result = new ArrayList<>();

        try {
            throttle();

            String codesStr = codes.stream().map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.joining(","));

            HttpResponse response = HttpRequest.get(SINA_QUOTE_URL + codesStr)
                    .header("Referer", REFERER)
                    .timeout(8000)
                    .execute();

            lastRequestTime = System.currentTimeMillis();
            String body = response.body();
            if (StrUtil.isBlank(body)) return result;

            Pattern p = Pattern.compile("var hq_str_(\\w+)=\"([^\"]+)\"");
            Matcher m = p.matcher(body);
            while (m.find()) {
                String code = m.group(1);
                String dataStr = m.group(2);
                StockQuote quote = parseQuote(code, dataStr);
                if (quote != null) result.add(quote);
            }

            if (result.isEmpty()) {
                log.warn("新浪行情返回为空，body前200字符：{}", StrUtil.sub(body, 0, 200));
            }
        } catch (Exception e) {
            log.error("获取新浪行情失败 codes={}", codes, e);
        }
        return result;
    }

    public StockQuote fetchQuote(String code) {
        List<StockQuote> list = fetchQuotes(Collections.singletonList(code));
        return list.isEmpty() ? null : list.get(0);
    }

    // ==================== K线数据 ====================

    /**
     * 获取K线数据
     * @param code   股票代码，如 sh600519
     * @param scale  240=日K, 1440=周K, 10080=月K
     * @param datalen 数据条数
     */
    public List<StockKline> fetchKline(String code, int scale, int datalen) {
        List<StockKline> result = new ArrayList<>();
        try {
            throttle();

            HttpResponse response = HttpRequest.get(SINA_KLINE_URL)
                    .header("Referer", REFERER)
                    .form("symbol", code)
                    .form("scale", scale)
                    .form("datalen", datalen)
                    .timeout(8000)
                    .execute();

            lastRequestTime = System.currentTimeMillis();

            String body = response.body();
            if (StrUtil.isBlank(body)) {
                log.warn("新浪K线返回为空 code={}", code);
                return result;
            }

            // 新浪返回JSON数组：[{"d":"2024-01-02","o":"1720.00","h":"1735.00","l":"1710.00","c":"1728.00","v":"12345678"}]
            JSONArray jsonArray = JSONUtil.parseArray(body);
            String period = scaleToPeriod(scale);

            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                StockKline kline = new StockKline();
                kline.setCode(code);
                kline.setDate(parseDate(item.getStr("d")));
                kline.setPeriod(period);
                kline.setOpenPrice(parseBigDecimal(item.getStr("o")));
                kline.setClosePrice(parseBigDecimal(item.getStr("c")));
                kline.setHighPrice(parseBigDecimal(item.getStr("h")));
                kline.setLowPrice(parseBigDecimal(item.getStr("l")));
                kline.setVolume(parseLong(item.getStr("v")));
                kline.setAmount(parseBigDecimal(item.getStr("a")));
                kline.setChangePercent(parseBigDecimal(item.getStr("p")));
                result.add(kline);
            }

            log.info("新浪K线获取成功 code={}, period={}, 共{}条", code, period, result.size());
        } catch (Exception e) {
            log.error("获取新浪K线失败 code={}", code, e);
        }
        return result;
    }

    /**
     * 一键获取日K、周K、月K
     */
    public Map<String, List<StockKline>> fetchAllPeriodKline(String code) {
        Map<String, List<StockKline>> map = new HashMap<>();
        map.put("DAY", fetchKline(code, 240, 365));
        map.put("WEEK", fetchKline(code, 1440, 200));
        map.put("MONTH", fetchKline(code, 10080, 100));
        return map;
    }

    // ==================== 解析方法 ====================

    private StockQuote parseQuote(String code, String dataStr) {
        if (StrUtil.isBlank(dataStr)) return null;
        String[] fields = dataStr.split(",");
        if (fields.length < 20) return null;

        try {
            StockQuote quote = new StockQuote();
            quote.setCode(code);
            quote.setName(fields[0]);

            BigDecimal yesterdayClose = parseBigDecimal(fields[2]);
            BigDecimal currentPrice = parseBigDecimal(fields[3]);

            quote.setOpenPrice(parseBigDecimal(fields[1]));
            quote.setYesterdayClose(yesterdayClose);
            quote.setCurrentPrice(currentPrice);
            quote.setHighPrice(parseBigDecimal(fields[4]));
            quote.setLowPrice(parseBigDecimal(fields[5]));
            quote.setVolume(parseLong(fields[8]));
            quote.setAmount(parseBigDecimal(fields[9]));

            if (yesterdayClose != null && yesterdayClose.compareTo(BigDecimal.ZERO) > 0 && currentPrice != null) {
                BigDecimal changeAmount = currentPrice.subtract(yesterdayClose);
                BigDecimal changePercent = changeAmount.divide(yesterdayClose, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                quote.setChangeAmount(changeAmount);
                quote.setChangePercent(changePercent);
            }

            String dateStr = fields[6];
            String timeStr = fields[7];
            if (StrUtil.isNotBlank(dateStr) && StrUtil.isNotBlank(timeStr)) {
                try {
                    quote.setTime(LocalDateTime.parse(dateStr + " " + timeStr,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (Exception ignored) {}
            }
            quote.setUpdateTime(LocalDateTime.now());
            return quote;
        } catch (Exception e) {
            log.warn("解析行情失败 code={}", code, e);
            return null;
        }
    }

    private String scaleToPeriod(int scale) {
        return switch (scale) {
            case 240 -> "DAY";
            case 1440 -> "WEEK";
            case 10080 -> "MONTH";
            default -> "DAY";
        };
    }

    private LocalDate parseDate(String str) {
        try { return LocalDate.parse(str); } catch (Exception e) { return null; }
    }

    private BigDecimal parseBigDecimal(String str) {
        try {
            str = str.trim();
            if (StrUtil.isBlank(str) || "-".equals(str)) return null;
            return new BigDecimal(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String str) {
        try {
            str = str.trim();
            if (StrUtil.isBlank(str) || "-".equals(str)) return null;
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void throttle() {
        long now = System.currentTimeMillis();
        long waitTime = MIN_REQUEST_INTERVAL - (now - lastRequestTime);
        if (waitTime > 0) {
            try { Thread.sleep(waitTime); } catch (InterruptedException ignored) {}
        }
    }
}
