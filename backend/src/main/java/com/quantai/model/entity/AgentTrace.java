package com.quantai.model.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 调用追踪记录
 * 记录每次 LLM 调用的完整链路：耗时、Token、工具调用链、成功/失败快照
 */
public class AgentTrace {

    private String id;                     // 唯一ID: trace_{timestamp}_{seq}
    private String traceType;              // REACT | SUGGESTION | SENTIMENT | FINANCE | STOCK_ANALYSIS | CHAT
    private String stockCode;              // 关联股票代码
    private String userMessage;            // 用户输入摘要（前100字）
    private LocalDateTime startTime;       // 调用开始时间
    private LocalDateTime endTime;         // 调用结束时间
    private long durationMs;               // 总耗时
    private Integer promptTokens;          // 输入 Token 数
    private Integer completionTokens;      // 输出 Token 数
    private String model;                  // 模型名称
    private boolean success;               // 是否成功
    private String errorMessage;           // 失败原因

    // ReAct 专用
    private Integer totalRounds;           // 总轮数
    private List<StepDetail> steps;        // 每步详情

    // 失败快照
    private String snapshotContext;         // 失败时的对话上下文（前500字）
    private String snapshotRawResponse;     // 失败时的 LLM 原始响应

    // 额外标签
    private Map<String, String> tags;      // 自定义标签，如 {"source":"ReActAgent","iteration":"3"}

    /** ReAct 单步详情 */
    public static class StepDetail {
        private int round;
        private String thought;
        private String action;
        private String toolName;
        private long toolDurationMs;
        private String observationSummary;  // 前200字
        private boolean toolSuccess;

        public int getRound() { return round; }
        public void setRound(int round) { this.round = round; }
        public String getThought() { return thought; }
        public void setThought(String thought) { this.thought = thought; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public long getToolDurationMs() { return toolDurationMs; }
        public void setToolDurationMs(long toolDurationMs) { this.toolDurationMs = toolDurationMs; }
        public String getObservationSummary() { return observationSummary; }
        public void setObservationSummary(String observationSummary) { this.observationSummary = observationSummary; }
        public boolean isToolSuccess() { return toolSuccess; }
        public void setToolSuccess(boolean toolSuccess) { this.toolSuccess = toolSuccess; }
    }

    // ====== Getters / Setters ======

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTraceType() { return traceType; }
    public void setTraceType(String traceType) { this.traceType = traceType; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getTotalRounds() { return totalRounds; }
    public void setTotalRounds(Integer totalRounds) { this.totalRounds = totalRounds; }
    public List<StepDetail> getSteps() { return steps; }
    public void setSteps(List<StepDetail> steps) { this.steps = steps; }
    public String getSnapshotContext() { return snapshotContext; }
    public void setSnapshotContext(String snapshotContext) { this.snapshotContext = snapshotContext; }
    public String getSnapshotRawResponse() { return snapshotRawResponse; }
    public void setSnapshotRawResponse(String snapshotRawResponse) { this.snapshotRawResponse = snapshotRawResponse; }
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
}
