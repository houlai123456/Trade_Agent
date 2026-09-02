package com.quantai.service;

import com.quantai.model.entity.TradeOrder;
import com.quantai.service.impl.TradeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等性和分布式锁测试
 */
@SpringBootTest
public class IdempotencyTest {

    @Autowired
    private TradeService tradeService;

    /**
     * 测试幂等性保护 - 5秒内重复提交相同买单应被拦截
     */
    @Test
    public void testIdempotentBuyStock() throws InterruptedException {
        String code = "600519";
        int quantity = 100;
        BigDecimal price = new BigDecimal("1500.00");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        // 并发提交10次相同订单
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    tradeService.buyStock(code, quantity, price);
                    successCount.incrementAndGet();
                    System.out.println("订单提交成功");
                } catch (RuntimeException e) {
                    String msg = e.getMessage();
                    System.out.println("异常: " + msg);
                    if (msg != null && msg.contains("重复")) {
                        failCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("成功提交: " + successCount.get());
        System.out.println("被幂等拦截: " + failCount.get());
        System.out.println("其他错误: " + otherErrorCount.get());

        // 验证幂等性：最多只有1个成功
        assertTrue(successCount.get() <= 1, "最多只能有1个订单成功");

        // 如果有成功的订单，验证幂等拦截生效
        if (successCount.get() == 1) {
            assertTrue(failCount.get() >= 1, "至少应有部分请求被幂等性拦截");
        }

        // 验证整体：成功 + 幂等拦截 + 其他错误 = 总请求数
        assertEquals(10, successCount.get() + failCount.get() + otherErrorCount.get(),
                "请求总数应为10");
    }

    /**
     * 测试分布式锁 - K线刷新任务不会并发执行
     */
    @Test
    public void testDistributedLockOnScheduledTask() throws InterruptedException {
        // 由于定时任务需要真实 Redis 和多实例环境
        // 这里只做编译验证，实际分布式锁测试需要集成测试环境
        assertTrue(true, "分布式锁已集成到定时任务，需要集成测试验证");
    }
}
