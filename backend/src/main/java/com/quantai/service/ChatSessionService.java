package com.quantai.service;

import com.quantai.mapper.ChatMessageMapper;
import com.quantai.mapper.ChatSessionMapper;
import com.quantai.model.entity.ChatMessage;
import com.quantai.model.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    private static final int MAX_SESSIONS = 50;
    private static final int MAX_HISTORY = 30;

    /** 创建新会话，返回 sessionId */
    @Transactional
    public String createSession(String title, String stockCode) {
        String sessionId = "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setTitle(title != null ? title : "新对话");
        session.setStockCode(stockCode);
        session.setMessageCount(0);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return sessionId;
    }

    /** 保存一条消息（自动创建或更新会话） */
    @Transactional
    public void saveMessage(String sessionId, String role, String content, String messageType,
                            Integer tokenCount, String metadataJson) {
        if (sessionId == null || sessionId.isBlank()) return;

        // 确保会话存在
        ChatSession session = sessionMapper.selectBySessionId(sessionId);
        if (session == null) {
            session = new ChatSession();
            session.setSessionId(sessionId);
            session.setTitle(truncateTitle(content, 30));
            session.setMessageCount(0);
            session.setCreateTime(LocalDateTime.now());
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.insert(session);
        }

        // 插入消息
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setMessageType(messageType != null ? messageType : "chat");
        msg.setTokenCount(tokenCount != null ? tokenCount : 0);
        msg.setMetadataJson(metadataJson);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);

        // 更新会话
        int count = messageMapper.countBySessionId(sessionId);
        sessionMapper.updateInfo(sessionId, truncateTitle(content, 30), count, LocalDateTime.now());
    }

    /** 获取会话列表 */
    public List<ChatSession> listSessions() {
        return sessionMapper.listRecent(MAX_SESSIONS);
    }

    /** 获取会话历史消息 */
    public List<ChatMessage> getHistory(String sessionId) {
        return messageMapper.selectBySessionIdWithLimit(sessionId, MAX_HISTORY);
    }

    /** 搜索会话 */
    public List<ChatSession> searchSessions(String keyword) {
        return sessionMapper.search(keyword);
    }

    /** 删除会话 */
    @Transactional
    public void deleteSession(String sessionId) {
        messageMapper.deleteBySessionId(sessionId);
        sessionMapper.deleteById(sessionMapper.selectBySessionId(sessionId).getId());
    }

    private String truncateTitle(String content, int maxLen) {
        if (content == null) return "新对话";
        String cleaned = content.replace("\n", " ").trim();
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) + "…" : cleaned;
    }
}
