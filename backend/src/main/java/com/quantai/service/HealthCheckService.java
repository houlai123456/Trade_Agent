package com.quantai.service;

import com.quantai.model.dto.HealthStatus;

/**
 * 健康检查服务
 * 定期检查依赖服务的健康状态
 */
public interface HealthCheckService {

    /**
     * 检查 Python 数据服务是否健康
     * @return true-健康，false-异常
     */
    boolean isPythonServiceHealthy();

    /**
     * 检查 Redis 是否健康
     * @return true-健康，false-异常
     */
    boolean isRedisHealthy();

    /**
     * 检查 Qdrant 向量库是否健康
     * @return true-健康，false-异常
     */
    boolean isQdrantHealthy();

    /**
     * 获取所有服务的健康状态摘要
     * @return 健康状态信息
     */
    HealthStatus getOverallHealth();
}
