package com.quantai.controller;

import com.quantai.model.dto.ChatRequest;
import com.quantai.service.AiAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiAnalysisService aiAnalysisService;

    /**
     * AI对话（流式输出SSE）
     * POST /api/ai/chat/stream
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chatStream(@Valid @RequestBody ChatRequest request) {
        return aiAnalysisService.chatStream(request.getMessage(), request.getStockCode());
    }

    /**
     * AI对话（一次性返回）
     * POST /api/ai/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@Valid @RequestBody ChatRequest request) {
        String response = aiAnalysisService.chat(request.getMessage(), request.getStockCode());
        return ResponseEntity.ok(Map.of("response", response));
    }

    /**
     * 股票行情解读
     * GET /api/ai/analyze/{code}
     */
    @GetMapping("/analyze/{code}")
    public ResponseEntity<Map<String, String>> analyzeStock(@PathVariable String code) {
        String analysis = aiAnalysisService.analyzeStock(code);
        return ResponseEntity.ok(Map.of("code", code, "analysis", analysis));
    }
}
