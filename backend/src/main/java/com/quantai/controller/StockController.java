package com.quantai.controller;

import com.quantai.model.entity.StockInfo;
import com.quantai.model.entity.StockQuote;
import com.quantai.model.vo.KlineVO;
import com.quantai.service.AiAnalysisService;
import com.quantai.service.DataServiceClient;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final DataServiceClient dataServiceClient;
    private final AiAnalysisService aiAnalysisService;

    /**
     * 诊断端点：验证数据服务是否正常
     * GET /api/stock/check/sh600519
     */
    @GetMapping("/check/{code}")
    public ResponseEntity<Map<String, Object>> checkApi(@PathVariable String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", java.time.LocalDateTime.now().toString());

        // 检查Python服务
        boolean pythonOk = dataServiceClient.healthCheck();
        result.put("python_service", pythonOk ? "running" : "NOT_RUNNING");

        // 获取行情验证
        StockQuote quote = stockService.getQuote(code);
        result.put("code", code);
        result.put("data_fetched", quote != null);
        if (quote != null) {
            result.put("name", quote.getName());
            result.put("currentPrice", quote.getCurrentPrice());
            result.put("changePercent", quote.getChangePercent());
        } else {
            result.put("error", "未能获取到数据，请确认Python服务已启动: python data_service.py");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 搜索股票
     * GET /api/stock/search?keyword=茅台
     */
    @GetMapping("/search")
    public ResponseEntity<List<StockInfo>> searchStock(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(stockService.searchStock(keyword));
    }

    /**
     * 获取实时行情
     * GET /api/stock/quote/{code}
     */
    @GetMapping("/quote/{code}")
    public ResponseEntity<StockQuote> getQuote(@PathVariable String code) {
        StockQuote quote = stockService.getQuote(code);
        if (quote == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(quote);
    }

    /**
     * 批量获取行情
     * GET /api/stock/quotes?codes=sh600519,sz000001
     */
    @GetMapping("/quotes")
    public ResponseEntity<List<StockQuote>> getQuotes(@RequestParam List<String> codes) {
        return ResponseEntity.ok(stockService.getQuotes(codes));
    }

    /**
     * 获取K线数据
     * GET /api/stock/kline/{code}?period=DAY&limit=120
     */
    @GetMapping("/kline/{code}")
    public ResponseEntity<List<KlineVO>> getKline(
            @PathVariable String code,
            @RequestParam(defaultValue = "DAY") String period,
            @RequestParam(defaultValue = "120") int limit) {
        return ResponseEntity.ok(stockService.getKlineData(code, period, limit));
    }

    /**
     * 获取自选股列表
     * GET /api/stock/watchlist
     */
    @GetMapping("/watchlist")
    public ResponseEntity<List<StockQuote>> getWatchlist() {
        return ResponseEntity.ok(stockService.getWatchlist(1L));
    }

    /**
     * 添加自选股
     * POST /api/stock/watchlist
     */
    @PostMapping("/watchlist")
    public ResponseEntity<Map<String, Object>> addWatchlist(@RequestBody Map<String, String> params) {
        String code = params.get("code");
        String remark = params.get("remark");
        boolean success = stockService.addToWatchlist(1L, code, remark);
        return ResponseEntity.ok(Map.of("success", success));
    }

    /**
     * 删除自选股
     * DELETE /api/stock/watchlist/{code}
     */
    @DeleteMapping("/watchlist/{code}")
    public ResponseEntity<Map<String, Object>> removeWatchlist(@PathVariable String code) {
        boolean success = stockService.removeFromWatchlist(1L, code);
        return ResponseEntity.ok(Map.of("success", success));
    }

    /**
     * 获取板块股票列表（分页）
     * GET /api/stock/board/{board}?page=1&size=10
     * board: main(主板) chiNext(创业板) star(科创板) bj(北交所)
     */
    @GetMapping("/board/{board}")
    public ResponseEntity<Map<String, Object>> getBoardStocks(
            @PathVariable String board,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> result = dataServiceClient.fetchBoardStocksWithTotal(board, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取主要指数行情
     * GET /api/stock/index
     */
    @GetMapping("/index")
    public ResponseEntity<List<Map<String, Object>>> getIndexQuotes() {
        return ResponseEntity.ok(dataServiceClient.fetchIndexQuotes());
    }

    /**
     * 获取市场概况
     * GET /api/stock/market/overview
     */
    @GetMapping("/market/overview")
    public ResponseEntity<Map<String, Object>> getMarketOverview() {
        return ResponseEntity.ok(dataServiceClient.fetchMarketOverview());
    }

    /**
     * 获取热点板块排名
     * GET /api/stock/hot-boards
     */
    @GetMapping("/hot-boards")
    public ResponseEntity<List<Map<String, Object>>> getHotBoards() {
        return ResponseEntity.ok(dataServiceClient.fetchHotBoards());
    }

    /**
     * 获取指数分时数据
     * GET /api/stock/index/intraday/{code}
     */
    @GetMapping("/index/intraday/{code}")
    public ResponseEntity<List<Map<String, Object>>> getIndexIntraday(@PathVariable String code) {
        return ResponseEntity.ok(dataServiceClient.fetchIndexIntraday(code));
    }

    /**
     * AI财报解读
     * GET /api/stock/finance/analysis/{code}
     */
    @GetMapping("/finance/analysis/{code}")
    public ResponseEntity<Map<String, String>> analyzeFinance(@PathVariable String code) {
        String report = aiAnalysisService.analyzeFinance(code);
        return ResponseEntity.ok(Map.of("code", code, "report", report));
    }

    /**
     * AI财报对比
     * GET /api/stock/finance/compare/{code1}/{code2}
     */
    @GetMapping("/finance/compare/{code1}/{code2}")
    public ResponseEntity<Map<String, String>> analyzeFinanceCompare(
            @PathVariable String code1, @PathVariable String code2) {
        String report = aiAnalysisService.analyzeFinanceCompare(code1, code2);
        return ResponseEntity.ok(Map.of("code1", code1, "code2", code2, "report", report));
    }
}
