package com.quantai.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.config.PromptsConfig;
import com.quantai.service.agent.tool.ToolRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct Agent — 状态机模式实现
 * LLM自主决定调什么工具、什么时候给最终答案
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActAgentService {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final PromptsConfig promptsConfig;

    // Prometheus 指标
    private final Counter llmPromptTokensCounter;
    private final Counter llmCompletionTokensCounter;
    private final Counter llmTotalTokensCounter;
    private final Counter llmCallCounter;
    private final Counter llmErrorCounter;
    private final Timer llmCallTimer;
    private final Counter agentRoundsCounter;
    private final Counter agentToolCallCounter;

    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_OBSERVATION_LEN = 500;

    /* ========== 状态机定义 ========== */
    enum AgentState {
        INIT,           // 初始状态
        THINKING,       // 调用LLM获取思考
        DECIDING,       // 判断LLM输出是Action还是Final
        EXECUTING,      // 执行工具调用
        OBSERVING,      // 记录工具返回并判断是否继续
        TIMEOUT,        // 超时终止
        FINISHED        // 终态
    }

    /** 构建工具描述（给LLM的System Prompt） */
    private String buildToolDescriptions() {
        return promptsConfig.getSystem().getReactAgent()
                .replace("{tools}", toolRegistry.buildToolDescriptions())
                .replace("{maxIterations}", String.valueOf(MAX_ITERATIONS));
    }

    /**
     * 运行 ReAct 状态机
     * @param userMessage 用户输入，如"分析一下贵州茅台"
     * @return 完整的思考轨迹 + 最终答案
     */
    public ReActResult run(String userMessage) {
        long start = System.currentTimeMillis();
        List<ReActStep> traceSteps = new ArrayList<>();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildToolDescriptions()));
        messages.add(Map.of("role", "user", "content", userMessage));

        // 状态机上下文
        AgentState state = AgentState.INIT;
        int iteration = 0;
        String llmResponse = null;
        String thought = null;
        String actionJson = null;
        String finalAnswer = null;
        boolean hasError = false;
        String errorMsg = null;

        // 收集工具返回数据，供 Reflexion 审查用
        StringBuilder observationsLog = new StringBuilder();

        while (state != AgentState.FINISHED) {
            switch (state) {

                case INIT:
                    state = AgentState.THINKING;
                    break;

                case THINKING:
                    iteration++;
                    log.info("ReAct 第{}轮", iteration);
                    try {
                        llmResponse = callLlm(messages);
                    } catch (Exception e) {
                        hasError = true;
                        errorMsg = "LLM调用异常: " + e.getMessage();
                        log.error("ReAct LLM调用失败", e);
                        finalAnswer = promptsConfig.getFallback().getLlmError();
                        state = AgentState.FINISHED;
                        break;
                    }
                    state = AgentState.DECIDING;
                    break;

                case DECIDING: {
                    thought = extractThought(llmResponse);
                    actionJson = extractAction(llmResponse);
                    String finalResult = extractFinal(llmResponse);

                    if (finalResult != null) {
                        finalAnswer = finalResult;
                        traceSteps.add(ReActStep.builder()
                                .thought("得出最终结论")
                                .action("Final")
                                .observation(finalResult)
                                .build());
                        state = AgentState.FINISHED;
                    } else if (actionJson == null) {
                        finalAnswer = llmResponse;
                        traceSteps.add(ReActStep.builder()
                                .thought(thought != null ? thought : "直接回答")
                                .action("Final")
                                .observation(llmResponse)
                                .build());
                        state = AgentState.FINISHED;
                    } else {
                        state = AgentState.EXECUTING;
                    }
                    break;
                }

                case EXECUTING: {
                    String observation;
                    String toolName = "unknown";
                    long toolStart = System.currentTimeMillis();
                    boolean toolSuccess = true;

                    try {
                        Map<String, Object> action = objectMapper.readValue(actionJson,
                                new TypeReference<Map<String, Object>>() {});
                        toolName = (String) action.get("name");

                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = action.get("args") instanceof Map
                                ? (Map<String, Object>) action.get("args")
                                : new LinkedHashMap<>();

                        log.info("调用工具: {}({})", toolName, args);
                        agentToolCallCounter.increment();
                        observation = toolRegistry.execute(toolName, args);
                        toolSuccess = !observation.startsWith("错误");
                        log.info("工具返回: {}...", observation.length() > 100 ? observation.substring(0, 100) : observation);
                    } catch (Exception e) {
                        observation = "执行错误：" + e.getMessage();
                        toolSuccess = false;
                        log.warn("工具执行失败", e);
                    }

                    long toolDuration = System.currentTimeMillis() - toolStart;

                    observationsLog.append("【").append(toolName).append("】\n").append(observation).append("\n\n");
                    traceSteps.add(ReActStep.builder()
                            .thought(thought != null ? thought : "")
                            .action(actionJson)
                            .observation(observation)
                            .build());

                    messages.add(Map.of("role", "assistant", "content", llmResponse));
                    String ctxObs = observation.length() > MAX_OBSERVATION_LEN
                            ? observation.substring(0, MAX_OBSERVATION_LEN) + "...(已截断)"
                            : observation;
                    messages.add(Map.of("role", "system", "content", "【工具返回】" + ctxObs));

                    state = AgentState.OBSERVING;
                    break;
                }

                case OBSERVING:
                    agentRoundsCounter.increment();
                    if (iteration >= MAX_ITERATIONS) {
                        state = AgentState.TIMEOUT;
                    } else {
                        state = AgentState.THINKING;
                    }
                    break;

                case TIMEOUT:
                    finalAnswer = promptsConfig.getFallback().getTimeout();
                    state = AgentState.FINISHED;
                    break;
            }
        }

        finalAnswer = extractPlainText(finalAnswer);

        // ====== Reflexion 自省审查 ======
        boolean reflexionPassed = true;
        String reflexionIssues = null;
        if (!hasError && observationsLog.length() > 0 && finalAnswer != null && !finalAnswer.isBlank()) {
            try {
                String reviewerPrompt = promptsConfig.getReflexion().getReviewer()
                        .replace("{question}", userMessage)
                        .replace("{observations}", truncate(observationsLog.toString(), 2000))
                        .replace("{analysis}", finalAnswer);

                Prompt reflexionPrompt = new Prompt(List.of(
                        new UserMessage(reviewerPrompt)
                ));
                ChatResponse reflexionResponse = chatModel.call(reflexionPrompt);
                String reflexionRaw = reflexionResponse.getResult().getOutput().getContent();
                log.debug("Reflexion reviewer response: {}", reflexionRaw);

                // 解析审查结果
                String json = extractJson(reflexionRaw);
                if (json != null) {
                    Map<String, Object> review = objectMapper.readValue(json,
                            new TypeReference<Map<String, Object>>() {});
                    Boolean pass = (Boolean) review.get("pass");
                    if (pass != null && !pass) {
                        reflexionPassed = false;
                        reflexionIssues = review.get("issues") != null
                                ? review.get("issues").toString() : "审查未通过";
                        String corrected = (String) review.get("corrected");
                        if (corrected != null && !corrected.isBlank()) {
                            log.info("Reflexion 修正分析: issues={}", reflexionIssues);
                            finalAnswer = corrected;
                        }
                    } else {
                        log.info("Reflexion 审查通过");
                    }
                }
            } catch (Exception e) {
                log.warn("Reflexion审查调用失败，使用原始分析: {}", e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("ReAct完成 共{}轮 耗时={}ms reflexion={}", iteration, duration,
                !reflexionPassed ? "corrected" : (observationsLog.length() > 0 ? "passed" : "skipped"));

        return ReActResult.builder()
                .trace(traceSteps)
                .finalAnswer(finalAnswer)
                .totalRounds(iteration)
                .totalDurationMs(duration)
                .build();
    }

    /** 从文本中提取JSON对象 */
    private String extractJson(String text) {
        if (text == null) return null;
        text = text.replaceAll("```(?:json)?\\s*|\\s*```", "").trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return null;
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(已截断)";
    }

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 500;

    private String callLlm(List<Map<String, String>> messages) {
        List<org.springframework.ai.chat.messages.Message> springMessages = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("system".equals(role)) {
                springMessages.add(new SystemMessage(content));
            } else if ("user".equals(role)) {
                springMessages.add(new UserMessage(content));
            } else if ("assistant".equals(role)) {
                springMessages.add(new AssistantMessage(content));
            }
        }

        Prompt prompt = new Prompt(springMessages);
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            Timer.Sample sample = Timer.start();
            try {
                llmCallCounter.increment();
                ChatResponse response = chatModel.call(prompt);
                sample.stop(llmCallTimer);

                // 记录 Token 消耗
                Usage usage = response.getMetadata().getUsage();
                if (usage != null) {
                    Long promptTokens = usage.getPromptTokens();
                    Long completionTokens = usage.getGenerationTokens();
                    Long totalTokens = usage.getTotalTokens();

                    if (promptTokens != null) {
                        llmPromptTokensCounter.increment(promptTokens);
                    }
                    if (completionTokens != null) {
                        llmCompletionTokensCounter.increment(completionTokens);
                    }
                    if (totalTokens != null) {
                        llmTotalTokensCounter.increment(totalTokens);
                    }

                    log.debug("LLM Token 消耗 - Prompt: {}, Completion: {}, Total: {}",
                            promptTokens, completionTokens, totalTokens);
                }

                return response.getResult().getOutput().getContent();
            } catch (Exception e) {
                sample.stop(llmCallTimer);
                llmErrorCounter.increment();
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << attempt);
                    log.warn("LLM调用失败(第{}次)，{}ms后重试: {}", attempt + 1, delay, e.getMessage());
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw new RuntimeException("LLM调用失败，已重试" + MAX_RETRIES + "次: " + lastException.getMessage(), lastException);
    }

    private String extractThought(String text) {
        Pattern p = Pattern.compile("Thought:\\s*(.*?)(?=Action:|Final:|$)", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractAction(String text) {
        Pattern p = Pattern.compile("Action:\\s*(\\{.*)", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String json = m.group(1).trim();
            int depth = 0;
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return json.substring(0, i + 1);
                }
            }
        }
        return null;
    }

    private String extractFinal(String text) {
        // 去掉markdown代码块标记（LLM有时会用```json包裹JSON）
        String clean = text.replaceAll("```(?:json)?\\s*|\\s*```", "").trim();

        Pattern p = Pattern.compile("Final:\\s*(\\{.*\\}|[^\\{].*)", Pattern.DOTALL);
        Matcher m = p.matcher(clean);
        if (m.find()) {
            String result = m.group(1).trim();
            if (result.startsWith("{")) {
                // 用大括号匹配提取完整JSON，去掉尾随文本
                int depth = 0;
                for (int i = 0; i < result.length(); i++) {
                    char c = result.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) return result.substring(0, i + 1);
                    }
                }
            }
            return result;
        }
        // 没有Final:标签但直接输出了JSON
        if (clean.startsWith("{") && clean.contains("analysis")) {
            int depth = 0;
            for (int i = 0; i < clean.length(); i++) {
                char c = clean.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return clean.substring(0, i + 1);
                }
            }
            return clean;
        }
        return null;
    }

    /** 将最终答案转为纯文本（优先提取analysis字段，失败则清理原始输出） */
    private String extractPlainText(String answer) {
        if (answer == null || answer.isBlank()) return answer;
        String trimmed = answer.trim();
        trimmed = trimmed.replaceAll("```(?:json)?\\s*|\\s*```", "").trim();
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        String textAfterJson = "";

        // 尝试提取JSON后的纯文本（LLM有时在JSON后面写分析）
        if (jsonEnd > jsonStart && jsonEnd < trimmed.length() - 1) {
            textAfterJson = trimmed.substring(jsonEnd + 1).trim();
            // 去掉"Observation:"等前缀
            textAfterJson = textAfterJson.replaceFirst("^(Observation|观察|分析)[：:]\\s*", "");
        }

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            String jsonPart = trimmed.substring(jsonStart, jsonEnd + 1);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(jsonPart, Map.class);
                StringBuilder sb = new StringBuilder();

                Object analysis = map.get("analysis");
                if (analysis != null && !analysis.toString().isBlank()) {
                    sb.append(analysis.toString());
                }

                Object suggestion = map.get("suggestion");
                if (suggestion != null) {
                    String s = suggestion.toString();
                    String label;
                    if ("BUY".equals(s)) label = "\n\n建议买入";
                    else if ("SELL".equals(s)) label = "\n\n建议卖出";
                    else label = "\n\n建议持有观望";
                    sb.append(label);
                    Object confidence = map.get("confidence");
                    if (confidence != null) {
                        String c = confidence.toString();
                        if ("HIGH".equals(c)) sb.append("（信心高）");
                        else if ("MEDIUM".equals(c)) sb.append("（信心中）");
                        else sb.append("（信心低）");
                    }
                }

                Object reason = map.get("reason");
                if (reason != null && !reason.toString().isBlank()) {
                    sb.append("\n\n").append(reason.toString());
                }

                String result = sb.toString().trim();
                if (!result.isEmpty()) return result;

                // JSON中无analysis等字段，使用JSON后的纯文本
                if (!textAfterJson.isBlank()) return textAfterJson;
            } catch (Exception ignored) {
                // JSON解析失败，继续往下走
            }
        }

        // 兜底：清理掉JSON块和前缀，返回纯文本部分
        if (!textAfterJson.isBlank()) return textAfterJson;
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonEnd + 1).trim();
        }
        return answer;
    }
}
