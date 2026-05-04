package com.quantai.service;

import com.quantai.model.entity.ConditionOrder;
import com.quantai.model.entity.WatchRule;

import java.math.BigDecimal;
import java.util.List;

public interface WatchService {

    // ========== 盯盘规则 ==========

    List<WatchRule> getWatchRules(Long userId);

    WatchRule addWatchRule(Long userId, String code, String name, String conditionType, BigDecimal targetPrice);

    WatchRule updateWatchRule(Long id, String conditionType, BigDecimal targetPrice, Integer enabled);

    void deleteWatchRule(Long id);

    /** 检查所有盯盘规则，触发条件时推送消息 */
    List<String> checkWatchRules();

    // ========== 条件单 ==========

    List<ConditionOrder> getConditionOrders(Long userId);

    ConditionOrder addConditionOrder(Long userId, String code, String name, String direction,
                                     String conditionType, BigDecimal triggerPrice,
                                     Integer quantity, BigDecimal orderPrice);

    void cancelConditionOrder(Long id);

    /** 检查所有待触发的条件单，满足条件时执行交易 */
    List<Long> checkConditionOrders();
}
