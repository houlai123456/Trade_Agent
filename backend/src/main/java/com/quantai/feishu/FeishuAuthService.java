package com.quantai.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuAuthService {

    private final FeishuConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private String cachedToken;
    private long expireAt = 0;

    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";

    public synchronized String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < expireAt) {
            return cachedToken;
        }
        try {
            Map<String, String> body = Map.of("app_id", config.getAppId(), "app_secret", config.getAppSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String resp = restTemplate.postForObject(TOKEN_URL, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(resp);
            if (root.get("code").asInt() != 0) {
                log.error("飞书token获取失败: {}", resp);
                return null;
            }
            cachedToken = root.get("tenant_access_token").asText();
            expireAt = System.currentTimeMillis() + (root.get("expire").asInt() - 300) * 1000L;
            log.info("飞书token已刷新");
            return cachedToken;
        } catch (Exception e) {
            log.error("飞书鉴权异常", e);
            return null;
        }
    }
}
