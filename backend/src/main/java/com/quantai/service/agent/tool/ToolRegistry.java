package com.quantai.service.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具注册中心 — 统一管理工具注册、参数校验、执行审计
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolInfo> registry = new LinkedHashMap<>();
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);

    /** 敏感操作需人工确认 */
    private static final Set<String> SENSITIVE_TOOLS = Set.of("place_order", "cancel_order");

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            register(tool);
        }
        log.info("ToolRegistry initialized: {} tools registered", registry.size());
        log.info("Registered tools: {}", getToolNames());
    }

    private void register(Tool tool) {
        ToolInfo info = new ToolInfo();
        info.tool = tool;
        info.requiredParams = extractRequiredParams(tool.getParameters());
        registry.put(tool.getName(), info);
    }

    /** 校验并执行工具 */
    public String execute(String toolName, Map<String, Object> args) {
        ToolInfo info = registry.get(toolName);
        if (info == null) {
            return "错误：找不到工具【" + toolName + "】，可用工具：" + getToolNames();
        }

        // 参数校验
        List<String> missing = checkRequiredParams(info, args);
        if (!missing.isEmpty()) {
            failedCalls.incrementAndGet();
            return "错误：缺少必填参数 " + missing;
        }

        // 敏感操作拦截
        if (SENSITIVE_TOOLS.contains(toolName)) {
            log.warn("敏感工具调用被拦截: {} args={}", toolName, args);
            return "错误：工具【" + toolName + "】需要用户确认后才能执行";
        }

        // 执行
        totalCalls.incrementAndGet();
        try {
            String result = info.tool.execute(args);
            log.debug("Tool executed: {} args={} result_len={}", toolName, args,
                    result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            failedCalls.incrementAndGet();
            log.error("Tool execution failed: {}", toolName, e);
            return "执行错误：" + e.getMessage();
        }
    }

    /** 获取所有工具（用于构建 LLM Prompt） */
    public List<Tool> getTools() {
        List<Tool> list = new ArrayList<>();
        for (ToolInfo info : registry.values()) {
            list.add(info.tool);
        }
        return list;
    }

    /** 获取工具名称列表 */
    public Collection<String> getToolNames() {
        return registry.keySet();
    }

    /** 检查工具是否存在 */
    public boolean hasTool(String name) {
        return registry.containsKey(name);
    }

    /** 获取统计信息 */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCalls", totalCalls.get());
        stats.put("failedCalls", failedCalls.get());
        stats.put("toolCount", registry.size());
        List<String> names = new ArrayList<>(registry.keySet());
        stats.put("tools", names);
        return stats;
    }

    /** 构建工具描述（给LLM的System Prompt用） */
    public String buildToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (ToolInfo info : registry.values()) {
            sb.append("- ").append(info.tool.getName()).append(": ").append(info.tool.getDescription()).append("\n");
            Map<String, Object> params = info.tool.getParameters();
            if (!params.isEmpty()) {
                sb.append("  参数：");
                for (Map.Entry<String, Object> e : params.entrySet()) {
                    sb.append(e.getKey()).append("=").append(e.getValue()).append(", ");
                }
                sb.setLength(sb.length() - 2);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ====== 内部方法 ======

    /** 从工具参数描述中提取必填参数名 */
    private Set<String> extractRequiredParams(Map<String, Object> params) {
        // 参数 Map 的 key 就是参数名，所有声明的参数都视为必填
        return new LinkedHashSet<>(params.keySet());
    }

    /** 检查缺少的必填参数 */
    private List<String> checkRequiredParams(ToolInfo info, Map<String, Object> args) {
        List<String> missing = new ArrayList<>();
        for (String required : info.requiredParams) {
            Object val = args.get(required);
            if (val == null || (val instanceof String && ((String) val).isBlank())) {
                missing.add(required);
            }
        }
        return missing;
    }

    // ====== 内部类 ======

    private static class ToolInfo {
        Tool tool;
        Set<String> requiredParams;
    }
}
