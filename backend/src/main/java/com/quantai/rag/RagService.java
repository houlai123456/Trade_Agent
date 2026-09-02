package com.quantai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.config.PromptsConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OpenAiChatModel chatModel;
    private final PromptsConfig promptsConfig;

    @Value("${rag.aliyun.api-key:}")
    private String aliyunApiKey;

    @Value("${rag.qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    private static final String ALIYUN_EMBEDDING_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";
    private static final String ALIYUN_EMBEDDING_MODEL = "tongyi-embedding-vision-plus-2026-03-06";
    private static final String COLLECTION_NAME = "stock_news";
    private static final int EMBED_DIM = 1152;
    private static final int EMBED_BATCH = 10;
    private static final int CHUNK_MAX_CHARS = 500;
    private static final int CHUNK_OVERLAP = 50;

    // ==================== Embedding（阿里云API）====================

    public List<Double> getEmbedding(String text) {
        return getEmbeddingsBatch(Collections.singletonList(text)).get(0);
    }

    public List<List<Double>> getEmbeddingsBatch(List<String> texts) {
        try {
            List<Map<String, Object>> contents = texts.stream()
                    .map(t -> Map.<String, Object>of("text", t))
                    .collect(Collectors.toList());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", ALIYUN_EMBEDDING_MODEL);
            body.put("input", Map.of("contents", contents));
            body.put("parameters", Map.of("auto_truncation", true));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aliyunApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String resp = restTemplate.postForObject(ALIYUN_EMBEDDING_URL, request, String.class);

            JsonNode root = objectMapper.readTree(resp);
            JsonNode embeddings = root.path("output").path("embeddings");

            List<List<Double>> results = new ArrayList<>();
            for (JsonNode emb : embeddings) {
                List<Double> vec = new ArrayList<>();
                for (JsonNode v : emb.path("embedding")) {
                    vec.add(v.asDouble());
                }
                results.add(vec);
            }
            return results;
        } catch (Exception e) {
            log.error("Embedding 失败", e);
            throw new RuntimeException("Embedding 调用失败", e);
        }
    }

    // ==================== Qdrant 操作（REST API）====================

    private String qdrantUrl(String path) {
        return qdrantUrl + path;
    }

    /** 检查collection是否存在，不存在则创建 */
    public void ensureCollection() {
        try {
            String infoUrl = qdrantUrl("/collections/" + COLLECTION_NAME);
            ResponseEntity<String> resp = restTemplate.getForEntity(infoUrl, String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                int existingDim = root.path("result").path("config").path("params").path("vectors").path("size").asInt();
                if (existingDim != EMBED_DIM) {
                    log.warn("Qdrant维度不匹配 {}->{}，删除重建", existingDim, EMBED_DIM);
                    restTemplate.exchange(qdrantUrl("/collections/" + COLLECTION_NAME),
                            HttpMethod.DELETE, null, String.class);
                    createCollection();
                }
                return;
            }
        } catch (Exception e) {
            log.info("Qdrant collection 不存在，创建中");
        }
        createCollection();
    }

    private void createCollection() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", COLLECTION_NAME);
        body.put("vectors", Map.of("size", EMBED_DIM, "distance", "Cosine"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(qdrantUrl("/collections"), HttpMethod.PUT, request, String.class);
            log.info("创建 Qdrant collection: {}", COLLECTION_NAME);
        } catch (Exception e) {
            log.warn("创建 Qdrant collection 失败（Qdrant可能未运行）: {}", e.getMessage());
        }
    }

    /** 向量检索 */
    public List<Map<String, Object>> search(String query, int topK) {
        List<Double> vector = getEmbedding(query);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("limit", topK);
        body.put("with_payload", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            String url = qdrantUrl("/collections/" + COLLECTION_NAME + "/points/search");
            String resp = restTemplate.postForObject(url, request, String.class);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode points = root.path("result");

            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode p : points) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("score", p.path("score").asDouble());
                JsonNode payload = p.path("payload");
                item.put("doc_id", payload.path("doc_id").asText(""));
                item.put("filename", payload.path("filename").asText(""));
                item.put("page", payload.path("page").asInt(0));
                item.put("chapter", payload.path("chapter").asText(""));
                item.put("stock_code", payload.path("stock_code").asText(""));
                item.put("stock_name", payload.path("stock_name").asText(""));
                item.put("title", payload.path("title").asText(""));
                item.put("content", payload.path("content").asText(""));
                item.put("date", payload.path("date").asText(""));
                item.put("source", payload.path("source").asText(""));
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.warn("Qdrant 检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 写入向量点 */
    public void upsertPoints(List<Map<String, Object>> points) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", points);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            String url = qdrantUrl("/collections/" + COLLECTION_NAME + "/points");
            restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
        } catch (Exception e) {
            log.warn("Qdrant 写入失败: {}", e.getMessage());
        }
    }

    /** 获取 collection 信息 */
    public Map<String, Object> getCollectionInfo() {
        try {
            String url = qdrantUrl("/collections/" + COLLECTION_NAME);
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode result = root.path("result");
            return Map.of(
                    "vectors_count", result.path("points_count").asInt(),
                    "status", result.path("status").asText("unknown")
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    // ==================== 文档页缓存（readPage 工具用） ====================

    private final java.util.concurrent.ConcurrentHashMap<String, PdfParserService.ParsedDocument> docCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ==================== 语义切片（标题感知 + 章节上下文补全） ====================

    /**
     * 基于文档结构切片：
     * 1. 按标题分段（语义切片，替代固定长度）
     * 2. 段内超长时按句边界切分
     * 3. 每个 chunk 前置章节标题上下文
     * 4. 绑定页码元数据
     */
    public List<Map<String, Object>> chunkDocument(PdfParserService.ParsedDocument doc) {
        List<Map<String, Object>> chunks = new ArrayList<>();
        String currentChapter = doc.filename != null ? doc.filename : "文档";

        for (PdfParserService.PageContent page : doc.pages) {
            String pageText = page.text;
            if (pageText == null || pageText.isBlank()) continue;

            // 按标题切分页面内容
            List<String> sections = splitByHeadings(pageText, page.headings);
            for (String section : sections) {
                String sectionText = section.trim();
                if (sectionText.isEmpty()) continue;

                // 检测段首标题，更新当前章节
                String detectedHeading = detectLeadingHeading(sectionText, page.headings);
                if (detectedHeading != null) {
                    currentChapter = detectedHeading;
                    // 章节标题行不参与切片内容
                }

                // 按句边界分块（段落内超长才切）
                for (String piece : splitBySentenceLimit(sectionText, CHUNK_MAX_CHARS)) {
                    if (piece.isBlank()) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("content", piece);
                    item.put("page", page.pageNumber);
                    item.put("chapter", currentChapter);
                    item.put("docId", doc.docId);
                    item.put("title", doc.filename);
                    item.put("source", "上传文档/" + doc.filename);
                    chunks.add(item);
                }
            }
        }
        return chunks;
    }

    /** 按标题位置切分页面 */
    private List<String> splitByHeadings(String pageText, List<PdfParserService.Heading> headings) {
        if (headings == null || headings.isEmpty()) return List.of(pageText);
        List<String> sections = new ArrayList<>();
        int lastIdx = 0;
        for (PdfParserService.Heading h : headings) {
            int idx = pageText.indexOf(h.text);
            if (idx < 0) continue;
            if (idx > lastIdx) {
                sections.add(pageText.substring(lastIdx, idx));
            }
            lastIdx = idx;
        }
        sections.add(pageText.substring(lastIdx));
        return sections;
    }

    /** 检测段落开头是否为标题 */
    private String detectLeadingHeading(String text, List<PdfParserService.Heading> headings) {
        if (headings == null) return null;
        for (PdfParserService.Heading h : headings) {
            if (text.startsWith(h.text)) return h.text;
        }
        return null;
    }

    /** 按句边界限长切分（保留语义完整） */
    private List<String> splitBySentenceLimit(String text, int maxChars) {
        List<String> pieces = new ArrayList<>();
        String[] sentences = text.split("(?<=[。！？\n])");
        StringBuilder current = new StringBuilder();
        for (String sent : sentences) {
            String s = sent.trim();
            if (s.isEmpty()) continue;
            if (current.length() + s.length() > maxChars && !current.isEmpty()) {
                pieces.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(s);
        }
        if (!current.isEmpty()) pieces.add(current.toString().trim());
        return pieces;
    }

    // ==================== 文档上传索引 ====================

    /**
     * 索引 PDF 文档（新版管道）：
     * 解析 → 语义切片 → 章节上下文补全 → Embedding → Qdrant（带页码元数据）
     */
    public Map<String, Object> ingestPdfDocument(PdfParserService.ParsedDocument doc) {
        if (doc == null || doc.pages.isEmpty()) {
            return Map.of("chunks", 0, "message", "未提取到文本内容（可能是扫描件PDF，暂不支持OCR）");
        }

        List<Map<String, Object>> chunks = chunkDocument(doc);
        if (chunks.isEmpty()) {
            return Map.of("chunks", 0, "message", "文档切片为空");
        }

        // 缓存页面文本，供 readPage 工具回读
        docCache.put(doc.docId, doc);

        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += EMBED_BATCH) {
            List<Map<String, Object>> batch = chunks.subList(i, Math.min(i + EMBED_BATCH, chunks.size()));
            // 关键：Embedding 文本带章节上下文前缀，检索文本保持原样
            List<String> texts = batch.stream()
                    .map(c -> "【" + c.get("chapter") + "】\n" + c.get("content"))
                    .collect(Collectors.toList());
            try {
                List<List<Double>> vectors = getEmbeddingsBatch(texts);
                for (int j = 0; j < vectors.size(); j++) {
                    Map<String, Object> c = batch.get(j);
                    String chunkId = md5(doc.docId + "|" + c.get("page") + "|" + c.get("content"));
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("id", chunkId);
                    point.put("vector", vectors.get(j));
                    point.put("payload", Map.of(
                            "doc_id", doc.docId,
                            "filename", doc.filename != null ? doc.filename : "",
                            "page", (Integer) c.get("page"),
                            "chapter", (String) c.get("chapter"),
                            "title", c.get("title"),
                            "content", c.get("content"),
                            "source", c.get("source")
                    ));
                    points.add(point);
                }
            } catch (Exception e) {
                log.warn("文档索引 Embedding 失败: {}", e.getMessage());
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("文档索引完成: {} 共{}片段（带页码+章节元数据）", doc.filename, points.size());
        }
        return Map.of("chunks", points.size(),
                "pages", doc.pages.size(),
                "docId", doc.docId,
                "message", "索引完成");
    }

    /** 兼容旧接口：纯文本直接索引（无页码信息） */
    public int ingestDocument(String content, String filename) {
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (String piece : splitBySentenceLimit(content, CHUNK_MAX_CHARS)) {
            if (piece.isBlank()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("content", piece);
            item.put("page", 0);
            item.put("chapter", filename);
            item.put("docId", "txt_" + System.currentTimeMillis());
            item.put("title", filename);
            item.put("source", "上传文档/" + filename);
            chunks.add(item);
        }
        if (chunks.isEmpty()) return 0;

        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += EMBED_BATCH) {
            List<Map<String, Object>> batch = chunks.subList(i, Math.min(i + EMBED_BATCH, chunks.size()));
            List<String> texts = batch.stream()
                    .map(c -> (String) c.get("content"))
                    .collect(Collectors.toList());
            try {
                List<List<Double>> vectors = getEmbeddingsBatch(texts);
                for (int j = 0; j < vectors.size(); j++) {
                    Map<String, Object> c = batch.get(j);
                    String chunkId = md5((String) c.get("content"));
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("id", chunkId);
                    point.put("vector", vectors.get(j));
                    point.put("payload", Map.of(
                            "doc_id", c.get("docId"),
                            "filename", filename,
                            "page", 0,
                            "chapter", filename,
                            "title", c.get("title"),
                            "content", c.get("content"),
                            "source", c.get("source")
                    ));
                    points.add(point);
                }
            } catch (Exception e) {
                log.warn("文档索引 Embedding 失败: {}", e.getMessage());
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
        }
        return points.size();
    }

    // ==================== readPage / extractTable（Agent 工具支持） ====================

    /** 按文档+页码回读原文页 */
    public String readPage(String docId, int pageNumber) {
        PdfParserService.ParsedDocument doc = docCache.get(docId);
        if (doc == null) return null;
        for (PdfParserService.PageContent page : doc.pages) {
            if (page.pageNumber == pageNumber) return page.text;
        }
        return null;
    }

    /** 抽取某页的表格内容（启发式：数字密度高的行） */
    public String extractTable(String docId, int pageNumber) {
        String pageText = readPage(docId, pageNumber);
        if (pageText == null || pageText.isBlank()) return null;

        StringBuilder table = new StringBuilder();
        for (String line : pageText.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 表格行特征：含数字且空白分隔的列数 >= 3，或数字占比高
            int digitCount = 0;
            for (char ch : trimmed.toCharArray()) {
                if (Character.isDigit(ch) || ch == '.' || ch == '%' || ch == '-' || ch == '+') digitCount++;
            }
            String[] columns = trimmed.split("\\s{2,}");
            if (digitCount >= 6 && columns.length >= 3) {
                table.append(trimmed).append("\n");
            }
        }
        return table.length() > 0 ? table.toString() : null;
    }

    // ==================== 问答 ====================

    public Map<String, Object> ask(String question, int topK) {
        List<Map<String, Object>> docs = search(question, topK);
        if (docs.isEmpty()) {
            return Map.of("answer", "暂无相关数据，请换个问题试试。", "sources", Collections.emptyList());
        }

        // 上下文带页码和章节信息，供 LLM 溯源
        String context = docs.stream()
                .map(d -> {
                    String head = "【" + d.get("filename") + " 第" + d.get("page") + "页"
                            + (d.get("chapter") != null && !d.get("chapter").toString().isBlank()
                            ? " · " + d.get("chapter") : "")
                            + "】";
                    return head + "\n" + d.get("content");
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        String answer = callLlm(question, context);

        // 校验：断言回查原文
        answer = verifyAnswer(question, answer, docs);

        List<Map<String, Object>> sources = docs.stream()
                .map(d -> Map.of(
                        "filename", d.get("filename"),
                        "page", d.get("page"),
                        "chapter", d.get("chapter"),
                        "score", d.get("score")
                ))
                .collect(Collectors.toList());

        return Map.of("answer", answer, "sources", sources);
    }

    /** 输出校验：LLM 断言回查原文，发现编造就修正 */
    private String verifyAnswer(String question, String answer, List<Map<String, Object>> docs) {
        String verifierPrompt = promptsConfig.getReflexion().getReviewer();
        if (verifierPrompt == null || verifierPrompt.isBlank()) return answer;

        try {
            String context = docs.stream()
                    .map(d -> "【" + d.get("filename") + " 第" + d.get("page") + "页】" + d.get("content"))
                    .collect(Collectors.joining("\n---\n"));

            String prompt = verifierPrompt
                    .replace("{question}", question)
                    .replace("{observations}", "检索到的文档原文：\n" + context)
                    .replace("{analysis}", answer);

            String verified = chatModel.call(new Prompt(List.of(new UserMessage(prompt))))
                    .getResult().getOutput().getContent();
            if (verified != null && !verified.isBlank()) {
                // 提取修正后的内容（如果有 corrected 字段）
                try {
                    JsonNode node = objectMapper.readTree(verified);
                    boolean pass = node.path("pass").asBoolean(true);
                    if (!pass) {
                        String corrected = node.path("corrected").asText("");
                        log.warn("RAG答案校验未通过，已修正: {}", node.path("issues").toString());
                        return corrected.isBlank() ? answer : corrected;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("RAG答案校验失败，使用原始答案: {}", e.getMessage());
        }
        return answer;
    }

    private String callLlm(String question, String context) {
        String systemPrompt = promptsConfig.getSystem().getRag();
        String userMessage = promptsConfig.getTemplates().getRagUser()
                .replace("{context}", context)
                .replace("{question}", question);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        ));
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getContent();
    }

    // ==================== 工具方法 ====================

    private String md5(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
