package com.quantai.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSession {
    private Long id;
    private String sessionId;
    private String title;
    private String stockCode;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
