package com.quantai.service.impl;

import com.quantai.model.entity.ConditionOrder;
import com.quantai.model.entity.StockQuote;
import com.quantai.model.entity.WatchRule;
import com.quantai.mapper.ConditionOrderMapper;
import com.quantai.mapper.WatchRuleMapper;
import com.quantai.service.DataServiceClient;
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
                    "condition_type VARCHAR(10) NOT NULL," +
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
                    "condition_type VARCHAR(10) NOT NULL," +
                    "trigger_price DECIMAL(10,2) NOT NULL," +
                    "quantity INT NOT NULL," +
                    "order_price DECIMAL(10,2)," +
                    "status VARCHAR(20) DEFAULT 'PENDING'," +
                    "triggered_order_id BIGINT," +
                    "trigger_time TIMESTAMP," +
                    "expire_time TIMESTAMP," +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            log.info("盯盘/条件单表初始化完成");
        } catch (Exception e) {
            log.error("初始化盯盘/条件单表失败", e);
        }
    }

    private static final Long DEFAULT_USER_ID = 1L;

    private final WatchRuleMapper watchRuleMapper;
    private final ConditionOrderMapper conditionOrderMapper;
    private final DataServiceClient dataServiceClient;
    private final TradeService tradeService;
    private final StockWebSocketHandler webSocketHandler;

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

        // 批量获取实时行情
        List<String> codes = pendingOrders.stream().map(ConditionOrder::getCode).collect(Collectors.toList());
        List<StockQuote> quotes = dataServiceClient.fetchQuotes(codes);
        Map<String, StockQuote> quoteMap = quotes.stream()
                .collect(Collectors.toMap(StockQuote::getCode, q -> q, (a, b) -> a));

        List<Long> triggeredIds = new ArrayList<>();
        for (ConditionOrder order : pendingOrders) {
            StockQuote quote = quoteMap.get(order.getCode());
            if (quote == null || quote.getCurrentPrice() == null) continue;

            BigDecimal price = quote.getCurrentPrice();
            boolean shouldTrigger = "ABOVE".equals(order.getConditionType())
                    ? price.compareTo(order.getTriggerPrice()) >= 0
                    : price.compareTo(order.getTriggerPrice()) <= 0;

            if (shouldTrigger) {
                try {
                    // 执行交易
                    BigDecimal execPrice = order.getOrderPrice() != null ? order.getOrderPrice() : price;
                    var tradeOrder = tradeService.placeOrder(order.getCode(),
                            order.getQuantity(), execPrice, order.getDirection());

                    // 更新条件单状态
                    order.setStatus("TRIGGERED");
                    order.setTriggeredOrderId(tradeOrder.getId());
                    order.setTriggerTime(LocalDateTime.now());
                    conditionOrderMapper.updateById(order);
                    triggeredIds.add(order.getId());

                    log.info("条件单触发: id={}, code={}, direction={}, triggerPrice={}, currentPrice={}",
                            order.getId(), order.getCode(), order.getDirection(),
                            order.getTriggerPrice(), price);
                } catch (Exception e) {
                    log.error("条件单执行失败 id={}, code={}", order.getId(), order.getCode(), e);
                }
            }
        }
        return triggeredIds;
    }
}
