package com.quantai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quantai.mapper.StockInfoMapper;
import com.quantai.mapper.StockKlineMapper;
import com.quantai.mapper.UserStockMapper;
import com.quantai.model.entity.StockInfo;
import com.quantai.model.entity.StockKline;
import com.quantai.model.entity.StockQuote;
import com.quantai.model.entity.UserStock;
import com.quantai.model.vo.KlineVO;
import com.quantai.service.DataServiceClient;
import com.quantai.service.IndicatorService;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private static final String QUOTE_CACHE_PREFIX = "stock:quote:";
    private static final long QUOTE_CACHE_TTL = 1; // 行情1秒缓存（配合1s推送，Python限流1req/s）
    private static final String KLINE_CACHE_PREFIX = "stock:kline:";
    private static final long KLINE_CACHE_TTL = 300; // K线5分钟缓存

    private final DataServiceClient dataServiceClient;
    private final IndicatorService indicatorService;
    private final StockInfoMapper stockInfoMapper;
    private final StockKlineMapper stockKlineMapper;
    private final UserStockMapper userStockMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<StockInfo> searchStock(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return stockInfoMapper.selectList(null);
        }
        LambdaQueryWrapper<StockInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StockInfo::getCode, keyword)
                .or()
                .like(StockInfo::getName, keyword);
        return stockInfoMapper.selectList(wrapper);
    }

    @Override
    public StockQuote getQuote(String code) {
        if (StrUtil.isBlank(code)) return null;

        // Redis缓存（Redis不可用时降级直接拉取）
        try {
            String cacheKey = QUOTE_CACHE_PREFIX + code;
            StockQuote cached = (StockQuote) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("Redis不可用，跳过缓存读取: {}", e.getMessage());
        }

        StockQuote quote = dataServiceClient.fetchQuote(code);
        if (quote != null) {
            try {
                redisTemplate.opsForValue().set(QUOTE_CACHE_PREFIX + code, quote, QUOTE_CACHE_TTL, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
        return quote;
    }

    @Override
    public List<StockQuote> getQuotes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();

        List<StockQuote> result = new ArrayList<>();
        List<String> uncached = new ArrayList<>();

        for (String code : codes) {
            try {
                String cacheKey = QUOTE_CACHE_PREFIX + code;
                StockQuote cached = (StockQuote) redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    result.add(cached);
                    continue;
                }
            } catch (Exception ignored) {}
            uncached.add(code);
        }

        if (!uncached.isEmpty()) {
            List<StockQuote> fetched = dataServiceClient.fetchQuotes(uncached);
            for (StockQuote q : fetched) {
                try {
                    redisTemplate.opsForValue().set(QUOTE_CACHE_PREFIX + q.getCode(), q, QUOTE_CACHE_TTL, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
            result.addAll(fetched);
        }
        return result;
    }

    @Override
    public List<KlineVO> getKlineData(String code, String period, int limit) {
        // 先查数据库
        List<StockKline> klineList = stockKlineMapper.selectLatestKline(code, period, limit);

        // 数据库没有则从AKShare拉取
        if (klineList.isEmpty()) {
            String aksharePeriod = switch (period) {
                case "DAY" -> "daily";
                case "WEEK" -> "weekly";
                case "MONTH" -> "monthly";
                default -> "daily";
            };
            List<StockKline> fetched = dataServiceClient.fetchKline(code, aksharePeriod, Math.max(limit, 200));

            if (!fetched.isEmpty()) {
                // 批量入库
                for (StockKline k : fetched) {
                    try { stockKlineMapper.insert(k); }
                    catch (Exception ignored) {}
                }
                klineList = fetched;
            }
        }

        if (klineList.isEmpty()) return Collections.emptyList();

        klineList.sort(Comparator.comparing(StockKline::getDate));
        if (klineList.size() > limit) {
            klineList = klineList.subList(klineList.size() - limit, klineList.size());
        }

        List<KlineVO> voList = klineList.stream()
                .map(k -> new KlineVO(
                        k.getDate(), k.getOpenPrice(), k.getClosePrice(),
                        k.getHighPrice(), k.getLowPrice(), k.getVolume(),
                        k.getAmount(), k.getChangePercent(), null, null, null))
                .collect(Collectors.toList());

        return indicatorService.fillMAIndicators(voList);
    }

    @Override
    public List<StockQuote> getWatchlist(Long userId) {
        List<UserStock> userStocks = userStockMapper.selectByUserId(userId);
        if (userStocks.isEmpty()) return Collections.emptyList();
        List<String> codes = userStocks.stream().map(UserStock::getCode).collect(Collectors.toList());
        return getQuotes(codes);
    }

    @Override
    public boolean addToWatchlist(Long userId, String code, String remark) {
        LambdaQueryWrapper<UserStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserStock::getUserId, userId).eq(UserStock::getCode, code);
        if (userStockMapper.selectCount(wrapper) > 0) return false;

        UserStock userStock = new UserStock();
        userStock.setUserId(userId);
        userStock.setCode(code);
        userStock.setRemark(remark);
        return userStockMapper.insert(userStock) > 0;
    }

    @Override
    public boolean removeFromWatchlist(Long userId, String code) {
        LambdaQueryWrapper<UserStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserStock::getUserId, userId).eq(UserStock::getCode, code);
        return userStockMapper.delete(wrapper) > 0;
    }

    @Override
    public void preloadKlineData() {
        List<UserStock> watchlist = userStockMapper.selectByUserId(1L);
        List<String> codes = watchlist.isEmpty()
                ? List.of("sh600519", "sz000001", "sz300750")
                : watchlist.stream().map(UserStock::getCode).collect(Collectors.toList());

        for (String code : codes) {
            try {
                List<StockKline> existing = stockKlineMapper.selectLatestKline(code, "DAY", 1);
                if (!existing.isEmpty()) continue;

                log.info("AKShare预加载K线: {}", code);
                String[] periods = {"daily", "weekly", "monthly"};
                for (String p : periods) {
                    List<StockKline> fetched = dataServiceClient.fetchKline(code, p, 200);
                    for (StockKline k : fetched) {
                        try { stockKlineMapper.insert(k); } catch (Exception ignored) {}
                    }
                    log.info("  {} -> {}条", p, fetched.size());
                }
            } catch (Exception e) {
                log.error("预加载K线失败 code={}", code, e);
            }
        }
    }
}
