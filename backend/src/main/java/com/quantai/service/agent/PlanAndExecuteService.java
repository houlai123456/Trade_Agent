package com.quantai.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.config.PromptsConfig;
import com.quantai.model.entity.AgentTrace;
import com.quantai.service.AgentTraceService;
import com.quantai.service.agent.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Plan-and-Execute 模式
 * 检测复杂多步骤意图 → 先规划 → 分步执行 → 汇总
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanAndExecuteService {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final AgentTraceService traceService;
    private final PromptsConfig promptsConfig;

    private static final int MAX_PLAN_STEPS = 5;

    /** 判断是否需要进入 Plan 模式 */
    public boolean shouldPlan(String userMessage) {
        if (userMessage == null) return false;
        String lower = userMessage.toLowerCase();
        String[] planKeywords = {
            "全面分析", "综合分析", "对比", "排序", "先", "然后", "接着", "最后",
            "步骤", "流程", "选择", "筛选", "排名", "汇总", "总结",
            "comprehensive", "compare", "first", "then", "finally", "step"
        };
        int hits = 0;
        for (String kw : planKeywords) {
            if (lower.contains(kw)) hits++;
        }
        return hits >= 2;
    }

    /** Plan-and-Execute 主流程 */
    public PlanExecuteResult execute(String userMessage) {
        AgentTrace agentTrace = new AgentTrace();
        agentTrace.setTraceType("PLAN_EXECUTE");
        agentTrace.setUserMessage(truncate(userMessage, 100));
        agentTrace.setStartTime(LocalDateTime.now());

        long start = System.currentTimeMillis();
        List<PlanStep> plan = null;
        List<StepResult> results = new ArrayList<>();
        String finalAnswer = null;

        try {
            // 第1阶段：规划
            plan = generatePlan(userMessage);
            if (plan == null || plan.isEmpty()) {
                log.info("规划阶段未产出计划，回到ReAct模式");
                return null; // 降级回 ReAct
            }
            log.info("Planned {} steps: {}", plan.size(),
                    plan.stream().map(PlanStep::getGoal).toList());

            // 第2阶段：逐个执行
            StringBuilder context = new StringBuilder();
            for (int i = 0; i < plan.size() && i < MAX_PLAN_STEPS; i++) {
                PlanStep step = plan.get(i);
                String stepResult = executeStep(userMessage, step, context.toString(), i + 1);
                context.append("【步骤").append(i + 1).append("完成】").append(step.getGoal())
                        .append("：\n").append(stepResult).append("\n\n");
                results.add(new StepResult(step.getGoal(), stepResult));
            }

            // 第3阶段：汇总
            finalAnswer = synthesize(userMessage, context.toString());

            agentTrace.setSuccess(true);
            agentTrace.setTotalRounds(plan.size() + 1);
        } catch (Exception e) {
            log.error("Plan-and-Execute失败", e);
            agentTrace.setSuccess(false);
            agentTrace.setErrorMessage(e.getMessage());
            agentTrace.setSnapshotRawResponse(truncate(e.getMessage(), 500));
            finalAnswer = promptsConfig.getFallback().getLlmError();
        } finally {
            long duration = System.currentTimeMillis() - start;
            agentTrace.setEndTime(LocalDateTime.now());
            agentTrace.setDurationMs(duration);
            agentTrace.setTags(Map.of("mode", "plan-execute",
                    "steps", String.valueOf(results.size())));
            traceService.record(agentTrace);
        }

        return PlanExecuteResult.builder()
                .plan(plan)
                .stepResults(results)
                .finalAnswer(extractPlainText(finalAnswer))
                .totalDurationMs(System.currentTimeMillis() - start)
                .build();
    }

    /** 阶段1：生成执行计划 */
    private List<PlanStep> generatePlan(String userMessage) {
        String prompt = """
                你是一个A股量化分析任务规划器。请将用户的问题拆解为有序的执行步骤。

                可用工具：%s

                用户问题：%s

                请严格按照以下JSON数组格式返回（不要包含其他文字）：
                [{"step":1,"goal":"获取贵州茅台实时行情","tools":["get_quote"],"instruction":"查询sh600519的实时价格、涨跌幅、成交量"},
                 {"step":2,"goal":"分析K线趋势","tools":["get_kline"],"instruction":"获取sh600519近20日K线数据，分析趋势和均线"},
                 {"step":3,"goal":"综合给出建议","tools":[],"instruction":"综合以上数据，给出分析和建议"}]

                规则：
                - 步骤不超过%s个
                - 每个步骤的goal要具体明确
                - instruction描述该步骤要完成什么分析
                - 简单问题可以只有1-2步
                """.formatted(toolRegistry.buildToolDescriptions(), userMessage, MAX_PLAN_STEPS);

        String response = chatModel.call(new Prompt(List.of(new UserMessage(prompt))))
                .getResult().getOutput().getContent();

        try {
            String json = extractJsonArray(response);
            if (json == null) return null;
            List<Map<String, Object>> raw = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<PlanStep> steps = new ArrayList<>();
            for (Map<String, Object> r : raw) {
                PlanStep step = new PlanStep();
                step.setStep(((Number) r.get("step")).intValue());
                step.setGoal((String) r.get("goal"));
                Object toolsObj = r.get("tools");
                step.setTools(toolsObj instanceof List ? (List<String>) toolsObj : List.of());
                step.setInstruction((String) r.get("instruction"));
                steps.add(step);
            }
            return steps;
        } catch (Exception e) {
            log.warn("解析计划失败: {}", e.getMessage());
            return null;
        }
    }

    /** 阶段2：执行单个步骤（简化的ReAct单轮） */
    private String executeStep(String originalQuestion, PlanStep step, String previousContext, int stepNum) {
        String prompt = String.format("""
                你正在按计划执行分析任务。

                【原始问题】%s

                【已完成的步骤】
                %s

                【当前步骤 %d/%d】%s
                执行指令：%s

                请基于可用工具的返回数据，完成当前步骤的分析。如果步骤目标已达成，直接给出分析结论。
                """, originalQuestion,
                previousContext.isEmpty() ? "（无，这是第一步）" : previousContext,
                stepNum, MAX_PLAN_STEPS,
                step.getGoal(),
                step.getInstruction());

        String response = chatModel.call(new Prompt(List.of(
                new UserMessage(prompt)
        ))).getResult().getOutput().getContent();

        return response != null ? response : "步骤执行完成（无额外分析）";
    }

    /** 阶段3：汇总所有步骤 */
    private String synthesize(String question, String allContext) {
        String prompt = String.format("""
                你是A股量化分析专家。请汇总以下分步分析结果，给出一份完整的投资分析报告。

                【用户问题】%s

                【分步分析结果】
                %s

                请输出一份完整的分析报告（Markdown格式，800字以内），包含：
                ### 一、数据概览
                ### 二、趋势分析
                ### 三、资金面/消息面
                ### 四、综合建议与风险提示

                要求：数据准确、逻辑连贯、建议明确（含BUY/SELL/HOLD和置信度）。
                """, question, allContext);

        return chatModel.call(new Prompt(List.of(new UserMessage(prompt))))
                .getResult().getOutput().getContent();
    }

    // ====== 辅助方法 ======

    private String extractJsonArray(String text) {
        if (text == null) return null;
        text = text.replaceAll("```(?:json)?\\s*|\\s*```", "").trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return null;
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    private String extractPlainText(String answer) {
        if (answer == null || answer.isBlank()) return answer;
        return answer;
    }

    // ====== 数据类 ======

    @lombok.Data
    public static class PlanStep {
        private int step;
        private String goal;
        private List<String> tools;
        private String instruction;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StepResult {
        private String goal;
        private String analysis;
    }

    @lombok.Data
    @lombok.Builder
    public static class PlanExecuteResult {
        private List<PlanStep> plan;
        private List<StepResult> stepResults;
        private String finalAnswer;
        private long totalDurationMs;
    }
}
