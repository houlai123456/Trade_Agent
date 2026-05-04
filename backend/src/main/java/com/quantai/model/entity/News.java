package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新闻舆情
 */
@Data
@TableName("news")
public class News {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 新闻标题 */
    private String title;

    /** 新闻内容摘要 */
    private String summary;

    /** 全文内容 */
    private String content;

    /** 来源 */
    private String source;

    /** 新闻链接 */
    private String url;

    /** 关联股票代码 */
    private String stockCode;

    /** 关联股票名称 */
    private String stockName;

    /** 情绪标签：POSITIVE-利好，NEGATIVE-利空，NEUTRAL-中性 */
    private String sentiment;

    /** 情绪分析得分（-1到1） */
    private Double sentimentScore;

    /** 影响的股票或板块（JSON数组） */
    private String affectedStocks;

    /** 发布时间 */
    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
