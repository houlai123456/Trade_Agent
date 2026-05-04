package com.quantai.service;

import com.quantai.model.entity.News;
import com.quantai.model.vo.NewsVO;

import java.util.List;

public interface NewsService {

    /**
     * 根据股票代码获取相关新闻
     */
    List<NewsVO> getNewsByStock(String stockCode, int limit);

    /**
     * 获取最新新闻列表
     */
    List<NewsVO> getLatestNews(int limit);

    /**
     * 获取新闻详情
     */
    News getNewsDetail(Long id);

    /**
     * 使用AI分析新闻情绪
     */
    News analyzeSentiment(News news);
}
