package com.quantai.service;

import com.quantai.model.entity.StockInfo;
import com.quantai.model.entity.StockQuote;
import com.quantai.model.entity.UserStock;
import com.quantai.model.vo.KlineVO;

import java.util.List;

/**
 * 股票行情数据服务
 */
public interface StockService {

    /**
     * 搜索股票（按代码或名称模糊匹配）
     */
    List<StockInfo> searchStock(String keyword);

    /**
     * 获取实时行情
     */
    StockQuote getQuote(String code);

    /**
     * 批量获取行情
     */
    List<StockQuote> getQuotes(List<String> codes);

    /**
     * 获取K线数据
     * @param code   股票代码
     * @param period 日K:DAY 周K:WEEK 月K:MONTH
     * @param limit  数据条数
     */
    List<KlineVO> getKlineData(String code, String period, int limit);

    /**
     * 获取自选股列表
     */
    List<StockQuote> getWatchlist(Long userId);

    /**
     * 添加自选股
     */
    boolean addToWatchlist(Long userId, String code, String remark);

    /**
     * 删除自选股
     */
    boolean removeFromWatchlist(Long userId, String code);

    /**
     * 预加载K线数据到数据库（从新浪API拉取并缓存）
     */
    void preloadKlineData();
}
