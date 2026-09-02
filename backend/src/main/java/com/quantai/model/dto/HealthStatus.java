package com.quantai.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康状态信息
 */
@Data
public class HealthStatus {

    /**
     * 整体状态: UP, DOWN, DEGRADED
     */
    private String status;

    /**
     * 检查时间
     */
    private LocalDateTime timestamp;

    /**
     * 各个服务的健康状态
     */
    private Map<String, ServiceHealth> services;

    public HealthStatus() {
        this.timestamp = LocalDateTime.now();
        this.services = new HashMap<>();
        this.status = "UP";
    }

    /**
     * 添加服务健康检查结果
     */
    public void addService(String name, boolean healthy, String message) {
        ServiceHealth serviceHealth = new ServiceHealth();
        serviceHealth.setHealthy(healthy);
        serviceHealth.setMessage(message);
        services.put(name, serviceHealth);

        // 如果有任何服务不健康，整体状态降级
        if (!healthy) {
            this.status = "DEGRADED";
        }
    }

    /**
     * 单个服务健康状态
     */
    @Data
    public static class ServiceHealth {
        private boolean healthy;
        private String message;
    }
}
