package com.quantai.controller;

import com.quantai.mapper.AgentAdviceMapper;
import com.quantai.mapper.RiskMonitorLogMapper;
import com.quantai.model.entity.AgentAdvice;
import com.quantai.model.entity.RiskMonitorLog;
import com.quantai.service.AgentAdviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent建议和风险监控API
 */
@Slf4j
@RestController
@RequestMapping("/api/agent-advice")
@RequiredArgsConstructor
public class AgentAdviceController {

    private final AgentAdviceService agentAdviceService;
    private final AgentAdviceMapper adviceMapper;
    private final RiskMonitorLogMapper riskMonitorLogMapper;

    /**
     * 查询某只股票的历史建议
     */
    @GetMapping("/history/{stockCode}")
    public Map<String, Object> getAdviceHistory(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "10") int limit) {

        List<AgentAdvice> advices = adviceMapper.selectByStockCode(stockCode, limit);
        String accuracyStats = agentAdviceService.getAccuracyStats(stockCode);

        Map<String, Object> result = new HashMap<>();
        result.put("stockCode", stockCode);
        result.put("advices", advices);
        result.put("accuracyStats", accuracyStats);
        result.put("total", advices.size());

        return result;
    }

    /**
     * 查询某个建议的风险监控历史
     */
    @GetMapping("/monitor/{adviceId}")
    public Map<String, Object> getMonitorHistory(
            @PathVariable Long adviceId,
            @RequestParam(defaultValue = "30") int limit) {

        AgentAdvice advice = adviceMapper.selectById(adviceId);
        if (advice == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "建议不存在");
            return error;
        }

        List<RiskMonitorLog> logs = riskMonitorLogMapper.selectByAdviceId(adviceId, limit);

        Map<String, Object> result = new HashMap<>();
        result.put("advice", advice);
        result.put("monitorLogs", logs);
        result.put("total", logs.size());

        return result;
    }

    /**
     * 查询所有活跃的建议（正在监控中的）
     */
    @GetMapping("/active")
    public Map<String, Object> getActiveAdvices() {
        List<AgentAdvice> activeAdvices = adviceMapper.selectActiveAdvices();

        Map<String, Object> result = new HashMap<>();
        result.put("advices", activeAdvices);
        result.put("total", activeAdvices.size());

        return result;
    }

    /**
     * 手动关闭某个建议（停止监控）
     */
    @PostMapping("/close/{adviceId}")
    public Map<String, Object> closeAdvice(@PathVariable Long adviceId) {
        AgentAdvice advice = adviceMapper.selectById(adviceId);
        if (advice == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "建议不存在");
            return error;
        }

        agentAdviceService.closeAdvice(adviceId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已关闭建议");
        result.put("adviceId", adviceId);

        return result;
    }

    /**
     * 查询某只股票的最新监控状态
     */
    @GetMapping("/latest-monitor/{stockCode}")
    public Map<String, Object> getLatestMonitor(@PathVariable String stockCode) {
        RiskMonitorLog latestLog = riskMonitorLogMapper.selectLatestByStockCode(stockCode);

        Map<String, Object> result = new HashMap<>();
        if (latestLog != null) {
            result.put("hasData", true);
            result.put("log", latestLog);
        } else {
            result.put("hasData", false);
            result.put("message", "暂无监控数据");
        }

        return result;
    }

    /**
     * 查询股票的历史准确率统计
     */
    @GetMapping("/accuracy/{stockCode}")
    public Map<String, Object> getAccuracy(@PathVariable String stockCode) {
        String stats = agentAdviceService.getAccuracyStats(stockCode);

        Map<String, Object> result = new HashMap<>();
        result.put("stockCode", stockCode);
        result.put("stats", stats);

        return result;
    }
}
