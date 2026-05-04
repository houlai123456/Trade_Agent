package com.quantai.controller;

import com.quantai.model.entity.ConditionOrder;
import com.quantai.model.entity.WatchRule;
import com.quantai.service.WatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watch")
@RequiredArgsConstructor
public class WatchController {

    private final WatchService watchService;

    // ========== 盯盘规则 ==========

    @GetMapping("/rules")
    public ResponseEntity<List<WatchRule>> getWatchRules(@RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(watchService.getWatchRules(userId));
    }

    @PostMapping("/rules")
    public ResponseEntity<WatchRule> addWatchRule(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String conditionType = (String) body.get("conditionType");
        BigDecimal targetPrice = body.get("targetPrice") != null
                ? BigDecimal.valueOf(((Number) body.get("targetPrice")).doubleValue()) : null;
        return ResponseEntity.ok(watchService.addWatchRule(userId, code, name, conditionType, targetPrice));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<WatchRule> updateWatchRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String conditionType = (String) body.get("conditionType");
        BigDecimal targetPrice = body.get("targetPrice") != null
                ? BigDecimal.valueOf(((Number) body.get("targetPrice")).doubleValue()) : null;
        Integer enabled = body.get("enabled") != null ? ((Number) body.get("enabled")).intValue() : null;
        WatchRule rule = watchService.updateWatchRule(id, conditionType, targetPrice, enabled);
        if (rule == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(rule);
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteWatchRule(@PathVariable Long id) {
        watchService.deleteWatchRule(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ========== 条件单 ==========

    @GetMapping("/orders")
    public ResponseEntity<List<ConditionOrder>> getConditionOrders(@RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(watchService.getConditionOrders(userId));
    }

    @PostMapping("/orders")
    public ResponseEntity<ConditionOrder> addConditionOrder(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String direction = (String) body.get("direction");
        String conditionType = (String) body.get("conditionType");
        BigDecimal triggerPrice = body.get("triggerPrice") != null
                ? BigDecimal.valueOf(((Number) body.get("triggerPrice")).doubleValue()) : null;
        Integer quantity = body.get("quantity") != null ? ((Number) body.get("quantity")).intValue() : null;
        BigDecimal orderPrice = body.get("orderPrice") != null
                ? BigDecimal.valueOf(((Number) body.get("orderPrice")).doubleValue()) : null;
        return ResponseEntity.ok(watchService.addConditionOrder(userId, code, name, direction,
                conditionType, triggerPrice, quantity, orderPrice));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<Map<String, Boolean>> cancelConditionOrder(@PathVariable Long id) {
        watchService.cancelConditionOrder(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
