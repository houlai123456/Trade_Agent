package com.quantai.controller;

import com.quantai.common.Result;
import com.quantai.security.InputFilter;
import com.quantai.service.ChatSessionService;
import com.quantai.service.agent.IntelligentRoutingService;
import com.quantai.service.agent.ReActAgentService;
import com.quantai.service.agent.ReActResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent协作接口（维度型架构）
 * - ReAct: 快速查询（5秒）
 * - Analyze: 智能三档路由
 *   - 简单查询 → ReAct（5秒）
 *   - 单维度分析 → 单个维度Agent（20秒）
 *   - 完整分析 → 三维度并行 + 投票决策（60秒）
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentCollaborationController {

    private final ReActAgentService reActAgentService;
    private final ChatSessionService sessionService;
    private final IntelligentRoutingService intelligentRoutingService;

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
     * 智能分析 — 三档路由
     * 1. 简单查询（价格、PE等）→ ReAct（5秒）
     * 2. 单维度分析（财务、技术、情绪）→ 单个Agent（20秒）
     * 3. 完整分析（投资价值、建议等）→ 三维度并行 + 投票（60秒）
     *
     * POST /api/agent/analyze
     * {"message": "茅台(sh600519)的财务健康吗"}
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyze(@RequestBody Map<String, String> body) {
        long startTime = System.currentTimeMillis();

        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Result.error("message不能为空");
        }

        InputFilter.FilterResult filter = InputFilter.check(message);
        if (filter.sanitizedInput != null) {
            message = filter.sanitizedInput;
        }

        String sessionId = resolveSessionId(body.get("sessionId"));

        // 提取股票代码
        String stockCode = extractStockCode(message);
        if (stockCode == null) {
            log.warn("未找到股票代码: {}", message);
            return Result.error("请在消息中指定股票代码，例如：茅台(sh600519)的财务健康吗");
        }

        sessionService.saveMessage(sessionId, "user", message, "intelligent_routing", message.length(), null);

        try {
            // 调用智能路由服务
            String result = intelligentRoutingService.analyze(stockCode, message);

            long duration = System.currentTimeMillis() - startTime;

            sessionService.saveMessage(sessionId, "assistant", result, "intelligent_routing",
                    result.length(), null);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("finalAnswer", result);
            response.put("totalDurationMs", duration);
            response.put("sessionId", sessionId);
            response.put("stockCode", stockCode);

            return Result.success(response);

        } catch (Exception e) {
            log.error("智能分析失败", e);
            return Result.error("分析失败: " + e.getMessage());
        }
    }

    /**
     * 从消息中提取股票代码（格式：sh600519、sz000001等）
     */
    private String extractStockCode(String message) {
        // 匹配格式：sh600519、sz000001、600519等
        Pattern pattern = Pattern.compile("(sh|sz)?([0-9]{6})");
        Matcher matcher = pattern.matcher(message.toLowerCase());

        if (matcher.find()) {
            String prefix = matcher.group(1);
            String code = matcher.group(2);

            // 如果有前缀，直接返回
            if (prefix != null) {
                return prefix + code;
            }

            // 如果没有前缀，根据代码规则推断
            // 6开头 → 上海（sh），0/3开头 → 深圳（sz）
            if (code.startsWith("6")) {
                return "sh" + code;
            } else if (code.startsWith("0") || code.startsWith("3")) {
                return "sz" + code;
            }
            return code;
        }

        return null;
    }

    private String resolveSessionId(String providedId) {
        if (providedId != null && !providedId.isBlank()) return providedId;
        return "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
