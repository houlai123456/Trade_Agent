package com.quantai.service.agent.tool;

import com.quantai.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * searchRag 工具 — 向量检索上传文档（粗筛）
 * 返回结果带 docId + 页码，Agent 可继续用 read_page / extract_table 回读原文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchRagTool implements Tool {

    private final RagService ragService;

    @Override
    public String getName() {
        return "search_news_knowledge";
    }

    @Override
    public String getDescription() {
        return "搜索用户上传的私有文档（研报、PDF等）获取相关信息。适用于询问文档内容。返回结果包含docId和页码，如需更多上下文可用read_page工具读完整页。参数：question=问题";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("question", "问题内容，如 '这份研报里关于营收的预测是什么'");
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String question = (String) args.get("question");
        if (question == null || question.isBlank()) {
            return "错误：缺少 question 参数";
        }

        try {
            Map<String, Object> result = ragService.ask(question, 5);
            String answer = (String) result.get("answer");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sources = (List<Map<String, Object>>) result.get("sources");

            StringBuilder sb = new StringBuilder();
            sb.append("文档检索结果：\n").append(answer);

            if (sources != null && !sources.isEmpty()) {
                sb.append("\n\n命中片段（可用 read_page 工具回读原文）：");
                for (Map<String, Object> src : sources) {
                    String filename = String.valueOf(src.getOrDefault("filename", ""));
                    Object pageObj = src.get("page");
                    int page = pageObj instanceof Number ? ((Number) pageObj).intValue() : 0;
                    String chapter = String.valueOf(src.getOrDefault("chapter", ""));
                    Object docId = src.get("doc_id");
                    double score = src.get("score") instanceof Number ? ((Number) src.get("score")).doubleValue() : 0;
                    sb.append("\n- ").append(filename);
                    if (page > 0) sb.append(" 第").append(page).append("页");
                    if (!chapter.isBlank() && !"null".equals(chapter)) sb.append("（").append(chapter).append("）");
                    if (docId != null) sb.append(" docId=").append(docId);
                    sb.append(" 相似度").append(String.format("%.2f", score));
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("知识库查询失败 question={}", question, e);
            return "知识库查询暂不可用（" + e.getMessage() + "）";
        }
    }
}
