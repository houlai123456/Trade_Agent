package com.quantai.service.impl;

import com.quantai.feishu.FeishuNotificationService;
import com.quantai.model.entity.ConditionOrder;
import com.quantai.model.entity.StockQuote;
import com.quantai.model.entity.WatchRule;
import com.quantai.model.vo.KlineVO;
import com.quantai.mapper.ConditionOrderMapper;
import com.quantai.mapper.WatchRuleMapper;
import com.quantai.service.DataServiceClient;
import com.quantai.service.StockService;
import com.quantai.service.TradeService;
import com.quantai.service.WatchService;
import com.quantai.websocket.StockWebSocketHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchServiceImpl implements WatchService {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTables() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS watch_rule (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT NOT NULL DEFAULT 1," +
                    "code VARCHAR(20) NOT NULL," +
                    "name VARCHAR(100)," +
                    "condition_type VARCHAR(20) NOT NULL," +
                    "target_price DECIMAL(10,2) NOT NULL," +
                    "enabled INT DEFAULT 1," +
                    "last_triggered_time TIMESTAMP," +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS condition_order (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT NOT NULL DEFAULT 1," +
                    "code VARCHAR(20) NOT NULL," +
                    "name VARCHAR(100)," +
                    "direction VARCHAR(10) NOT NULL," +
                    "condition_type VARCHAR(20) NOT NULL," +
                    "trigger_price DECIMAL(10,2) NOT NULL," +
                    "quantity INT NOT NULL," +
                    "order_price DECIMAL(10,2)," +
                    "status VARCHAR(20) DEFAULT 'PENDING'," +
                    "triggered_order_id BIGINT," +
                    "trigger_time TIMESTAMP," +
                    "expire_time TIMESTAMP," +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            // 扩展 condition_type 字段长度，兼容新的技术指标类型
            try { jdbcTemplate.execute("ALTER TABLE watch_rule ALTER COLUMN condition_type VARCHAR(20)"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE condition_order ALTER COLUMN condition_type VARCHAR(20)"); } catch (Exception ignored) {}
            log.info("盯盘/条件单表初始化完成");
        } catch (Exception e) {
            log.error("初始化盯盘/条件单表失败", e);
        }
    }

    private static final Long DEFAULT_USER_ID = 1L;

    private final WatchRuleMapper watchRuleMapper;
    private final ConditionOrderMapper conditionOrderMapper;
    private final DataServiceClient dataServiceClient;
    private final StockService stockService;
    private final TradeService tradeService;
    private final StockWebSocketHandler webSocketHandler;
    private final FeishuNotificationService feishuNotifier;

    // ========== 盯盘规则 ==========

    @Override
    public List<WatchRule> getWatchRules(Long userId) {
        return watchRuleMapper.selectByUserId(userId != null ? userId : DEFAULT_USER_ID);
    }

    @Override
    public WatchRule addWatchRule(Long userId, String code, String name,
                                  String conditionType, BigDecimal targetPrice) {
        WatchRule rule = new WatchRule();
        rule.setUserId(userId != null ? userId : DEFAULT_USER_ID);
        rule.setCode(code);
        rule.setName(name);
        rule.setConditionType(conditionType);
        rule.setTargetPrice(targetPrice);
        rule.setEnabled(1);
        watchRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public WatchRule updateWatchRule(Long id, String conditionType, BigDecimal targetPrice, Integer enabled) {
        WatchRule rule = watchRuleMapper.selectById(id);
        if (rule == null) return null;
        if (conditionType != null) rule.setConditionType(conditionType);
        if (targetPrice != null) rule.setTargetPrice(targetPrice);
        if (enabled != null) rule.setEnabled(enabled);
        watchRuleMapper.updateById(rule);
        return rule;
    }

    @Override
    public void deleteWatchRule(Long id) {
        watchRuleMapper.deleteById(id);
    }

    @Override
    public List<String> checkWatchRules() {
        List<WatchRule> rules = watchRuleMapper.selectEnabledRules(DEFAULT_USER_ID);
        if (rules.isEmpty()) return Collections.emptyList();

        // 批量获取实时行情
        List<String> codes = rules.stream().map(WatchRule::getCode).collect(Collectors.toList());
        List<StockQuote> quotes = dataServiceClient.fetchQuotes(codes);
        Map<String, StockQuote> quoteMap = quotes.stream()
                .collect(Collectors.toMap(StockQuote::getCode, q -> q, (a, b) -> a));

        List<String> alerts = new ArrayList<>();
        for (WatchRule rule : rules) {
            StockQuote quote = quoteMap.get(rule.getCode());
            if (quote == null || quote.getCurrentPrice() == null) continue;

            BigDecimal price = quote.getCurrentPrice();
            boolean triggered = "ABOVE".equals(rule.getConditionType())
                    ? price.compareTo(rule.getTargetPrice()) >= 0
                    : price.compareTo(rule.getTargetPrice()) <= 0;

            if (triggered) {
                String msg = String.format("%s(%s) 当前价 %.2f %s %.2f",
                        rule.getName(), rule.getCode(), price,
                        "ABOVE".equals(rule.getConditionType()) ? "≥" : "≤",
                        rule.getTargetPrice());
                alerts.add(msg);
                watchRuleMapper.updateLastTriggeredTime(rule.getId(), LocalDateTime.now());

                // WebSocket推送
                Map<String, Object> push = new LinkedHashMap<>();
                push.put("type", "WATCH_ALERT");
                push.put("code", rule.getCode());
                push.put("name", rule.getName());
                push.put("currentPrice", price);
                push.put("targetPrice", rule.getTargetPrice());
                push.put("conditionType", rule.getConditionType());
                push.put("message", msg);
                webSocketHandler.broadcast(push);
                feishuNotifier.notifyWatchRule(rule.getCode(), rule.getName(),
                        rule.getConditionType(), rule.getTargetPrice().toPlainString(), price.toPlainString());
            }
        }
        return alerts;
    }

    // ========== 条件单 ==========

    @Override
    public List<ConditionOrder> getConditionOrders(Long userId) {
        return conditionOrderMapper.selectByUserId(userId != null ? userId : DEFAULT_USER_ID);
    }

    @Override
    public ConditionOrder addConditionOrder(Long userId, String code, String name,
                                            String direction, String conditionType,
                                            BigDecimal triggerPrice, Integer quantity,
                                            BigDecimal orderPrice) {
        ConditionOrder order = new ConditionOrder();
        order.setUserId(userId != null ? userId : DEFAULT_USER_ID);
        order.setCode(code);
        order.setName(name);
        order.setDirection(direction);
        order.setConditionType(conditionType);
        order.setTriggerPrice(triggerPrice);
        order.setQuantity(quantity);
        order.setOrderPrice(orderPrice);
        order.setStatus("PENDING");
        conditionOrderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelConditionOrder(Long id) {
        ConditionOrder order = conditionOrderMapper.selectById(id);
        if (order != null && "PENDING".equals(order.getStatus())) {
            order.setStatus("CANCELLED");
            conditionOrderMapper.updateById(order);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> checkConditionOrders() {
        List<ConditionOrder> pendingOrders = conditionOrderMapper.selectPendingOrders(DEFAULT_USER_ID);
        if (pendingOrders.isEmpty()) return Collections.emptyList();

        List<String> codes = pendingOrders.stream().map(ConditionOrder::getCode).collect(Collectors.toList());
        List<StockQuote> quotes = dataServiceClient.fetchQuotes(codes);
        Map<String, StockQuote> quoteMap = quotes.stream()
                .collect(Collectors.toMap(StockQuote::getCode, q -> q, (a, b) -> a));

        List<Long> triggeredIds = new ArrayList<>();
        for (ConditionOrder order : pendingOrders) {
            StockQuote quote = quoteMap.get(order.getCode());
            if (quote == null || quote.getCurrentPrice() == null) continue;

            boolean shouldTrigger = evaluateCondition(order, quote);
            if (!shouldTrigger) continue;

            try {
                BigDecimal execPrice = order.getOrderPrice() != null
                        ? order.getOrderPrice() : quote.getCurrentPrice();
                var tradeOrder = tradeService.placeOrder(order.getCode(),
                        order.getQuantity(), execPrice, order.getDirection());

                order.setStatus("TRIGGERED");
                order.setTriggeredOrderId(tradeOrder.getId());
                order.setTriggerTime(LocalDateTime.now());
                conditionOrderMapper.updateById(order);
                triggeredIds.add(order.getId());

                log.info("条件单触发: id={}, code={}, type={}, price={}",
                        order.getId(), order.getCode(), order.getConditionType(),
                        quote.getCurrentPrice());
                feishuNotifier.notifyConditionOrder(order.getCode(), order.getName(),
                        order.getDirection(), order.getConditionType(),
                        quote.getCurrentPrice().toPlainString());
            } catch (Exception e) {
                log.error("条件单执行失败 id={}, code={}", order.getId(), order.getCode(), e);
            }
        }
        return triggeredIds;
    }

    private boolean evaluateCondition(ConditionOrder order, StockQuote quote) {
        String type = order.getConditionType();
        BigDecimal price = quote.getCurrentPrice();

        return switch (type) {
            case "ABOVE" -> price.compareTo(order.getTriggerPrice()) >= 0;
            case "BELOW" -> price.compareTo(order.getTriggerPrice()) <= 0;
            case "GOLDEN_CROSS", "DEATH_CROSS" -> checkMaCross(order.getCode(), type);
            case "VOLUME_BREAKOUT" -> checkVolumeBreakout(order.getCode());
            default -> false;
        };
    }

    /** 检测均线金叉/死叉：MA5 与 MA10 交叉 */
    private boolean checkMaCross(String code, String type) {
        try {
            List<KlineVO> klines = stockService.getKlineData(code, "DAY", 3);
            if (klines == null || klines.size() < 2) return false;

            KlineVO prev = klines.get(klines.size() - 2);
            KlineVO curr = klines.get(klines.size() - 1);
            if (prev.getMa5() == null || prev.getMa10() == null
                    || curr.getMa5() == null || curr.getMa10() == null) return false;

            if ("GOLDEN_CROSS".equals(type)) {
                return prev.getMa5().compareTo(prev.getMa10()) < 0
                        && curr.getMa5().compareTo(curr.getMa10()) >= 0;
            } else {
                return prev.getMa5().compareTo(prev.getMa10()) > 0
                        && curr.getMa5().compareTo(curr.getMa10()) <= 0;
            }
        } catch (Exception e) {
            log.warn("均线交叉检测失败 code={}", code, e);
            return false;
        }
    }

    /** 检测放量突破：今日成交量 > 5日均量的1.5倍 */
    private boolean checkVolumeBreakout(String code) {
        try {
            List<KlineVO> klines = stockService.getKlineData(code, "DAY", 6);
            if (klines == null || klines.size() < 6) return false;

            // 最后一条是今日，前5条计算均量
            KlineVO today = klines.get(klines.size() - 1);
            long sumVol = 0;
            for (int i = klines.size() - 6; i < klines.size() - 1; i++) {
                KlineVO k = klines.get(i);
                if (k.getVolume() != null) sumVol += k.getVolume();
            }
            double avg5 = sumVol / 5.0;
            return today.getVolume() != null && today.getVolume() > avg5 * 1.5;
        } catch (Exception e) {
            log.warn("放量检测失败 code={}", code, e);
            return false;
        }
    }
}
