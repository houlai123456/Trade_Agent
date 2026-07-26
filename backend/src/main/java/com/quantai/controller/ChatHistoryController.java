package com.quantai.controller;

import com.quantai.common.Result;
import com.quantai.model.entity.ChatMessage;
import com.quantai.model.entity.ChatSession;
import com.quantai.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat-history")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatSessionService sessionService;

    /** 获取会话列表 */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> listSessions() {
        return Result.success(sessionService.listSessions());
    }

    /** 搜索会话 */
    @GetMapping("/sessions/search")
    public Result<List<ChatSession>> searchSessions(@RequestParam String keyword) {
        return Result.success(sessionService.searchSessions(keyword));
    }

    /** 获取会话历史消息 */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        List<ChatMessage> messages = sessionService.getHistory(sessionId);
        if (messages.isEmpty()) {
            return Result.error(404, "会话不存在或已过期");
        }
        return Result.success(messages);
    }

    /** 创建新会话，返回 sessionId */
    @PostMapping("/sessions")
    public Result<Map<String, String>> createSession(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", null);
        String stockCode = body.getOrDefault("stockCode", null);
        String sessionId = sessionService.createSession(title, stockCode);
        return Result.success(Map.of("sessionId", sessionId));
    }

    /** 删除会话 */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
        return Result.success();
    }
}
