package com.quantai.service.agent.tool;

import com.quantai.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * extractTable 工具 — 抽取上传文档指定页的表格数据（结构化文本）
 * 解决"表格被切片碾碎成乱字符串"的问题，Agent 可按需获取完整表格
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractTableTool implements Tool {

    private final RagService ragService;

    @Override
    public String getName() {
        return "extract_table";
    }

    @Override
    public String getDescription() {
        return "抽取上传文档指定页码的表格数据。当用户询问具体数字、财务数据、对比数据等表格内容时使用。参数：docId=文档ID, page=页码";
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

        String table = ragService.extractTable(docId, page);
        if (table == null) {
            return "错误：文档【" + docId + "】第" + page + "页未检测到表格内容，可用 read_page 工具查看该页原文";
        }

        log.info("extractTable: doc={} page={} rows={}", docId, page, table.split("\n").length);
        return "【" + docId + " 第" + page + "页表格数据】\n" + table;
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
