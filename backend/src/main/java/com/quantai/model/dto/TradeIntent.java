package com.quantai.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 交易指令意图 — AI解析自然语言后的结构化结果
 */
@Data
public class TradeIntent {

    // ===== LLM解析出来的字段 =====
    private Boolean trade;       // JSON: trade = true/false (用包装类避免Jackson+ Lombok的boolean getter命名问题)

    private String action;       // BUY 或 SELL

    @JsonProperty("stock_name")
    private String stockName;    // 股票中文名，如"贵州茅台"

    private Integer quantity;    // 股数

    private BigDecimal price;    // 指定价格（未指定为null）

    // ===== 服务端补充字段 =====
    @JsonProperty("stock_code")
    private String stockCode;    // 带前缀代码，如 sh600519

    @JsonProperty("display_message")
    private String displayMessage; // 给用户看的描述文字

    @JsonProperty("estimated_amount")
    private BigDecimal estimatedAmount; // 预估成交金额

    @JsonProperty("raw_message")
    private String rawMessage;   // 原始用户输入
}
