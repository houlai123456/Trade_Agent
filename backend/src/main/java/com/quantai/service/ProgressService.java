package com.quantai.service;

import com.quantai.dto.AnalysisProgress;
import com.quantai.websocket.StockWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 分析进度推送服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final StockWebSocketHandler webSocketHandler;

    public void pushProgress(AnalysisProgress progress) {
        try {
            webSocketHandler.broadcast(new ProgressMessage("ANALYSIS_PROGRESS", progress));
            log.debug("推送分析进度 - 任务: {}, 股票: {}, 进度: %",
                    progress.getTaskId(), progress.getStockCode(), progress.getProgress());
        } catch (Exception e) {
            log.error("推送进度失败", e);
        }
    }

    public void pushStageStart(String taskId, String stockCode, String stage, int totalAgents) {
        AnalysisProgress progress = AnalysisProgress.builder()
                .taskId(taskId)
                .stockCode(stockCode)
                .stage(stage)
                .completedAgents(0)
                .totalAgents(totalAgents)
                .progress(0)
                .status("RUNNING")
                .message("开始执行" + stage)
                .build();
        pushProgress(progress);
    }

    public void pushAgentStart(String taskId, String stockCode, String stage,
                               String agentName, int completedAgents, int totalAgents) {
        int progress = (completedAgents * 100) / totalAgents;
        AnalysisProgress progressDto = AnalysisProgress.builder()
                .taskId(taskId)
                .stockCode(stockCode)
                .stage(stage)
                .currentAgent(agentName)
                .completedAgents(completedAgents)
                .totalAgents(totalAgents)
                .progress(progress)
                .status("RUNNING")
                .message("正在执行: " + agentName)
                .build();
        pushProgress(progressDto);
    }

    public void pushAgentComplete(String taskId, String stockCode, String stage,
                                  String agentName, int completedAgents, int totalAgents,
                                  long durationMs, int tokenUsed) {
        int progress = (completedAgents * 100) / totalAgents;
        AnalysisProgress progressDto = AnalysisProgress.builder()
                .taskId(taskId)
                .stockCode(stockCode)
                .stage(stage)
                .currentAgent(agentName)
                .completedAgents(completedAgents)
                .totalAgents(totalAgents)
                .progress(progress)
                .status("RUNNING")
                .message(agentName + " 完成")
                .durationMs(durationMs)
                .tokenUsed(tokenUsed)
                .build();
        pushProgress(progressDto);
    }

    public void pushComplete(String taskId, String stockCode, long totalDurationMs, int totalTokens) {
        AnalysisProgress progress = AnalysisProgress.builder()
                .taskId(taskId)
                .stockCode(stockCode)
                .progress(100)
                .status("COMPLETED")
                .message("分析完成")
                .durationMs(totalDurationMs)
                .tokenUsed(totalTokens)
                .build();
        pushProgress(progress);
    }

    public void pushError(String taskId, String stockCode, String errorMessage) {
        AnalysisProgress progress = AnalysisProgress.builder()
                .taskId(taskId)
                .stockCode(stockCode)
                .status("ERROR")
                .message(errorMessage)
                .build();
        pushProgress(progress);
    }

    private record ProgressMessage(String type, AnalysisProgress data) {
    }
}
