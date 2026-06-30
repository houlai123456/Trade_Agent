package com.quantai.service;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * AI智能分析服务
 */
public interface AiAnalysisService {

    /**
     * AI对话（流式输出SSE）
     * @param message   用户消息
     * @param stockCode 关联股票代码（可选）
     * @return 流式响应
     */
    Flux<ChatResponse> chatStream(String message, String stockCode);

    /**
     * AI对话（一次性返回）
     */
    String chat(String message, String stockCode);

    /**
     * 股票行情解读
     * @param code 股票代码
     * @return AI分析报告
     */
    String analyzeStock(String code);

    /**
     * 新闻情绪分析
     * @param title   新闻标题
     * @param content 新闻内容
     * @return 情绪分析结果 json格式
     */
    String analyzeSentiment(String title, String content);

    /**
     * AI财报解读
     * @param code 股票代码
     * @return 财报分析报告（Markdown格式）
     */
    String analyzeFinance(String code);

    /**
     * AI财报对比分析
     * @param code1 第一只股票代码
     * @param code2 第二只股票代码
     * @return 对比分析报告（Markdown格式）
     */
    String analyzeFinanceCompare(String code1, String code2);
}
