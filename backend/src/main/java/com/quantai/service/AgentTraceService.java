package com.quantai.service;

import com.quantai.model.entity.AgentTrace;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Agent 调用追踪服务
 * 内存环形缓冲区 + 查询接口，保留最近 1000 条追踪记录
 * 后续可扩展：写入数据库、上报 Prometheus、对接分布式追踪
 */
@Slf4j
@Service
public class AgentTraceService {

    private static final int MAX_TRACES = 1000;
    private final ConcurrentLinkedDeque<AgentTrace> traces = new ConcurrentLinkedDeque<>();
    private final AtomicLong seq = new AtomicLong(0);

    // ====== 统计计数器 ======
    private final AtomicLong totalLlmCalls = new AtomicLong(0);
    private final AtomicLong failedLlmCalls = new AtomicLong(0);
    private final AtomicLong totalTokens = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    @PostConstruct
    public void init() {
        log.info("AgentTraceService 已初始化，环形缓冲区容量={}", MAX_TRACES);
    }

    /** 记录一条追踪 */
    public void record(AgentTrace trace) {
        if (trace.getId() == null) {
            trace.setId("trace_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "_" + seq.incrementAndGet());
        }
        if (trace.getStartTime() == null) trace.setStartTime(LocalDateTime.now());
        if (trace.getEndTime() == null) trace.setEndTime(LocalDateTime.now());
        trace.setDurationMs(java.time.Duration.between(trace.getStartTime(), trace.getEndTime()).toMillis());

        traces.addFirst(trace);
        if (traces.size() > MAX_TRACES) {
            traces.pollLast();
        }

        // 更新统计
        totalLlmCalls.incrementAndGet();
        if (!trace.isSuccess()) failedLlmCalls.incrementAndGet();
        if (trace.getPromptTokens() != null && trace.getCompletionTokens() != null) {
            totalTokens.addAndGet(trace.getPromptTokens() + trace.getCompletionTokens());
        }
        totalDurationMs.addAndGet(trace.getDurationMs());
    }

    /** 构建失败快照 */
    public static AgentTrace buildFailureSnapshot(String traceType, String stockCode,
                                                  String userMessage, String snapshotContext,
                                                  String snapshotRawResponse, String errorMessage) {
        AgentTrace trace = new AgentTrace();
        trace.setTraceType(traceType);
        trace.setStockCode(stockCode);
        trace.setUserMessage(truncate(userMessage, 100));
        trace.setStartTime(LocalDateTime.now());
        trace.setEndTime(LocalDateTime.now());
        trace.setSuccess(false);
        trace.setErrorMessage(truncate(errorMessage, 500));
        trace.setSnapshotContext(truncate(snapshotContext, 500));
        trace.setSnapshotRawResponse(truncate(snapshotRawResponse, 500));
        return trace;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(已截断)";
    }

    // ====== 查询接口 ======

    /** 获取最近 N 条追踪 */
    public List<AgentTrace> getRecentTraces(int limit) {
        return traces.stream().limit(Math.min(limit, MAX_TRACES)).collect(Collectors.toList());
    }

    /** 按类型过滤 */
    public List<AgentTrace> getTracesByType(String traceType, int limit) {
        return traces.stream()
                .filter(t -> traceType.equals(t.getTraceType()))
                .limit(Math.min(limit, MAX_TRACES))
                .collect(Collectors.toList());
    }

    /** 按股票代码过滤 */
    public List<AgentTrace> getTracesByStock(String stockCode, int limit) {
        return traces.stream()
                .filter(t -> stockCode.equals(t.getStockCode()))
                .limit(Math.min(limit, MAX_TRACES))
                .collect(Collectors.toList());
    }

    /** 查询最近失败的追踪 */
    public List<AgentTrace> getFailedTraces(int limit) {
        return traces.stream()
                .filter(t -> !t.isSuccess())
                .limit(Math.min(limit, MAX_TRACES))
                .collect(Collectors.toList());
    }

    /** 按时间范围过滤 */
    public List<AgentTrace> getTracesByTimeRange(LocalDateTime from, LocalDateTime to, int limit) {
        return traces.stream()
                .filter(t -> t.getStartTime() != null
                        && !t.getStartTime().isBefore(from)
                        && !t.getStartTime().isAfter(to))
                .limit(Math.min(limit, MAX_TRACES))
                .collect(Collectors.toList());
    }

    /** 获取单条追踪详情 */
    public AgentTrace getTraceById(String id) {
        return traces.stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);
    }

    // ====== 统计接口 ======

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalLlmCalls", totalLlmCalls.get());
        stats.put("failedLlmCalls", failedLlmCalls.get());
        stats.put("failureRate", totalLlmCalls.get() > 0
                ? String.format("%.2f%%", failedLlmCalls.get() * 100.0 / totalLlmCalls.get())
                : "0%");
        stats.put("totalTokens", totalTokens.get());
        stats.put("averageDurationMs", totalLlmCalls.get() > 0
                ? totalDurationMs.get() / totalLlmCalls.get()
                : 0);

        // 按类型统计
        Map<String, Long> typeCount = traces.stream()
                .collect(Collectors.groupingBy(AgentTrace::getTraceType, Collectors.counting()));
        stats.put("typeCount", typeCount);

        // 按类型统计失败数
        Map<String, Long> typeFailCount = traces.stream()
                .filter(t -> !t.isSuccess())
                .collect(Collectors.groupingBy(AgentTrace::getTraceType, Collectors.counting()));
        stats.put("typeFailCount", typeFailCount);

        return stats;
    }
}
