package com.quantai.controller;

import com.quantai.common.Result;
import com.quantai.model.vo.CollaborationResult;
import com.quantai.security.InputFilter;
import com.quantai.service.ChatSessionService;
import com.quantai.service.agent.AgentCoordinatorService;
import com.quantai.service.agent.PlanAndExecuteService;
import com.quantai.service.agent.ReActAgentService;
import com.quantai.service.agent.ReActResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentCollaborationController {

    private final AgentCoordinatorService agentCoordinatorService;
    private final ReActAgentService reActAgentService;
    private final PlanAndExecuteService planAndExecuteService;
    private final ChatSessionService sessionService;

    @PostMapping("/collaborate")
    public Result<CollaborationResult> collaborate(@RequestBody Map<String, String> body) {
        String stockCode = body.get("stockCode");
        if (stockCode == null || stockCode.isBlank()) {
            return Result.error("stockCode不能为空");
        }
        String newsTitle = body.get("newsTitle");
        String newsContent = body.get("newsContent");

        String sessionId = resolveSessionId(body.get("sessionId"));
        sessionService.saveMessage(sessionId, "user", "分析股票 " + stockCode, "analysis",
                null, metaJson(stockCode));

        CollaborationResult result = agentCoordinatorService.collaborate(stockCode, newsTitle, newsContent);

        String summary = result.getSuggestion() != null ? result.getSuggestion().getReason() : null;
        if (summary != null) {
            sessionService.saveMessage(sessionId, "assistant", summary, "analysis", summary.length(), metaJson(stockCode));
            result.setSessionId(sessionId);
        }
        return Result.success(result);
    }

    /**
     * ReAct Agent - 自主分析
     */
    @PostMapping("/react")
    public Result<ReActResult> react(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.error("message不能为空");
        }

        InputFilter.FilterResult filter = InputFilter.check(message);
        if (filter.sanitizedInput != null) {
            message = filter.sanitizedInput;
        }

        String sessionId = resolveSessionId(body.get("sessionId"));
        sessionService.saveMessage(sessionId, "user", message, "react", message.length(), null);

        ReActResult result = reActAgentService.run(message);
        if (result.getFinalAnswer() != null) {
            sessionService.saveMessage(sessionId, "assistant", result.getFinalAnswer(), "react",
                    result.getFinalAnswer().length(), null);
        }
        return Result.success(result);
    }

    /**
     * 智能分析 — 自动判断复杂度，选择 Plan-and-Execute 或 ReAct
     * POST /api/agent/analyze
     * {"message": "全面分析对比茅台和五粮液"}
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyze(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.error("message不能为空");
        }

        InputFilter.FilterResult filter = InputFilter.check(message);
        if (filter.sanitizedInput != null) {
            message = filter.sanitizedInput;
        }

        String sessionId = resolveSessionId(body.get("sessionId"));

        Map<String, Object> response = new LinkedHashMap<>();

        // 判断是否适合 Plan-and-Execute
        if (planAndExecuteService.shouldPlan(message)) {
            PlanAndExecuteService.PlanExecuteResult result = planAndExecuteService.execute(message);
            if (result != null && result.getFinalAnswer() != null) {
                sessionService.saveMessage(sessionId, "user", message, "plan", message.length(), null);
                sessionService.saveMessage(sessionId, "assistant", result.getFinalAnswer(), "plan",
                        result.getFinalAnswer().length(), null);
                response.put("mode", "plan-execute");
                response.put("plan", result.getPlan().stream()
                        .map(p -> Map.of("step", p.getStep(), "goal", p.getGoal())).toList());
                response.put("finalAnswer", result.getFinalAnswer());
                response.put("totalDurationMs", result.getTotalDurationMs());
                response.put("sessionId", sessionId);
                return Result.success(response);
            }
            // Plan退化 → 走ReAct
        }

        // 简单问题直接ReAct
        sessionService.saveMessage(sessionId, "user", message, "react", message.length(), null);
        ReActResult reactResult = reActAgentService.run(message);
        if (reactResult.getFinalAnswer() != null) {
            sessionService.saveMessage(sessionId, "assistant", reactResult.getFinalAnswer(), "react",
                    reactResult.getFinalAnswer().length(), null);
        }
        response.put("mode", "react");
        response.put("finalAnswer", reactResult.getFinalAnswer());
        response.put("totalRounds", reactResult.getTotalRounds());
        response.put("totalDurationMs", reactResult.getTotalDurationMs());
        response.put("sessionId", sessionId);
        return Result.success(response);
    }

    private String resolveSessionId(String providedId) {
        if (providedId != null && !providedId.isBlank()) return providedId;
        return "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String metaJson(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) return null;
        return "{\"stockCode\":\"" + stockCode + "\"}";
    }
}
