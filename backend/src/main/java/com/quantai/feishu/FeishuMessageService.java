package com.quantai.feishu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuMessageService {

    private final FeishuAuthService authService;
    private final FeishuConfig config;
    private final RestTemplate restTemplate;

    private static final String SEND_URL = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id";

    public boolean sendText(String openId, String text) {
        if (openId == null || openId.isBlank() || text == null || text.isBlank()) return false;
        String token = authService.getAccessToken();
        if (token == null) return false;

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receive_id", openId);
            body.put("msg_type", "text");
            body.put("content", "{\"text\":\"" + escapeJson(text) + "\"}");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            ResponseEntity<String> resp = restTemplate.postForEntity(SEND_URL, new HttpEntity<>(body, headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("飞书消息发送失败, status={}, body={}", resp.getStatusCode(), resp.getBody());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("飞书消息发送失败", e);
            return false;
        }
    }

    public boolean sendToMe(String text) {
        String openId = config.getTargetOpenId();
        if (openId == null || openId.isBlank()) {
            log.debug("飞书 target-open-id 未配置，跳过推送");
            return false;
        }
        return sendText(openId, text);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
