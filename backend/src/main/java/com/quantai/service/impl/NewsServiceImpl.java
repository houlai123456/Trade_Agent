package com.quantai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quantai.mapper.NewsMapper;
import com.quantai.model.entity.News;
import com.quantai.model.vo.NewsVO;
import com.quantai.service.AiAnalysisService;
import com.quantai.service.DataServiceClient;
import com.quantai.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {

    private final NewsMapper newsMapper;
    private final DataServiceClient dataServiceClient;
    private final AiAnalysisService aiAnalysisService;

    /** 从AKShare拉取新闻并保存，对前N条自动做AI情感分析 */
    private void fetchAndSaveNews(String stockCode) {
        List<Map<String, Object>> fetched = dataServiceClient.fetchNews(stockCode);
        if (fetched == null || fetched.isEmpty()) return;
        // 查找股票名称，所有新闻共用
        String stockName = null;
        try {
            List<Map<String, Object>> result = dataServiceClient.searchStock(stockCode);
            if (!result.isEmpty()) {
                stockName = (String) result.get(0).get("name");
            }
        } catch (Exception e) {
            log.warn("查找股票名称失败 code={}", stockCode, e);
        }
        for (int i = 0; i < fetched.size(); i++) {
            Map<String, Object> item = fetched.get(i);
            News news = new News();
            news.setTitle((String) item.get("title"));
            news.setSummary((String) item.get("summary"));
            news.setContent((String) item.get("content"));
            news.setSource((String) item.get("source"));
            news.setUrl((String) item.get("url"));
            news.setStockCode(stockCode);
            news.setStockName(stockName);
            String pubTime = (String) item.get("publish_time");
            if (StrUtil.isNotBlank(pubTime)) {
                try {
                    news.setPublishTime(LocalDateTime.parse(pubTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (Exception e) {
                    news.setPublishTime(LocalDateTime.now());
                }
            }
            try {
                newsMapper.insert(news);
                // 异步做AI情感分析，不阻塞API返回
                if (StrUtil.isNotBlank(news.getTitle())) {
                    final News saved = newsMapper.selectById(news.getId());
                    if (saved != null) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                String result = aiAnalysisService.analyzeSentiment(saved.getTitle(), saved.getContent());
                                if (StrUtil.isNotBlank(result)) {
                                    cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(result);
                                    saved.setSentiment(json.getStr("sentiment", "NEUTRAL"));
                                    saved.setSentimentScore(json.getDouble("score", 0.0));
                                    if (json.containsKey("summary")) {
                                        saved.setSummary(json.getStr("summary"));
                                    }
                                    if (json.containsKey("affected_stocks")) {
                                        saved.setAffectedStocks(json.getJSONArray("affected_stocks").toString());
                                    }
                                    newsMapper.updateById(saved);
                                }
                            } catch (Exception e) {
                                log.warn("AI情感分析失败 newsId={}, title={}", saved.getId(), StrUtil.sub(saved.getTitle(), 0, 30), e);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public List<NewsVO> getNewsByStock(String stockCode, int limit) {
        // 先查数据库
        List<News> dbNews = newsMapper.selectByStockCode(stockCode, limit);
        if (!dbNews.isEmpty()) return convertToVO(dbNews);

        // 数据库没有，从AKShare拉取
        fetchAndSaveNews(stockCode);

        return convertToVO(newsMapper.selectByStockCode(stockCode, limit));
    }

    @Override
    public List<NewsVO> getLatestNews(int limit) {
        // 数据库为空时先预拉一些热门股票的新闻
        long count = newsMapper.selectCount(null);
        if (count == 0) {
            String[] hotCodes = {"600519", "000001", "300750", "000333", "002415"};
            for (String code : hotCodes) {
                try {
                    fetchAndSaveNews(code);
                } catch (Exception e) {
                    log.warn("预拉新闻失败 code={}", code, e);
                }
            }
        }
        LambdaQueryWrapper<News> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(News::getPublishTime).last("LIMIT " + limit);
        return convertToVO(newsMapper.selectList(wrapper));
    }

    @Override
    public News getNewsDetail(Long id) {
        return newsMapper.selectById(id);
    }

    @Override
    public News analyzeSentiment(News news) {
        if (news == null || StrUtil.isBlank(news.getTitle())) return news;
        try {
            String result = aiAnalysisService.analyzeSentiment(news.getTitle(), news.getContent());
            if (StrUtil.isNotBlank(result)) {
                cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(result);
                news.setSentiment(json.getStr("sentiment", "NEUTRAL"));
                news.setSentimentScore(json.getDouble("score", 0.0));
                if (json.containsKey("summary")) {
                    news.setSummary(json.getStr("summary"));
                }
                if (json.containsKey("affected_stocks")) {
                    news.setAffectedStocks(json.getJSONArray("affected_stocks").toString());
                }
                newsMapper.updateById(news);
            }
        } catch (Exception e) {
            log.error("AI情绪分析失败 newsId={}", news.getId(), e);
        }
        return news;
    }

    private List<NewsVO> convertToVO(List<News> newsList) {
        if (newsList == null || newsList.isEmpty()) return Collections.emptyList();
        return newsList.stream().map(n -> {
            NewsVO vo = new NewsVO();
            vo.setId(n.getId());
            vo.setTitle(n.getTitle());
            vo.setSummary(n.getSummary());
            vo.setSource(n.getSource());
            vo.setUrl(n.getUrl());
            vo.setStockCode(n.getStockCode());
            vo.setStockName(n.getStockName());
            vo.setSentiment(n.getSentiment());
            vo.setAffectedStocks(n.getAffectedStocks());
            vo.setPublishTime(n.getPublishTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
