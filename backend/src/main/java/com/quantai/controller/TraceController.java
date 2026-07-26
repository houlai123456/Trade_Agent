package com.quantai.controller;

import com.quantai.model.entity.AgentTrace;
import com.quantai.service.AgentTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 追踪调试接口
 * 用于查看 LLM 调用记录、耗时、Token 消耗和失败快照
 */
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TraceController {

    private final AgentTraceService traceService;

    /** 获取最近追踪列表 */
    @GetMapping("/recent")
    public ResponseEntity<List<AgentTrace>> getRecentTraces(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(traceService.getRecentTraces(limit));
    }

    /** 按类型过滤 */
    @GetMapping("/type/{traceType}")
    public ResponseEntity<List<AgentTrace>> getByType(
            @PathVariable String traceType,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(traceService.getTracesByType(traceType, limit));
    }

    /** 按股票代码过滤 */
    @GetMapping("/stock/{stockCode}")
    public ResponseEntity<List<AgentTrace>> getByStock(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(traceService.getTracesByStock(stockCode, limit));
    }

    /** 获取失败的调用 */
    @GetMapping("/failed")
    public ResponseEntity<List<AgentTrace>> getFailed(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(traceService.getFailedTraces(limit));
    }

    /** 获取单条详情 */
    @GetMapping("/{id}")
    public ResponseEntity<AgentTrace> getById(@PathVariable String id) {
        AgentTrace trace = traceService.getTraceById(id);
        if (trace == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(trace);
    }

    /** 获取统计概览 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(traceService.getStats());
    }
}
