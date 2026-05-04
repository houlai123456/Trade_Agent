package com.quantai.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI对话请求
 */
@Data
public class ChatRequest {
    /** 用户消息 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 关联的股票代码（可选） */
    private String stockCode;

    /** 对话历史ID（可选，用于上下文） */
    private String sessionId;
}
