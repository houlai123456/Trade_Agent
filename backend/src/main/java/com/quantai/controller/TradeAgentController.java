package com.quantai.controller;

import com.quantai.common.Result;
import com.quantai.model.dto.TradeIntent;
import com.quantai.model.entity.TradeAccount;
import com.quantai.model.entity.TradeOrder;
import com.quantai.model.entity.TradePosition;
import com.quantai.service.TradeAgentService;
import com.quantai.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI交易指令Agent — 自然语言下单入口
 */
@RestController
@RequestMapping("/api/trade-agent")
@RequiredArgsConstructor
public class TradeAgentController {

    private final TradeAgentService tradeAgentService;
    private final TradeService tradeService;

    /**
     * 解析自然语言交易指令
     * POST /api/trade-agent/parse
     * {"message": "买入100股贵州茅台"}
     *
     * 返回解析后的交易意图（还未执行），前端确认后再调 /execute
     */
    @PostMapping("/parse")
    public Result<TradeIntent> parse(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.error("消息不能为空");
        }

        TradeIntent intent = tradeAgentService.parseIntent(message);
        if (!Boolean.TRUE.equals(intent.getTrade()) && intent.getDisplayMessage() == null) {
            intent.setDisplayMessage("未能识别出交易指令，请确认输入格式，如：买入100股贵州茅台");
        }
        return Result.success(intent);
    }

    /**
     * 执行已确认的交易指令
     * POST /api/trade-agent/execute
     * {
     *   "action": "BUY",
     *   "stockCode": "sh600519",
     *   "stockName": "贵州茅台",
     *   "quantity": 100,
     *   "price": null
     * }
     */
    @PostMapping("/execute")
    public Result<Map<String, Object>> execute(@RequestBody TradeIntent intent) {
        try {
            TradeOrder order = tradeAgentService.executeTrade(intent);

            String actionText = "BUY".equals(intent.getAction()) ? "买入" : "卖出";
            Map<String, Object> result = new HashMap<>();
            result.put("order", order);
            result.put("message", String.format("✅ 已%s %d 股 %s(%s)，成交价 %.2f 元，成交金额 %.2f 元",
                    actionText, order.getQuantity(), intent.getStockName(),
                    order.getCode(), order.getPrice(), order.getAmount()));

            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("交易执行失败: " + e.getMessage());
        }
    }

    /**
     * 查询账户总资产
     * GET /api/trade-agent/account
     */
    @GetMapping("/account")
    public Result<TradeAccount> getAccount() {
        return Result.success(tradeService.getAccount());
    }

    /**
     * 查询持仓列表
     * GET /api/trade-agent/positions
     */
    @GetMapping("/positions")
    public Result<List<TradePosition>> getPositions() {
        return Result.success(tradeService.getPositions());
    }
}
