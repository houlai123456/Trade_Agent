package com.quantai.controller;

import com.quantai.model.entity.News;
import com.quantai.model.vo.NewsVO;
import com.quantai.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /**
     * 根据股票代码获取新闻
     * GET /api/news/stock/{stockCode}?limit=20
     */
    @GetMapping("/stock/{stockCode}")
    public ResponseEntity<List<NewsVO>> getNewsByStock(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(newsService.getNewsByStock(stockCode, limit));
    }

    /**
     * 获取最新新闻
     * GET /api/news/latest?limit=20
     */
    @GetMapping("/latest")
    public ResponseEntity<List<NewsVO>> getLatestNews(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(newsService.getLatestNews(limit));
    }

    /**
     * 获取新闻详情
     * GET /api/news/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<News> getNewsDetail(@PathVariable Long id) {
        News news = newsService.getNewsDetail(id);
        if (news == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(news);
    }

    /**
     * 分析新闻情绪
     * POST /api/news/{id}/sentiment
     */
    @PostMapping("/{id}/sentiment")
    public ResponseEntity<Map<String, Object>> analyzeSentiment(@PathVariable Long id) {
        News news = newsService.getNewsDetail(id);
        if (news == null) {
            return ResponseEntity.notFound().build();
        }
        news = newsService.analyzeSentiment(news);
        return ResponseEntity.ok(Map.of(
                "sentiment", news.getSentiment(),
                "score", news.getSentimentScore()
        ));
    }
}
