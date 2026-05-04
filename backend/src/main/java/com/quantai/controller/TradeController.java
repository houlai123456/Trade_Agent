package com.quantai.controller;

import com.quantai.common.Result;
import com.quantai.model.entity.TradeAccount;
import com.quantai.model.entity.TradeOrder;
import com.quantai.model.entity.TradePosition;
import com.quantai.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模拟交易控制器
 */
@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    /**
     * 查询账户资产
     * GET /api/trade/account
     */
    @GetMapping("/account")
    public Result<TradeAccount> getAccount() {
        return Result.success(tradeService.getAccount());
    }

    /**
     * 模拟买入股票
     * POST /api/trade/buy
     * 请求体: {"code": "sh600519", "quantity": 100}
     */
    @PostMapping("/buy")
    public Result<TradeOrder> buyStock(@RequestBody Map<String, Object> params) {
        String code = (String) params.get("code");
        int quantity = params.get("quantity") instanceof Integer
                ? (Integer) params.get("quantity")
                : Integer.parseInt(params.get("quantity").toString());
        BigDecimal price = params.containsKey("price") && params.get("price") != null
                ? new BigDecimal(params.get("price").toString())
                : null;
        return Result.success(tradeService.buyStock(code, quantity, price));
    }

    /**
     * 模拟卖出股票
     * POST /api/trade/sell
     * 请求体: {"code": "sh600519", "quantity": 100}
     */
    @PostMapping("/sell")
    public Result<TradeOrder> sellStock(@RequestBody Map<String, Object> params) {
        String code = (String) params.get("code");
        int quantity = params.get("quantity") instanceof Integer
                ? (Integer) params.get("quantity")
                : Integer.parseInt(params.get("quantity").toString());
        BigDecimal price = params.containsKey("price") && params.get("price") != null
                ? new BigDecimal(params.get("price").toString())
                : null;
        return Result.success(tradeService.sellStock(code, quantity, price));
    }

    /**
     * 查询持仓列表
     * GET /api/trade/positions
     */
    @GetMapping("/positions")
    public Result<List<TradePosition>> getPositions() {
        return Result.success(tradeService.getPositions());
    }

    /**
     * 查询交易流水
     * GET /api/trade/orders
     */
    @GetMapping("/orders")
    public Result<List<TradeOrder>> getOrders() {
        return Result.success(tradeService.getOrders());
    }

    /**
     * 创建限价挂单
     * POST /api/trade/place-order
     * {"code": "sh600519", "quantity": 100, "price": 180.00, "direction": "BUY"}
     */
    @PostMapping("/place-order")
    public Result<TradeOrder> placeOrder(@RequestBody Map<String, Object> params) {
        String code = (String) params.get("code");
        int quantity = params.get("quantity") instanceof Integer
                ? (Integer) params.get("quantity")
                : Integer.parseInt(params.get("quantity").toString());
        BigDecimal price = params.containsKey("price") && params.get("price") != null
                ? new BigDecimal(params.get("price").toString())
                : null;
        String direction = (String) params.get("direction");
        return Result.success(tradeService.placeOrder(code, quantity, price, direction));
    }

    /**
     * 撤销挂单
     * POST /api/trade/cancel-order/{id}
     */
    @PostMapping("/cancel-order/{id}")
    public Result<Void> cancelOrder(@PathVariable Long id) {
        tradeService.cancelOrder(id);
        return Result.success(null);
    }

    /**
     * 查询挂单列表
     * GET /api/trade/pending-orders
     */
    @GetMapping("/pending-orders")
    public Result<List<TradeOrder>> getPendingOrders() {
        return Result.success(tradeService.getPendingOrders());
    }
}
