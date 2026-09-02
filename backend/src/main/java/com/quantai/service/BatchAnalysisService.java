package com.quantai.service;

import com.quantai.dto.BatchAnalysisRequest;
import com.quantai.dto.BatchAnalysisResult;
import com.quantai.service.agent.IntelligentRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAnalysisService {

    private final IntelligentRoutingService intelligentRoutingService;
    private final ProgressService progressService;

    private final Map<String, BatchAnalysisResult> taskResults = new ConcurrentHashMap<>();

    /**
     * 批量分析股票
     */
    public String submitBatchAnalysis(BatchAnalysisRequest request) {
        String taskId = UUID.randomUUID().toString();

        BatchAnalysisResult result = BatchAnalysisResult.builder()
                .taskId(taskId)
                .results(new ConcurrentHashMap<>())
                .totalCount(request.getStockCodes().size())
                .successCount(0)
                .failureCount(0)
                .totalDurationMs(0L)
                .totalTokens(0)
                .status("RUNNING")
                .build();

        taskResults.put(taskId, result);

        if (Boolean.TRUE.equals(request.getAsync())) {
            executeBatchAsync(taskId, request);
        } else {
            executeBatchSync(taskId, request);
        }

        return taskId;
    }

    /**
     * 异步批量执行
     */
    @Async
    public void executeBatchAsync(String taskId, BatchAnalysisRequest request) {
        executeBatchSync(taskId, request);
    }

    /**
     * 同步批量执行（串行）
     */
    private void executeBatchSync(String taskId, BatchAnalysisRequest request) {
        long startTime = System.currentTimeMillis();
        BatchAnalysisResult result = taskResults.get(taskId);

        try {
            log.info("[BatchAnalysis] 任务开始 - ID: {}, 股票数: {}", taskId, request.getStockCodes().size());

            for (int i = 0; i < request.getStockCodes().size(); i++) {
                String stockCode = request.getStockCodes().get(i);

                try {
                    log.info("[BatchAnalysis] 分析股票 {}/{} - {}", i + 1, request.getStockCodes().size(), stockCode);

                    progressService.pushStageStart(taskId, stockCode, "批量分析", 10);

                    long analyzeStart = System.currentTimeMillis();
                    String analysisResult = intelligentRoutingService.analyze(stockCode, request.getQuery());
                    long analyzeDuration = System.currentTimeMillis() - analyzeStart;

                    result.getResults().put(stockCode, analysisResult);

                    if (analysisResult != null && !analysisResult.contains("失败")) {
                        result.setSuccessCount(result.getSuccessCount() + 1);
                    } else {
                        result.setFailureCount(result.getFailureCount() + 1);
                    }

                    progressService.pushComplete(taskId, stockCode, analyzeDuration, 0);

                } catch (Exception e) {
                    log.error("[BatchAnalysis] 分析失败 - {}", stockCode, e);
                    result.setFailureCount(result.getFailureCount() + 1);
                    progressService.pushError(taskId, stockCode, "分析失败: " + e.getMessage());
                }
            }

            result.setTotalDurationMs(System.currentTimeMillis() - startTime);
            result.setStatus("COMPLETED");

            log.info("[BatchAnalysis] 任务完成 - ID: {}, 成功: {}, 失败: {}, 耗时: {}ms",
                    taskId, result.getSuccessCount(), result.getFailureCount(), result.getTotalDurationMs());

        } catch (Exception e) {
            log.error("[BatchAnalysis] 任务执行失败 - ID: {}", taskId, e);
            result.setStatus("ERROR");
            result.setTotalDurationMs(System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 查询批量分析结果
     */
    public BatchAnalysisResult getBatchResult(String taskId) {
        return taskResults.get(taskId);
    }

    /**
     * 清理任务结果（可选）
     */
    public void cleanupTask(String taskId) {
        taskResults.remove(taskId);
    }
}
