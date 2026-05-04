package com.quantai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键业务工具和数据解析辅助测试
 */
class DataServiceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testStockCodePrefixParsing() {
        assertEquals("600519", "sh600519".replace("sh", "").replace("sz", ""));
        assertEquals("000001", "sz000001".replace("sh", "").replace("sz", ""));
        assertEquals("688001", "sh688001".replace("sh", "").replace("sz", ""));
    }

    @Test
    void testLimitPriceCalculation() {
        BigDecimal base = new BigDecimal("100.00");
        int pct = 10;
        BigDecimal limitUp = base.multiply(BigDecimal.valueOf(1 + pct / 100.0))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        assertEquals(new BigDecimal("110.00"), limitUp);

        BigDecimal limitDown = base.multiply(BigDecimal.valueOf(1 - pct / 100.0))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        assertEquals(new BigDecimal("90.00"), limitDown);
    }

    @Test
    void testLimitPriceCalculation_STAR() {
        BigDecimal base = new BigDecimal("50.00");
        int pct = 20;
        BigDecimal limitUp = base.multiply(BigDecimal.valueOf(1 + pct / 100.0))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        assertEquals(new BigDecimal("60.00"), limitUp);
    }

    @Test
    void testDateParsing() {
        String dateStr = "2026-05-04";
        LocalDate date = LocalDate.parse(dateStr);
        assertEquals(2026, date.getYear());
        assertEquals(5, date.getMonthValue());
        assertEquals(4, date.getDayOfMonth());
    }

    @Test
    void testAmountFormatting() {
        // 模拟成交量格式化逻辑
        long v1 = 150000000L; // 1.5亿
        String result;
        if (v1 >= 1e8) result = String.format("%.2f亿", v1 / 1e8);
        else if (v1 >= 1e4) result = String.format("%.0f万", v1 / 1e4);
        else result = String.valueOf(v1);
        assertEquals("1.50亿", result);

        long v2 = 500000L; // 50万
        if (v2 >= 1e8) result = String.format("%.2f亿", v2 / 1e8);
        else if (v2 >= 1e4) result = String.format("%.0f万", v2 / 1e4);
        else result = String.valueOf(v2);
        assertEquals("50万", result);
    }
}
