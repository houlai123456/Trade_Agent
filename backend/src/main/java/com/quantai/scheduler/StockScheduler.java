package com.quantai.scheduler;

import com.quantai.annotation.DistributedLock;
import com.quantai.model.entity.StockQuote;
import com.quantai.feishu.FeishuNotificationService;
import com.quantai.model.vo.AlertVO;
import com.quantai.service.AlertService;
import com.quantai.service.DataServiceClient;
import com.quantai.service.RiskMonitorService;
import com.quantai.service.StockService;
import com.quantai.service.WatchService;
import com.quantai.websocket.StockWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockScheduler implements CommandLineRunner {

    private final StockService stockService;
    private final AlertService alertService;
    private final DataServiceClient dataServiceClient;
    private final StockWebSocketHandler webSocketHandler;
    private final WatchService watchService;
    private final FeishuNotificationService feishuNotifier;
    private final RiskMonitorService riskMonitorService;
    private final com.quantai.service.BacktestService backtestService;

    @Override
    public void run(String... args) {
        log.info("检查AKShare数据服务状态...");
        boolean ok = dataServiceClient.healthCheck();
        if (ok) {
            log.info("AKShare数据服务已连接，开始预加载K线数据");
            try {
                stockService.preloadKlineData();
                log.info("数据预加载完成");
            } catch (Exception e) {
                log.warn("数据预加载失败，请确认Python服务已启动: python data_service.py", e);
            }
        } else {
            log.warn("AKShare数据服务未启动（http://localhost:5000），跳过预加载。");
            log.warn("请先启动Python服务: cd backend && python data_service.py");
        }
    }

    private int boardPushIndex = 0;
    private static final String[] BOARD_TYPES = {"all", "main", "chiNext", "star", "bj"};

    @Scheduled(fixedRate = 1000)
    public void refreshWatchlistQuotes() {
        if (!isTradingTime()) return;
        try {
            List<StockQuote> quotes = stockService.getWatchlist(1L);
            if (!quotes.isEmpty()) {
                webSocketHandler.pushQuoteUpdate(quotes);
            }
            List<Map<String, Object>> indices = dataServiceClient.fetchIndexQuotes();
            if (indices != null && !indices.isEmpty()) {
                webSocketHandler.pushIndexUpdate(indices);
            }
            // 每秒轮替推送一个板块的股票数据（5秒一圈）
            String board = BOARD_TYPES[boardPushIndex % BOARD_TYPES.length];
            boardPushIndex++;
            Map<String, Object> boardData = dataServiceClient.fetchBoardStocksWithTotal(board, 1, 10);
            if (boardData != null) {
                boardData.put("board", board);
                webSocketHandler.pushBoardUpdate(boardData);
            }
            // 热点/概念/概况每5秒推送一次（变动频率低，无需每秒拉）
            if (boardPushIndex % 5 == 0) {
                List<Map<String, Object>> hotBoards = dataServiceClient.fetchHotBoards();
                if (hotBoards != null) webSocketHandler.pushHotBoardUpdate(hotBoards);
                List<Map<String, Object>> hotConcepts = dataServiceClient.fetchHotConcepts();
                if (hotConcepts != null) webSocketHandler.pushHotConceptUpdate(hotConcepts);
                Map<String, Object> overview = dataServiceClient.fetchMarketOverview();
                if (overview != null) webSocketHandler.pushMarketUpdate(overview);
            }
        } catch (Exception e) {
            log.error("定时刷新行情失败", e);
        }
    }

    @Scheduled(fixedRate = 1800000)
    @DistributedLock(key = "task:refreshKline", waitTime = 1, leaseTime = 1800, failMessage = "K线刷新任务已在其他节点执行")
    public void refreshKlineData() {
        try {
            log.info("开始刷新K线数据");
            stockService.preloadKlineData();
            log.info("K线数据刷新完成");
        } catch (Exception e) {
            log.error("定时刷新K线失败", e);
        }
    }

    @Scheduled(fixedRate = 120000)
    @DistributedLock(key = "task:checkAlerts", waitTime = 1, leaseTime = 120, failMessage = "预警检查任务已在其他节点执行")
    public void checkAlerts() {
        try {
            List<AlertVO> alerts = alertService.checkAlerts();
            if (!alerts.isEmpty()) {
                for (AlertVO alert : alerts) {
                    webSocketHandler.pushAlert(alert);
                    feishuNotifier.notifyAbnormalAlert(alert.getCode(), alert.getName(),
                            alert.getAlertType(), alert.getDescription());
                }
                log.info("发现{}条异动预警", alerts.size());
            }
        } catch (Exception e) {
            log.error("定时检查异动失败", e);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void checkWatchRules() {
        if (!isTradingTime()) return;
        try {
            watchService.checkWatchRules();
        } catch (Exception e) {
            log.error("定时检查盯盘规则失败", e);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void checkConditionOrders() {
        if (!isTradingTime()) return;
        try {
            watchService.checkConditionOrders();
        } catch (Exception e) {
            log.error("定时检查条件单失败", e);
        }
    }

    /**
     * 每日收盘后执行风险检查（16:00）
     */
    @Scheduled(cron = "0 0 16 * * ?")
    @DistributedLock(key = "task:riskCheck", waitTime = 1, leaseTime = 600, failMessage = "风险检查任务已在其他节点执行")
    public void dailyRiskCheck() {
        try {
            log.info("开始执行每日风险检查");
            riskMonitorService.dailyRiskCheck();
            log.info("每日风险检查完成");
        } catch (Exception e) {
            log.error("每日风险检查失败", e);
        }
    }

    /**
     * 每日凌晨执行7天回测（02:00）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @DistributedLock(key = "task:backtest7d", waitTime = 1, leaseTime = 1800, failMessage = "7天回测任务已在其他节点执行")
    public void runBacktest7d() {
        try {
            log.info("开始执行7天回测任务");
            backtestService.runBacktest7d();
            log.info("7天回测任务完成");
        } catch (Exception e) {
            log.error("7天回测任务失败", e);
        }
    }

    /**
     * 每日凌晨执行30天回测（03:00）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @DistributedLock(key = "task:backtest30d", waitTime = 1, leaseTime = 1800, failMessage = "30天回测任务已在其他节点执行")
    public void runBacktest30d() {
        try {
            log.info("开始执行30天回测任务");
            backtestService.runBacktest30d();
            log.info("30天回测任务完成");
        } catch (Exception e) {
            log.error("30天回测任务失败", e);
        }
    }

    private boolean isTradingTime() {
        LocalTime now = LocalTime.now();
        boolean morning = now.isAfter(LocalTime.of(9, 25)) && now.isBefore(LocalTime.of(11, 30));
        boolean afternoon = now.isAfter(LocalTime.of(13, 0)) && now.isBefore(LocalTime.of(15, 6));
        return morning || afternoon;
    }
}
