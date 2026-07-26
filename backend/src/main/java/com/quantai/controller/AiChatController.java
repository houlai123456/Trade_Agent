package com.quantai.controller;

import com.quantai.model.dto.ChatRequest;
import com.quantai.security.InputFilter;
import com.quantai.service.AiAnalysisService;
import com.quantai.service.ChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiAnalysisService aiAnalysisService;
    private final ChatSessionService sessionService;

    /**
     * AI对话（流式输出SSE）
     * POST /api/ai/chat/stream
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chatStream(@Valid @RequestBody ChatRequest request) {
        InputFilter.FilterResult filter = InputFilter.check(request.getMessage());
        if (!filter.passed) {
            request.setMessage(filter.sanitizedInput != null ? filter.sanitizedInput : request.getMessage());
        }
        String sessionId = resolveSessionId(request.getSessionId());
        sessionService.saveMessage(sessionId, "user", request.getMessage(), "chat",
                request.getMessage().length(), toMetaJson(request.getStockCode()));

        return aiAnalysisService.chatStream(request.getMessage(), request.getStockCode())
                .doOnNext(chunk -> {
                    String content = chunk.getResult() != null && chunk.getResult().getOutput() != null
                            ? chunk.getResult().getOutput().getContent() : null;
                    if (content != null) {
                        sessionService.saveMessage(sessionId, "assistant", content, "chat", content.length(),
                                toMetaJson(request.getStockCode()));
                    }
                });
    }

    /**
     * AI对话（一次性返回）
     * POST /api/ai/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@Valid @RequestBody ChatRequest request) {
        InputFilter.FilterResult filter = InputFilter.check(request.getMessage());
        if (!filter.passed) {
            request.setMessage(filter.sanitizedInput != null ? filter.sanitizedInput : request.getMessage());
        }
        String response = aiAnalysisService.chat(request.getMessage(), request.getStockCode());
        String sessionId = resolveSessionId(request.getSessionId());

        sessionService.saveMessage(sessionId, "user", request.getMessage(), "chat",
                request.getMessage().length(), toMetaJson(request.getStockCode()));
        sessionService.saveMessage(sessionId, "assistant", response, "chat",
                response.length(), toMetaJson(request.getStockCode()));

        return ResponseEntity.ok(Map.of("response", response, "sessionId", sessionId));
    }

    /** 解析或生成 sessionId */
    private String resolveSessionId(String providedId) {
        if (providedId != null && !providedId.isBlank()) return providedId;
        return "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String toMetaJson(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) return null;
        return "{\"stockCode\":\"" + stockCode + "\"}";
    }
}
