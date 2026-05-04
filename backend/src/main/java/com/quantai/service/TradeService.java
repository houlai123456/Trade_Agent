package com.quantai.service;

import com.quantai.model.entity.TradeAccount;
import com.quantai.model.entity.TradeOrder;
import com.quantai.model.entity.TradePosition;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟交易服务接口
 */
public interface TradeService {

    /**
     * 查询账户资产
     */
    TradeAccount getAccount();

    /**
     * 模拟买入股票
     * @param code     股票代码（如 sh600519）
     * @param quantity 买入数量（股）
     * @param price    指定成交价（null 则取实时行情价）
     * @return 交易流水
     */
    TradeOrder buyStock(String code, int quantity, BigDecimal price);

    /**
     * 模拟卖出股票
     * @param code     股票代码
     * @param quantity 卖出数量（股）
     * @param price    指定成交价（null 则取实时行情价）
     * @return 交易流水
     */
    TradeOrder sellStock(String code, int quantity, BigDecimal price);

    /**
     * 查询持仓列表
     */
    List<TradePosition> getPositions();

    /**
     * 查询交易流水
     */
    List<TradeOrder> getOrders();

    /**
     * 创建限价挂单
     * @param code     股票代码（如 sh600519）
     * @param quantity 数量（股）
     * @param price    限价
     * @param direction BUY/SELL
     * @return 挂单订单
     */
    TradeOrder placeOrder(String code, int quantity, BigDecimal price, String direction);

    /**
     * 撤销挂单
     * @param orderId 订单ID
     */
    void cancelOrder(Long orderId);

    /**
     * 查询所有挂单（PENDING状态订单）
     */
    List<TradeOrder> getPendingOrders();
}
