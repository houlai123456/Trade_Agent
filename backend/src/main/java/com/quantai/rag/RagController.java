package com.quantai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final PdfParserService pdfParserService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        ragService.ensureCollection();
    }

    /** GET /api/rag/info — 向量库状态 */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data", ragService.getCollectionInfo()));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("success", false, "error", "RAG服务未初始化（Qdrant未运行）"));
        }
    }

    /** POST /api/rag/ask — 问答 */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, Object> body) {
        String question = body.get("question") instanceof String ? (String) body.get("question") : null;
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "缺少 question 参数"));
        }
        int topK = body.containsKey("top_k") ? ((Number) body.get("top_k")).intValue() : 5;

        try {
            Map<String, Object> result = ragService.ask(question, topK);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            log.error("RAG问答失败", e);
            return ResponseEntity.ok(Map.of("success", true, "data",
                    Map.of("answer", "AI分析服务暂不可用，请稍后重试。", "sources", java.util.Collections.emptyList())));
        }
    }

    /** POST /api/rag/ask/stream — 流式问答（SSE） */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody Map<String, Object> body) {
        String question = body.get("question") instanceof String ? (String) body.get("question") : null;
        if (question == null || question.isBlank()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().data("{\"type\":\"answer\",\"content\":\"缺少问题参数\"}"));
                emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        int topK = body.containsKey("top_k") ? ((Number) body.get("top_k")).intValue() : 5;
        SseEmitter emitter = new SseEmitter(60000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                Map<String, Object> result = ragService.ask(question, topK);
                String answer = (String) result.get("answer");

                // 逐句发送
                String[] sentences = answer.split("(?<=[。！？\n])");
                for (String sentence : sentences) {
                    if (!sentence.isBlank()) {
                        String json = "{\"type\":\"answer\",\"content\":\"" +
                                escapeJson(sentence) + "\"}";
                        emitter.send(SseEmitter.event().data(json));
                        Thread.sleep(50);
                    }
                }

                // 发送来源
                Object sources = result.get("sources");
                if (sources != null) {
                    String sourcesJson = "{\"type\":\"sources\",\"sources\":" +
                            objectMapper.writeValueAsString(sources) + "}";
                    emitter.send(SseEmitter.event().data(sourcesJson));
                }

                emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("{\"type\":\"answer\",\"content\":\"分析暂不可用\"}"));
                    emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        });

        executor.shutdown();
        return emitter;
    }

    /** POST /api/rag/upload — 上传文档（PDF 走新版解析管道） */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "文件为空"));
        }

        try {
            String filename = file.getOriginalFilename();
            Map<String, Object> result;

            if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
                PdfParserService.ParsedDocument doc = pdfParserService.parse(file.getInputStream(), filename);
                if (pdfParserService.isScanned(doc)) {
                    return ResponseEntity.ok(Map.of("success", false, "error",
                            "检测到扫描件PDF（无文字层），暂不支持OCR识别，请上传原生电子版PDF"));
                }
                result = ragService.ingestPdfDocument(doc);
            } else {
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    return ResponseEntity.ok(Map.of("success", true, "data",
                            Map.of("chunks", 0, "message", "文件内容为空")));
                }
                int chunks = ragService.ingestDocument(content,
                        filename != null ? filename : "未命名文件");
                result = Map.of("chunks", chunks, "message", "索引完成");
            }

            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            log.error("文档上传失败", e);
            return ResponseEntity.ok(Map.of("success", false, "error", "文档处理失败: " + e.getMessage()));
        }
    }

    /** GET /api/rag/page — 按文档+页码回读原文（Agent readPage 工具后端支持） */
    @GetMapping("/page")
    public ResponseEntity<Map<String, Object>> readPage(@RequestParam String docId, @RequestParam int page) {
        String text = ragService.readPage(docId, page);
        if (text == null) {
            return ResponseEntity.ok(Map.of("success", false, "error", "文档不存在或页码超出范围"));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("docId", docId, "page", page, "content", text)));
    }

    /** GET /api/rag/table — 抽取某页表格内容 */
    @GetMapping("/table")
    public ResponseEntity<Map<String, Object>> extractTable(@RequestParam String docId, @RequestParam int page) {
        String table = ragService.extractTable(docId, page);
        if (table == null) {
            return ResponseEntity.ok(Map.of("success", false, "error", "该页未检测到表格内容"));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", Map.of("docId", docId, "page", page, "table", table)));
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
