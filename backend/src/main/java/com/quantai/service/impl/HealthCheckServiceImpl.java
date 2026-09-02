package com.quantai.service.impl;

import com.quantai.annotation.DistributedLock;
import com.quantai.feishu.FeishuMessageService;
import com.quantai.model.dto.HealthStatus;
import com.quantai.service.DataServiceClient;
import com.quantai.service.HealthCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class HealthCheckServiceImpl implements HealthCheckService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DataServiceClient dataServiceClient;

    @Autowired(required = false)
    private FeishuMessageService feishuMessageService;

    @Value("${rag.qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // 健康状态缓存（避免频繁检查）
    private volatile boolean pythonServiceHealthy = true;
    private volatile boolean redisHealthy = true;
    private volatile boolean qdrantHealthy = true;
    private volatile long lastCheckTime = 0;

    // 数据源异常计数（连续失败次数）
    private volatile int pythonFailureCount = 0;
    private volatile int qdrantFailureCount = 0;

    // 告警阈值和冷却时间
    private static final int FAILURE_THRESHOLD = 3;  // 连续失败3次触发告警
    private static final long ALERT_COOLDOWN_MS = 300000;  // 5分钟内不重复告警
    private volatile long lastPythonAlertTime = 0;
    private volatile long lastQdrantAlertTime = 0;

    @Override
    public boolean isPythonServiceHealthy() {
        return pythonServiceHealthy;
    }

    @Override
    public boolean isRedisHealthy() {
        return redisHealthy;
    }

    @Override
    public boolean isQdrantHealthy() {
        return qdrantHealthy;
    }

    @Override
    public HealthStatus getOverallHealth() {
        HealthStatus health = new HealthStatus();

        health.addService("python-data-service", pythonServiceHealthy,
                pythonServiceHealthy ? "运行正常" : "服务异常");

        health.addService("redis", redisHealthy,
                redisHealthy ? "连接正常" : "连接失败");

        health.addService("qdrant", qdrantHealthy,
                qdrantHealthy ? "运行正常" : "服务异常");

        return health;
    }

    /**
     * 定期健康检查（每30秒）
     */
    @Scheduled(fixedRate = 30000, initialDelay = 5000)
    @DistributedLock(key = "task:healthCheck", waitTime = 1, leaseTime = 30, failMessage = "健康检查任务已在其他节点执行")
    public void scheduledHealthCheck() {
        log.debug("开始定期健康检查");
        lastCheckTime = System.currentTimeMillis();

        // 检查 Python 数据服务
        checkPythonService();

        // 检查 Redis
        checkRedis();

        // 检查 Qdrant
        checkQdrant();

        // 记录健康状态
        if (!pythonServiceHealthy || !redisHealthy || !qdrantHealthy) {
            log.warn("服务健康检查发现异常 - Python: {}, Redis: {}, Qdrant: {}",
                    pythonServiceHealthy, redisHealthy, qdrantHealthy);
        }
    }

    private void checkPythonService() {
        try {
            boolean healthy = dataServiceClient.healthCheck();

            if (healthy) {
                // 服务恢复
                if (!pythonServiceHealthy) {
                    log.info("Python 数据服务已恢复正常");
                    sendRecoveryAlert("Python 数据服务", pythonFailureCount);
                }
                pythonServiceHealthy = true;
                pythonFailureCount = 0;
            } else {
                // 服务异常
                pythonServiceHealthy = false;
                pythonFailureCount++;

                // 达到阈值且未在冷却期内，发送告警
                if (pythonFailureCount >= FAILURE_THRESHOLD) {
                    long now = System.currentTimeMillis();
                    if (now - lastPythonAlertTime > ALERT_COOLDOWN_MS) {
                        sendDataSourceAlert("Python 数据服务", pythonFailureCount, "健康检查返回 false");
                        lastPythonAlertTime = now;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Python 数据服务健康检查失败: {}", e.getMessage());
            pythonServiceHealthy = false;
            pythonFailureCount++;

            // 达到阈值且未在冷却期内，发送告警
            if (pythonFailureCount >= FAILURE_THRESHOLD) {
                long now = System.currentTimeMillis();
                if (now - lastPythonAlertTime > ALERT_COOLDOWN_MS) {
                    sendDataSourceAlert("Python 数据服务", pythonFailureCount, e.getMessage());
                    lastPythonAlertTime = now;
                }
            }
        }
    }

    private void checkRedis() {
        if (redisTemplate == null) {
            redisHealthy = true;  // Redis 未配置，视为健康
            return;
        }

        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            String pong = connection.ping();
            redisHealthy = "PONG".equals(pong);
            connection.close();
        } catch (Exception e) {
            log.error("Redis 健康检查失败: {}", e.getMessage());
            redisHealthy = false;
        }
    }

    private void checkQdrant() {
        try {
            String healthUrl = qdrantUrl + "/healthz";
            restTemplate.getForObject(healthUrl, String.class);

            // 服务恢复
            if (!qdrantHealthy) {
                log.info("Qdrant 向量库已恢复正常");
                sendRecoveryAlert("Qdrant 向量库", qdrantFailureCount);
            }
            qdrantHealthy = true;
            qdrantFailureCount = 0;
        } catch (Exception e) {
            log.debug("Qdrant 健康检查失败（可能未启动）: {}", e.getMessage());
            qdrantHealthy = false;
            qdrantFailureCount++;

            // 达到阈值且未在冷却期内，发送告警
            if (qdrantFailureCount >= FAILURE_THRESHOLD) {
                long now = System.currentTimeMillis();
                if (now - lastQdrantAlertTime > ALERT_COOLDOWN_MS) {
                    sendDataSourceAlert("Qdrant 向量库", qdrantFailureCount, e.getMessage());
                    lastQdrantAlertTime = now;
                }
            }
        }
    }

    /**
     * 发送数据源异常告警到飞书
     */
    private void sendDataSourceAlert(String serviceName, int failureCount, String errorMessage) {
        if (feishuMessageService == null) {
            log.debug("飞书服务未配置，跳过告警推送");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String alertText = String.format(
                "【数据源异常告警】\n" +
                "服务：%s\n" +
                "状态：连续失败 %d 次\n" +
                "时间：%s\n" +
                "错误：%s\n" +
                "影响：RAG 检索、K线查询等功能可能受限",
                serviceName, failureCount, timestamp,
                errorMessage != null && errorMessage.length() > 100
                    ? errorMessage.substring(0, 100) + "..."
                    : errorMessage
        );

        boolean sent = feishuMessageService.sendToMe(alertText);
        if (sent) {
            log.info("已发送数据源异常告警: {}, 失败次数: {}", serviceName, failureCount);
        } else {
            log.warn("数据源异常告警发送失败: {}", serviceName);
        }
    }

    /**
     * 发送数据源恢复通知到飞书
     */
    private void sendRecoveryAlert(String serviceName, int previousFailureCount) {
        if (feishuMessageService == null) {
            log.debug("飞书服务未配置，跳过恢复通知");
            return;
        }

        // 只有之前发送过告警（失败次数 >= 阈值）才发送恢复通知
        if (previousFailureCount < FAILURE_THRESHOLD) {
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String alertText = String.format(
                "【数据源恢复通知】\n" +
                "服务：%s\n" +
                "状态：已恢复正常\n" +
                "时间：%s\n" +
                "说明：服务已重新上线，功能恢复",
                serviceName, timestamp
        );

        boolean sent = feishuMessageService.sendToMe(alertText);
        if (sent) {
            log.info("已发送数据源恢复通知: {}", serviceName);
        }
    }
}
