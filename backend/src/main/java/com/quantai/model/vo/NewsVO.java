package com.quantai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新闻视图对象
 */
@Data
public class NewsVO {
    private Long id;
    private String title;
    private String summary;
    private String source;
    private String url;
    private String stockCode;
    private String stockName;
    private String sentiment;
    private String affectedStocks;
    private LocalDateTime publishTime;
}
