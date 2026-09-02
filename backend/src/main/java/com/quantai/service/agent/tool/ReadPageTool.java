package com.quantai.service.agent.tool;

import com.quantai.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * readPage 工具 — 向量检索信息不足时，按文档+页码回读完整原文页
 * 解决"只能吃预切片碎片"的问题，Agent 可自主回读原始文档
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadPageTool implements Tool {

    private final RagService ragService;

    @Override
    public String getName() {
        return "read_page";
    }

    @Override
    public String getDescription() {
        return "读取上传文档指定页码的完整原文。当向量检索返回的片段信息不足或上下文断裂时使用。参数：docId=文档ID(从search_news_knowledge的返回中获取), page=页码";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("docId", "文档ID，从检索结果中获取");
        params.put("page", "页码（整数）");
        return params;
    }

    @Override
    public String execute(Map<String, Object> args) {
        String docId = args.get("docId") instanceof String ? (String) args.get("docId") : null;
        Integer page = args.get("page") instanceof Number
                ? ((Number) args.get("page")).intValue()
                : parsePage(args.get("page"));

        if (docId == null || docId.isBlank()) return "错误：缺少 docId 参数";
        if (page == null || page <= 0) return "错误：缺少有效的 page 参数";

        String text = ragService.readPage(docId, page);
        if (text == null) {
            return "错误：未找到文档【" + docId + "】第" + page + "页（文档可能已过期或页码超出范围）";
        }

        log.info("readPage: doc={} page={} len={}", docId, page, text.length());
        return "【" + docId + " 第" + page + "页原文】\n" + text;
    }

    private Integer parsePage(Object val) {
        if (val == null) return null;
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
