package com.quantai.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * LLM 调用服务接口
 * 提供超时控制、熔断降级、重试等能力
 */
public interface LlmService {

    /**
     * 调用 LLM（带超时控制和降级）
     *
     * @param messages 消息列表
     * @param timeoutSeconds 超时时间（秒）
     * @return LLM 响应内容
     * @throws TimeoutException 超时异常
     */
    String callWithTimeout(List<Message> messages, int timeoutSeconds) throws TimeoutException;

    /**
     * 调用 LLM（使用默认超时30秒）
     *
     * @param messages 消息列表
     * @return LLM 响应内容
     */
    String call(List<Message> messages);

    /**
     * 降级响应
     *
     * @param messages 原始请求消息
     * @param cause 失败原因
     * @return 降级响应内容
     */
    String fallback(List<Message> messages, Throwable cause);
}
