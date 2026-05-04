package com.quantai.service.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActResult {
    private List<ReActStep> trace;  // 完整思考轨迹
    private String finalAnswer;     // 最终答案
    private int totalRounds;        // 总轮数
    private long totalDurationMs;   // 总耗时
}
