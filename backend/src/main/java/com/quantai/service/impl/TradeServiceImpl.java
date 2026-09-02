package com.quantai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quantai.annotation.Idempotent;
import com.quantai.common.BusinessException;
import com.quantai.mapper.TradeAccountMapper;
import com.quantai.mapper.TradeOrderMapper;
import com.quantai.mapper.TradePositionMapper;
import com.quantai.model.entity.*;
import com.quantai.service.StockService;
import com.quantai.service.TradeService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    /** 默认用户ID（单用户模式） */
    private static final Long DEFAULT_USER_ID = 1L;

    /** A股买入最小单位（手 = 100股） */
    private static final int MIN_BUY_QUANTITY = 100;

    private final TradeAccountMapper accountMapper;
    private final TradePositionMapper positionMapper;
    private final TradeOrderMapper orderMapper;
    private final StockService stockService;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTables() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS trade_account (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT NOT NULL DEFAULT 1," +
                    "total_assets DECIMAL(20,2) DEFAULT 0," +
                    "available_balance DECIMAL(20,2) DEFAULT 0," +
                    "frozen_balance DECIMAL(20,2) DEFAULT 0," +
                    "market_value DECIMAL(20,2) DEFAULT 0," +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_user_id(user_id))");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS trade_position (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT NOT NULL DEFAULT 1," +
                    "code VARCHAR(20) NOT NULL," +
                    "name VARCHAR(100)," +
                    "quantity INT NOT NULL DEFAULT 0," +
                    "available_quantity INT NOT NULL DEFAULT 0," +
                    "cost_price DECIMAL(10,4)," +
                    "total_cost DECIMAL(20,4)," +
                    "current_price DECIMAL(10,4)," +
                    "market_value DECIMAL(20,2)," +
                    "profit_loss DECIMAL(20,2)," +
                    "pl_ratio DECIMAL(10,4)," +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_tp_user_code(user_id,code))");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS trade_order (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT NOT NULL DEFAULT 1," +
                    "code VARCHAR(20) NOT NULL," +
                    "name VARCHAR(100)," +
                    "trade_type VARCHAR(10) NOT NULL," +
                    "price DECIMAL(10,4) NOT NULL," +
                    "quantity INT NOT NULL," +
                    "amount DECIMAL(20,2) NOT NULL," +
                    "profit_loss DECIMAL(20,2)," +
                    "status VARCHAR(10) DEFAULT 'DONE'," +
                    "trade_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            jdbcTemplate.execute("INSERT IGNORE INTO trade_account (user_id,total_assets,available_balance,frozen_balance,market_value) " +
                    "VALUES (1,1000000.00,1000000.00,0.00,0.00)");
            // 兼容升级：给 trade_order 添加 order_type 列
            try {
                jdbcTemplate.execute("ALTER TABLE trade_order ADD COLUMN order_type VARCHAR(10) DEFAULT 'MARKET'");
            } catch (Exception ignored) {}
            log.info("交易相关表初始化完成");
        } catch (Exception e) {
            log.error("初始化交易表失败", e);
        }
    }

    @Override
    public TradeAccount getAccount() {
        TradeAccount account = accountMapper.selectByUserId(DEFAULT_USER_ID);
        if (account == null) {
            // 自动创建默认账户
            account = new TradeAccount();
            account.setUserId(DEFAULT_USER_ID);
            account.setTotalAssets(new BigDecimal("1000000.00"));
            account.setAvailableBalance(new BigDecimal("1000000.00"));
            account.setFrozenBalance(BigDecimal.ZERO);
            account.setMarketValue(BigDecimal.ZERO);
            accountMapper.insert(account);
        }
        // 实时刷新持仓市值和总资产
        refreshAccount(account);
        return account;
    }

    @Override
    @Idempotent(prefix = "buy", key = "#code + ':' + #quantity + ':' + #price", expireTime = 5, message = "请勿重复下单")
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TradeOrder buyStock(String code, int quantity, BigDecimal price) {
        // ========== 参数校验 ==========
        if (StrUtil.isBlank(code)) {
            throw new BusinessException("股票代码不能为空");
        }
        if (quantity <= 0 || quantity % MIN_BUY_QUANTITY != 0) {
            throw new BusinessException("买入数量必须是100股（1手）的整数倍");
        }

        // ========== 获取股票名称和成交价 ==========
        String name = resolveName(code);
        if (price == null) {
            price = resolveMarketPrice(code);
        }
        validatePriceLimit(code, price);

        // ========== 计算交易金额 ==========
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        // ========== 账户资金校验（悲观锁） ==========
        TradeAccount account = accountMapper.selectByUserIdForUpdate(DEFAULT_USER_ID);
        if (account == null) {
            throw new BusinessException("账户不存在，请先初始化");
        }
        if (account.getAvailableBalance().compareTo(totalCost) < 0) {
            throw new BusinessException(
                    String.format("可用资金不足！需要 %.2f 元，当前可用 %.2f 元",
                            totalCost, account.getAvailableBalance()));
        }

        // ========== 执行买入（事务内） ==========
        // 1. 扣减可用资金
        BigDecimal newBalance = account.getAvailableBalance().subtract(totalCost);
        accountMapper.updateBalance(DEFAULT_USER_ID, newBalance);

        // 2. 更新或创建持仓（悲观锁）
        TradePosition position = positionMapper.selectByUserAndCodeForUpdate(DEFAULT_USER_ID, code);
        if (position == null) {
            position = new TradePosition();
            position.setUserId(DEFAULT_USER_ID);
            position.setCode(code);
            position.setName(name);
            position.setQuantity(quantity);
            position.setAvailableQuantity(quantity);
            position.setTotalCost(totalCost.setScale(4, RoundingMode.HALF_UP));
            position.setCostPrice(price.setScale(4, RoundingMode.HALF_UP));
            position.setCurrentPrice(price);
            position.setMarketValue(totalCost);
            position.setProfitLoss(BigDecimal.ZERO);
            position.setPlRatio(BigDecimal.ZERO);
            positionMapper.insert(position);
        } else {
            // 加权平均计算新成本价
            int oldQty = position.getQuantity();
            int newQty = oldQty + quantity;
            BigDecimal oldTotalCost = position.getTotalCost() != null ?
                    position.getTotalCost() : BigDecimal.ZERO;
            BigDecimal newTotalCost = oldTotalCost.add(totalCost);
            // 加权平均成本价 = 总成本 / 总数量
            BigDecimal avgCost = newTotalCost.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);

            int oldAvail = position.getAvailableQuantity() != null ? position.getAvailableQuantity() : 0;
            position.setQuantity(newQty);
            position.setAvailableQuantity(oldAvail + quantity);
            position.setTotalCost(newTotalCost);
            position.setCostPrice(avgCost);
            position.setCurrentPrice(price);
            position.setMarketValue(price.multiply(BigDecimal.valueOf(newQty)).setScale(2, RoundingMode.HALF_UP));
            position.setProfitLoss(BigDecimal.ZERO);
            position.setPlRatio(BigDecimal.ZERO);
            positionMapper.updateById(position);
        }

        // 3. 记录交易流水
        TradeOrder order = buildOrder(code, name, "BUY", price, quantity, totalCost, null);
        orderMapper.insert(order);

        log.info("买入成功: code={}, name={}, price={}, quantity={}, amount={}",
                code, name, price, quantity, totalCost);

        return order;
    }

    @Override
    @Idempotent(prefix = "sell", key = "#code + ':' + #quantity + ':' + #price", expireTime = 5, message = "请勿重复下单")
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TradeOrder sellStock(String code, int quantity, BigDecimal price) {
        // ========== 参数校验 ==========
        if (StrUtil.isBlank(code)) {
            throw new BusinessException("股票代码不能为空");
        }
        if (quantity <= 0) {
            throw new BusinessException("卖出数量必须大于0");
        }

        // ========== 检查持仓（悲观锁） ==========
        TradePosition position = positionMapper.selectByUserAndCodeForUpdate(DEFAULT_USER_ID, code);
        if (position == null || position.getQuantity() <= 0) {
            throw new BusinessException("未持有该股票，无法卖出");
        }
        int availQty = position.getAvailableQuantity() != null ? position.getAvailableQuantity() : 0;
        if (quantity > availQty) {
            throw new BusinessException(
                    String.format("卖出数量超过可用数量！可用 %d 股，卖出 %d 股（含挂单冻结）",
                            availQty, quantity));
        }

        // ========== 获取成交价 ==========
        if (price == null) {
            price = resolveMarketPrice(code);
        }
        validatePriceLimit(code, price);
        String name = position.getName();

        // ========== 计算交易金额和盈亏 ==========
        BigDecimal proceeds = price.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        // 卖出部分的成本
        BigDecimal costForSoldPart = position.getCostPrice()
                .multiply(BigDecimal.valueOf(quantity));
        // 盈亏 = (卖出价 - 成本价) × 数量
        BigDecimal profitLoss = price.subtract(position.getCostPrice())
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        // ========== 执行卖出（事务内） ==========
        // 1. 增加可用资金（悲观锁）
        TradeAccount account = accountMapper.selectByUserIdForUpdate(DEFAULT_USER_ID);
        BigDecimal newBalance = account.getAvailableBalance().add(proceeds);
        accountMapper.updateBalance(DEFAULT_USER_ID, newBalance);

        // 2. 扣减持仓
        int newQty = position.getQuantity() - quantity;
        if (newQty == 0) {
            // 全部卖完，删除持仓记录
            positionMapper.deleteById(position.getId());
            log.info("持仓清空: code={}", code);
        } else {
            // 部分卖出，更新剩余持仓
            // 剩余部分的总成本 = 原总成本 - 卖出部分的成本
            BigDecimal remainingTotalCost = position.getTotalCost()
                    .subtract(costForSoldPart)
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal remainingMarketValue = price.multiply(BigDecimal.valueOf(newQty))
                    .setScale(2, RoundingMode.HALF_UP);
            // 剩余部分的浮动盈亏 = (当前价 - 成本价) × 剩余数量
            BigDecimal remainingPL = price.subtract(position.getCostPrice())
                    .multiply(BigDecimal.valueOf(newQty))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal remainingPLPercent = position.getCostPrice().compareTo(BigDecimal.ZERO) > 0
                    ? price.subtract(position.getCostPrice())
                    .divide(position.getCostPrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            int shrinkAvail = position.getAvailableQuantity() != null ? position.getAvailableQuantity() - quantity : 0;
            position.setQuantity(newQty);
            position.setAvailableQuantity(Math.max(shrinkAvail, 0));
            position.setMarketValue(remainingMarketValue);
            position.setTotalCost(remainingTotalCost);
            position.setCurrentPrice(price);
            position.setProfitLoss(remainingPL);
            position.setPlRatio(remainingPLPercent);
            positionMapper.updateById(position);
        }

        // 3. 记录交易流水（含盈亏）
        TradeOrder order = buildOrder(code, name, "SELL", price, quantity, proceeds, profitLoss);
        orderMapper.insert(order);

        log.info("卖出成功: code={}, name={}, price={}, quantity={}, amount={}, profitLoss={}",
                code, name, price, quantity, proceeds, profitLoss);

        return order;
    }

    @Override
    public List<TradePosition> getPositions() {
        List<TradePosition> positions = positionMapper.selectByUserId(DEFAULT_USER_ID);
        // 批量获取实时行情，刷新持仓市值和盈亏
        if (!positions.isEmpty()) {
            List<String> codes = positions.stream().map(TradePosition::getCode).collect(Collectors.toList());
            List<StockQuote> quotes = stockService.getQuotes(codes);
            for (TradePosition p : positions) {
                quotes.stream()
                        .filter(q -> q.getCode().equals(p.getCode()) && q.getCurrentPrice() != null)
                        .findFirst()
                        .ifPresent(q -> refreshPositionPrice(p, q.getCurrentPrice()));
            }
        }
        return positions;
    }

    @Override
    public List<TradeOrder> getOrders() {
        return orderMapper.selectByUserId(DEFAULT_USER_ID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TradeOrder placeOrder(String code, int quantity, BigDecimal price, String direction) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException("股票代码不能为空");
        }
        if (quantity <= 0) {
            throw new BusinessException("数量必须大于0");
        }
        if ("BUY".equals(direction) && quantity % MIN_BUY_QUANTITY != 0) {
            throw new BusinessException("买入数量必须是100股（1手）的整数倍");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("限价单必须指定有效的价格");
        }

        validatePriceLimit(code, price);
        String name = resolveName(code);
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        TradeOrder order = new TradeOrder();
        order.setUserId(DEFAULT_USER_ID);
        order.setCode(code);
        order.setName(name);
        order.setTradeType(direction);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setAmount(amount);
        order.setStatus("PENDING");
        order.setOrderType("LIMIT");
        order.setTradeTime(LocalDateTime.now());

        if ("BUY".equals(direction)) {
            // 校验并冻结资金（悲观锁）
            TradeAccount account = accountMapper.selectByUserIdForUpdate(DEFAULT_USER_ID);
            if (account == null) {
                throw new BusinessException("账户不存在");
            }
            if (account.getAvailableBalance().compareTo(amount) < 0) {
                throw new BusinessException(
                        String.format("可用资金不足！需要 %.2f 元，当前可用 %.2f 元",
                                amount, account.getAvailableBalance()));
            }
            int rows = accountMapper.freezeBalance(DEFAULT_USER_ID, amount.negate(), amount);
            if (rows <= 0) {
                throw new BusinessException("资金冻结失败");
            }
        } else if ("SELL".equals(direction)) {
            // 校验并冻结股份（悲观锁）
            TradePosition position = positionMapper.selectByUserAndCodeForUpdate(DEFAULT_USER_ID, code);
            if (position == null) {
                throw new BusinessException("未持有该股票");
            }
            int availQty = position.getAvailableQuantity() != null ? position.getAvailableQuantity() : 0;
            if (quantity > availQty) {
                throw new BusinessException(
                        String.format("可用股份不足！可用 %d 股，需要 %d 股", availQty, quantity));
            }
            int rows = positionMapper.updateAvailableQuantity(position.getId(), -quantity);
            if (rows <= 0) {
                throw new BusinessException("股份冻结失败");
            }
        } else {
            throw new BusinessException("未知交易方向: " + direction);
        }

        orderMapper.insert(order);
        log.info("创建挂单成功: direction=, code={}, quantity={}, price={}", direction, code, quantity, price);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void cancelOrder(Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException("只能撤销挂单中的订单，当前状态: " + order.getStatus());
        }

        if ("BUY".equals(order.getTradeType())) {
            // 解冻资金（悲观锁）
            TradeAccount account = accountMapper.selectByUserIdForUpdate(DEFAULT_USER_ID);
            if (account != null) {
                BigDecimal amount = order.getAmount() != null ? order.getAmount() : BigDecimal.ZERO;
                accountMapper.freezeBalance(DEFAULT_USER_ID, amount, amount.negate());
            }
        } else if ("SELL".equals(order.getTradeType())) {
            // 解冻股份（悲观锁）
            TradePosition position = positionMapper.selectByUserAndCodeForUpdate(DEFAULT_USER_ID, order.getCode());
            if (position != null) {
                positionMapper.updateAvailableQuantity(position.getId(), order.getQuantity());
            }
        }

        order.setStatus("CANCELLED");
        orderMapper.updateById(order);
        log.info("撤单成功: orderId={}", orderId);
    }

    @Override
    public List<TradeOrder> getPendingOrders() {
        return orderMapper.selectByUserIdAndStatus(DEFAULT_USER_ID, "PENDING");
    }

    // ==================== 私有方法 ====================

    /**
     * 构建交易流水对象
     */
    private TradeOrder buildOrder(String code, String name, String type,
                                  BigDecimal price, int quantity, BigDecimal amount,
                                  BigDecimal profitLoss) {
        TradeOrder order = new TradeOrder();
        order.setUserId(DEFAULT_USER_ID);
        order.setCode(code);
        order.setName(name);
        order.setTradeType(type);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setAmount(amount);
        order.setProfitLoss(profitLoss);
        order.setStatus("DONE");
        order.setOrderType("MARKET");
        order.setTradeTime(LocalDateTime.now());
        return order;
    }

    /**
     * 用实时价格刷新持仓的市值和盈亏
     */
    private void refreshPositionPrice(TradePosition p, BigDecimal currentPrice) {
        BigDecimal marketValue = currentPrice.multiply(BigDecimal.valueOf(p.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pl = currentPrice.subtract(p.getCostPrice())
                .multiply(BigDecimal.valueOf(p.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal plPercent = p.getCostPrice().compareTo(BigDecimal.ZERO) > 0
                ? currentPrice.subtract(p.getCostPrice())
                .divide(p.getCostPrice(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        p.setCurrentPrice(currentPrice);
        p.setMarketValue(marketValue);
        p.setProfitLoss(pl);
        p.setPlRatio(plPercent);
    }

    /**
     * 刷新账户总资产
     */
    private void refreshAccount(TradeAccount account) {
        List<TradePosition> positions = positionMapper.selectByUserId(DEFAULT_USER_ID);
        if (positions.isEmpty()) {
            account.setMarketValue(BigDecimal.ZERO);
        } else {
            // 计算所有持仓的市值之和
            BigDecimal totalMv = BigDecimal.ZERO;
            for (TradePosition p : positions) {
                totalMv = totalMv.add(p.getMarketValue() != null ? p.getMarketValue() : BigDecimal.ZERO);
            }
            account.setMarketValue(totalMv);
        }
        // 总资产 = 可用资金 + 冻结资金 + 持仓市值
        BigDecimal available = account.getAvailableBalance() != null ? account.getAvailableBalance() : BigDecimal.ZERO;
        BigDecimal frozen = account.getFrozenBalance() != null ? account.getFrozenBalance() : BigDecimal.ZERO;
        BigDecimal mv = account.getMarketValue() != null ? account.getMarketValue() : BigDecimal.ZERO;
        account.setTotalAssets(available.add(frozen).add(mv));
    }

    /**
     * 校验限价是否在涨跌停范围内
     */
    private void validatePriceLimit(String code, BigDecimal price) {
        StockQuote quote = stockService.getQuote(code);
        if (quote == null || quote.getYesterdayClose() == null) return;

        BigDecimal base = quote.getYesterdayClose();
        int pct;
        String raw = code.replace("sh", "").replace("sz", "");
        if (raw.startsWith("688") || raw.startsWith("30")) pct = 20;
        else if (raw.startsWith("8") || raw.startsWith("4")) pct = 30;
        else pct = 10;

        BigDecimal limitUp = base.multiply(BigDecimal.valueOf(1 + pct / 100.0))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal limitDown = base.multiply(BigDecimal.valueOf(1 - pct / 100.0))
                .setScale(2, RoundingMode.HALF_UP);

        if (price.compareTo(limitUp) > 0) {
            throw new BusinessException(String.format(
                    "价格 %.2f 超过涨停价 %.2f（%s%%）", price, limitUp, pct));
        }
        if (price.compareTo(limitDown) < 0) {
            throw new BusinessException(String.format(
                    "价格 %.2f 低于跌停价 %.2f（%s%%）", price, limitDown, pct));
        }
    }

    /**
     * 获取股票名称（用于不经过行情查询的场景）
     */
    private String resolveName(String code) {
        try {
            StockQuote q = stockService.getQuote(code);
            if (q != null && StrUtil.isNotBlank(q.getName())) return q.getName();
        } catch (Exception ignored) {}
        return code;
    }

    /**
     * 获取实时行情价
     */
    private BigDecimal resolveMarketPrice(String code) {
        StockQuote quote = stockService.getQuote(code);
        if (quote == null) {
            throw new BusinessException("无法获取股票行情，请确认股票代码是否正确");
        }
        BigDecimal price = quote.getCurrentPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("股票当前价格异常，无法交易");
        }
        return price;
    }
}
