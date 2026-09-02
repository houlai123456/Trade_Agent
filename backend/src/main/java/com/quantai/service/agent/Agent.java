package com.quantai.service.agent;

import java.util.List;

/**
 * Agent 统一接口 - 借鉴 CrewAI 设计
 * 每个 Agent 有明确的角色、目标、可用工具
 */
public interface Agent {

    /** Agent 名称（唯一标识） */
    String getName();

    /** Agent 角色描述（传给 LLM 的 system prompt） */
    String getRole();

    /** Agent 目标/职责 */
    String getGoal();

    /** Agent 可用的工具名称列表 */
    List<String> getToolNames();

    /**
     * 执行 Agent 任务
     * @param context 上下文（包含用户问题、前序 Agent 的输出等）
     * @return Agent 执行结果
     */
    AgentResult execute(AgentContext context);
}
