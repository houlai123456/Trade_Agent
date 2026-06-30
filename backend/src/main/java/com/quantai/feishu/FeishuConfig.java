package com.quantai.feishu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuConfig {
    private String appId;
    private String appSecret;
    private String targetOpenId;
}
