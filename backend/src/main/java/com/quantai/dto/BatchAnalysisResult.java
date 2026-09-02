package com.quantai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 批量分析结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAnalysisResult {

    private String taskId;

    private Map<String, String> results;

    private Integer totalCount;

    private Integer successCount;

    private Integer failureCount;

    private Long totalDurationMs;

    private Integer totalTokens;

    private String status;
}
