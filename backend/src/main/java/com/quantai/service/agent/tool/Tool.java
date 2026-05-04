package com.quantai.service.agent.tool;

import java.util.Map;

/**
 * ReAct Agent 工具接口
 */
public interface Tool {
    /** 工具名称（LLM通过此名称调用） */
    String getName();

    /** 工具描述（给LLM看的） */
    String getDescription();

    /** JSON Schema 格式的参数定义 */
    Map<String, Object> getParameters();

    /** 执行工具并返回结果字符串 */
    String execute(Map<String, Object> args);
}
