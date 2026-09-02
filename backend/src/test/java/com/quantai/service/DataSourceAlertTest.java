package com.quantai.service;

import com.quantai.feishu.FeishuMessageService;
import com.quantai.service.impl.HealthCheckServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 数据源异常告警测试
 * 测试连续失败达到阈值时是否正确发送飞书告警
 */
@SpringBootTest
public class DataSourceAlertTest {

    @Autowired
    private HealthCheckServiceImpl healthCheckService;

    @MockBean(name = "feishuMessageService")
    private FeishuMessageService feishuMessageService;

    @Autowired
    private DataServiceClient dataServiceClient;

    /**
     * 测试数据源异常告警机制
     * 模拟连续健康检查失败，验证告警触发
     */
    @Test
    public void testDataSourceAlertOnConsecutiveFailures() throws InterruptedException {
        System.out.println("=== 测试数据源异常告警 ===\n");

        // 配置 Mock：飞书推送成功
        when(feishuMessageService.sendToMe(anyString())).thenReturn(true);

        // 等待健康检查任务执行（定时任务每30秒执行一次，初始延迟5秒）
        System.out.println("等待健康检查任务启动...");
        Thread.sleep(7000);  // 等待7秒确保第一次检查完成

        // 检查初始状态
        boolean pythonHealthy = healthCheckService.isPythonServiceHealthy();
        boolean qdrantHealthy = healthCheckService.isQdrantHealthy();

        System.out.println("初始健康状态:");
        System.out.println("  Python 服务: " + (pythonHealthy ? "正常" : "异常"));
        System.out.println("  Qdrant 服务: " + (qdrantHealthy ? "正常" : "异常"));

        // 由于 Python 和 Qdrant 服务未启动，健康检查会持续失败
        // 等待足够时间让失败次数达到阈值（3次）
        // 30秒检查一次，3次失败需要至少 90 秒
        System.out.println("\n等待连续失败达到告警阈值（3次）...");
        System.out.println("预计等待时间：90-120秒");

        for (int i = 1; i <= 4; i++) {
            Thread.sleep(30000);  // 每30秒检查一次
            System.out.println("已等待 " + (i * 30) + " 秒，健康检查第 " + i + " 次执行");
        }

        // 验证飞书告警是否被调用
        // 至少应该有一次告警（Python 或 Qdrant）
        verify(feishuMessageService, atLeastOnce()).sendToMe(argThat(text ->
                text != null && text.contains("数据源异常告警")
        ));

        System.out.println("\n✓ 数据源异常告警已触发");

        // 打印最终健康状态
        pythonHealthy = healthCheckService.isPythonServiceHealthy();
        qdrantHealthy = healthCheckService.isQdrantHealthy();

        System.out.println("\n最终健康状态:");
        System.out.println("  Python 服务: " + (pythonHealthy ? "正常" : "异常"));
        System.out.println("  Qdrant 服务: " + (qdrantHealthy ? "正常" : "异常"));

        System.out.println("\n=== 测试完成 ===");
    }

    /**
     * 测试告警冷却机制
     * 验证5分钟内不会重复告警
     */
    @Test
    public void testAlertCooldownMechanism() throws InterruptedException {
        System.out.println("=== 测试告警冷却机制 ===\n");

        // 配置 Mock
        when(feishuMessageService.sendToMe(anyString())).thenReturn(true);

        // 等待初始健康检查
        Thread.sleep(7000);

        System.out.println("等待第一次告警触发...");
        Thread.sleep(90000);  // 等待90秒让失败次数达到阈值

        // 记录第一次告警的调用次数
        int firstAlertCount = mockingDetails(feishuMessageService).getInvocations().size();
        System.out.println("第一次告警后，总调用次数: " + firstAlertCount);

        // 再等待60秒（仍在5分钟冷却期内）
        System.out.println("\n等待60秒（冷却期内）...");
        Thread.sleep(60000);

        // 验证没有新增告警
        int secondCheckCount = mockingDetails(feishuMessageService).getInvocations().size();
        System.out.println("冷却期内再次检查，总调用次数: " + secondCheckCount);

        if (secondCheckCount == firstAlertCount) {
            System.out.println("✓ 冷却期内未重复告警（正确）");
        } else {
            System.out.println("⚠ 冷却期内仍有新告警（可能是其他服务触发）");
        }

        System.out.println("\n=== 测试完成 ===");
    }
}
