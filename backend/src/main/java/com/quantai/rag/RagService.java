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

    // ==================== 文本切片 ====================

    public List<Map<String, Object>> chunkText(String content, String title, String source) {
        String[] sentences = content.split("(?<=[。！？\n])");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sent : sentences) {
            sent = sent.trim();
            if (sent.isEmpty()) continue;
            if (current.length() + sent.length() > CHUNK_MAX_CHARS && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
                if (CHUNK_OVERLAP > 0 && chunks.size() > 0) {
                    String last = chunks.get(chunks.size() - 1);
                    current.append(last.substring(Math.max(0, last.length() - CHUNK_OVERLAP)));
                }
            }
            current.append(sent);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("content", chunks.get(i));
            item.put("chunk_index", i);
            item.put("total_chunks", chunks.size());
            item.put("title", title);
            item.put("source", source);
            result.add(item);
        }
        return result;
    }

    // ==================== 文档上传索引 ====================

    public int ingestDocument(String content, String filename) {
        List<Map<String, Object>> chunks = chunkText(content, filename, "上传文档/" + filename);
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
                            "stock_code", "",
                            "stock_name", filename,
                            "title", c.get("title"),
                            "content", c.get("content"),
                            "date", "",
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
            log.info("文档索引完成: {} 条片段", points.size());
        }
        return points.size();
    }

    // ==================== 问答 ====================

    public Map<String, Object> ask(String question, int topK) {
        List<Map<String, Object>> docs = search(question, topK);
        if (docs.isEmpty()) {
            return Map.of("answer", "暂无相关数据，请换个问题试试。", "sources", Collections.emptyList());
        }

        String context = docs.stream()
                .map(d -> "[" + d.get("stock_name") + "(" + d.get("stock_code") + ")] "
                        + d.get("title") + "\n" + d.get("content"))
                .collect(Collectors.joining("\n\n---\n\n"));

        String answer = callLlm(question, context);

        List<Map<String, Object>> sources = docs.stream()
                .map(d -> Map.of(
                        "stock_name", d.get("stock_name"),
                        "title", d.get("title"),
                        "score", d.get("score")
                ))
                .collect(Collectors.toList());

        return Map.of("answer", answer, "sources", sources);
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
