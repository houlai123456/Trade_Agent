package com.quantai.controller;

import com.quantai.common.Result;
import com.quantai.model.vo.CollaborationResult;
import com.quantai.service.agent.AgentCoordinatorService;
import com.quantai.service.agent.ReActAgentService;
import com.quantai.service.agent.ReActResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多Agent协同分析
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentCollaborationController {

    private final AgentCoordinatorService agentCoordinatorService;
    private final ReActAgentService reActAgentService;

    @PostMapping("/collaborate")
    public Result<CollaborationResult> collaborate(@RequestBody Map<String, String> body) {
        String stockCode = body.get("stockCode");
        if (stockCode == null || stockCode.isBlank()) {
            return Result.error("stockCode不能为空");
        }
        String newsTitle = body.get("newsTitle");
        String newsContent = body.get("newsContent");

        CollaborationResult result = agentCoordinatorService.collaborate(stockCode, newsTitle, newsContent);
        return Result.success(result);
    }

    /**
     * ReAct Agent — 自主分析
     * POST /api/agent/react
     * {"message": "分析一下贵州茅台"}
     */
    @PostMapping("/react")
    public Result<ReActResult> react(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.error("message不能为空");
        }
        ReActResult result = reActAgentService.run(message);
        return Result.success(result);
    }
}
