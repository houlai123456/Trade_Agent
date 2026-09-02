package com.quantai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分析进度DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisProgress {

    private String taskId;

    private String stockCode;

    private String stage;

    private String currentAgent;

    private Integer completedAgents;

    private Integer totalAgents;

    private Integer progress;

    private String status;

    private String message;

    private Long durationMs;

    private Integer tokenUsed;
}
