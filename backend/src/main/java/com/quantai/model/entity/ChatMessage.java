package com.quantai.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private String sessionId;
    private String role;          // user / assistant
    private String content;
    private String messageType;   // chat / analysis / react / trade
    private Integer tokenCount;
    private String metadataJson;  // JSON: {"stockCode":"sh600519","traceId":"xxx"}
    private LocalDateTime createTime;
}
