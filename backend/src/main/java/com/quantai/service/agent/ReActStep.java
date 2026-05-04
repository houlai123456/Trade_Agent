package com.quantai.service.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActStep {
    private String thought;       // LLM的思考过程
    private String action;        // Action JSON或"Final"
    private String observation;   // 工具返回结果
}
