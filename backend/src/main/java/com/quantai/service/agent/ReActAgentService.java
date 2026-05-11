package com.quantai.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.service.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

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
    private final List<Tool> tools;

    private static final int MAX_ITERATIONS = 10;

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
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个A股量化分析助手，可以通过调用工具获取实时数据来分析股票。\n\n");
        sb.append("可用工具：\n");

        for (Tool tool : tools) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            Map<String, Object> params = tool.getParameters();
            if (!params.isEmpty()) {
                sb.append("  参数：");
                for (Map.Entry<String, Object> e : params.entrySet()) {
                    sb.append(e.getKey()).append("=").append(e.getValue()).append(", ");
                }
                sb.setLength(sb.length() - 2);
                sb.append("\n");
            }
        }

        sb.append("\n你每次回复必须按以下格式：\n");
        sb.append("Thought: 写下你当前的想法，打算做什么、为什么\n");
        sb.append("Action: {\"name\":\"工具名\",\"args\":{\"参数名\":\"参数值\"}}\n\n");
        sb.append("或者当你已经收集足够信息时：\n");
        sb.append("Final: {\"analysis\":\"综合分析\",\"suggestion\":\"BUY|SELL|HOLD\",\"confidence\":\"HIGH|MEDIUM|LOW\",\"reason\":\"详细理由\"}\n\n");
        sb.append("规则：\n");
        sb.append("- 一次只说一个Thought + 一个Action\n");
        sb.append("- 不要编造数据，所有数据必须通过工具获取\n");
        sb.append("- 如果工具返回空数据，告诉用户原因，不要硬编\n");
        sb.append("- 收集足够信息后再给出Final\n");
        sb.append("- 最多").append(MAX_ITERATIONS).append("轮工具调用");

        return sb.toString();
    }

    /**
     * 运行 ReAct 状态机
     * @param userMessage 用户输入，如"分析一下贵州茅台"
     * @return 完整的思考轨迹 + 最终答案
     */
    public ReActResult run(String userMessage) {
        long start = System.currentTimeMillis();
        List<ReActStep> trace = new ArrayList<>();

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

        while (state != AgentState.FINISHED) {
            switch (state) {

                case INIT:
                    state = AgentState.THINKING;
                    break;

                case THINKING:
                    iteration++;
                    log.info("ReAct 第{}轮", iteration);
                    llmResponse = callLlm(messages);
                    state = AgentState.DECIDING;
                    break;

                case DECIDING: {
                    thought = extractThought(llmResponse);
                    actionJson = extractAction(llmResponse);
                    String finalResult = extractFinal(llmResponse);

                    if (finalResult != null) {
                        // LLM主动给出最终结论
                        finalAnswer = finalResult;
                        trace.add(ReActStep.builder()
                                .thought("得出最终结论")
                                .action("Final")
                                .observation(finalResult)
                                .build());
                        state = AgentState.FINISHED;
                    } else if (actionJson == null) {
                        // LLM没返回Action格式，直接当作回答
                        finalAnswer = llmResponse;
                        trace.add(ReActStep.builder()
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
                    try {
                        Map<String, Object> action = objectMapper.readValue(actionJson,
                                new TypeReference<Map<String, Object>>() {});
                        String toolName = (String) action.get("name");

                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = action.get("args") instanceof Map
                                ? (Map<String, Object>) action.get("args")
                                : new LinkedHashMap<>();

                        Tool tool = findTool(toolName);
                        if (tool == null) {
                            observation = "错误：找不到工具【" + toolName + "】，可用工具：" + tools.stream().map(Tool::getName).toList();
                        } else {
                            log.info("调用工具: {}({})", toolName, args);
                            observation = tool.execute(args);
                            log.info("工具返回: {}...", observation.length() > 100 ? observation.substring(0, 100) : observation);
                        }
                    } catch (Exception e) {
                        observation = "执行错误：" + e.getMessage();
                        log.warn("工具执行失败", e);
                    }

                    trace.add(ReActStep.builder()
                            .thought(thought != null ? thought : "")
                            .action(actionJson)
                            .observation(observation)
                            .build());

                    messages.add(Map.of("role", "assistant", "content", llmResponse));
                    messages.add(Map.of("role", "system", "content", "Observation: " + observation));

                    state = AgentState.OBSERVING;
                    break;
                }

                case OBSERVING:
                    if (iteration >= MAX_ITERATIONS) {
                        state = AgentState.TIMEOUT;
                    } else {
                        state = AgentState.THINKING;
                    }
                    break;

                case TIMEOUT:
                    finalAnswer = "{\"analysis\":\"分析超时，未能完成完整的分析流程。请尝试更具体的提问。\",\"suggestion\":\"HOLD\",\"confidence\":\"LOW\",\"reason\":\"分析轮数超限\"}";
                    state = AgentState.FINISHED;
                    break;
            }
        }

        // 如果finalAnswer是JSON格式，提取analysis字段作为纯文本返回
        finalAnswer = extractPlainText(finalAnswer);

        long duration = System.currentTimeMillis() - start;
        log.info("ReAct完成 共{}轮 耗时={}ms", iteration, duration);

        return ReActResult.builder()
                .trace(trace)
                .finalAnswer(finalAnswer)
                .totalRounds(iteration)
                .totalDurationMs(duration)
                .build();
    }

    private String callLlm(List<Map<String, String>> messages) {
        // 按角色构建正确的对话消息列表
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
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getContent();
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

    private Tool findTool(String name) {
        for (Tool t : tools) {
            if (t.getName().equals(name)) return t;
        }
        return null;
    }

    /** 将JSON格式的最终答案转为纯文本（提取analysis字段），前端直接展示 */
    private String extractPlainText(String answer) {
        if (answer == null || answer.isBlank()) return answer;
        String trimmed = answer.trim();
        // 去代码块标记和leading非JSON前缀（如LLM输出的**等）
        trimmed = trimmed.replaceAll("```(?:json)?\\s*|\\s*```", "").trim();
        int jsonStart = trimmed.indexOf('{');
        if (jsonStart < 0) return answer;
        trimmed = trimmed.substring(jsonStart);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(trimmed, Map.class);
            Object analysis = map.get("analysis");
            String text = analysis != null ? analysis.toString() : null;

            // 组合成可读文本
            StringBuilder sb = new StringBuilder();
            if (text != null && !text.isBlank()) sb.append(text);

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
                sb.append("\n\n").append(reason);
            }

            String result = sb.toString().trim();
            return result.isEmpty() ? answer : result;
        } catch (Exception e) {
            return answer;
        }
    }
}
