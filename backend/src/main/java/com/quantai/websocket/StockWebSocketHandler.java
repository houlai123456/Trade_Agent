package com.quantai.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket行情推送处理器
 * 用于实时推送行情变化和异动预警
 */
public class StockWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StockWebSocketHandler.class);
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket连接建立，当前连接数：{}", sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket连接关闭，当前连接数：{}", sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端可以发送订阅消息，暂不处理
        log.debug("收到WebSocket消息：{}", message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket传输错误", exception);
        sessions.remove(session);
    }

    /**
     * 广播消息到所有连接
     */
    public void broadcast(Object message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化消息失败", e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.error("发送WebSocket消息失败", e);
                    sessions.remove(session);
                }
            }
        }
    }

    /**
     * 推送行情更新
     */
    public void pushQuoteUpdate(Object quoteData) {
        broadcast(new WebSocketMessage("QUOTE_UPDATE", quoteData));
    }

    /**
     * 推送异动预警
     */
    public void pushAlert(Object alertData) {
        broadcast(new WebSocketMessage("ALERT", alertData));
    }

    /**
     * WebSocket消息封装
     */
    private record WebSocketMessage(String type, Object data) {
    }

    public int getConnectionCount() {
        return sessions.size();
    }
}
