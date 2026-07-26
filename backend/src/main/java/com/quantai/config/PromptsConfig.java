package com.quantai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prompt 配置中心 — 从 prompts.yml 直接读取
 * 改 Prompt 只需改 YML 文件，不需要重新编译代码
 */
@Slf4j
@Data
@Component
public class PromptsConfig {

    private SystemPrompts system = new SystemPrompts();
    private Templates templates = new Templates();
    private Fallback fallback = new Fallback();
    private Reflexion reflexion = new Reflexion();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts.yml");
            Yaml yaml = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yaml.loadAs(resource.getInputStream(), Map.class);
            if (root == null) {
                log.error("prompts.yml is empty or missing");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> prompts = (Map<String, Object>) root.get("prompts");
            if (prompts == null) {
                log.error("prompts.yml missing 'prompts:' root key");
                return;
            }

            // 加载 system
            @SuppressWarnings("unchecked")
            Map<String, Object> sys = (Map<String, Object>) prompts.get("system");
            if (sys != null) {
                system.setChatAssistant(asStr(sys.get("chat-assistant")));
                system.setStockContextInject(asStr(sys.get("stock-context-inject")));
                system.setSuggestionAgent(asStr(sys.get("suggestion-agent")));
                system.setReactAgent(asStr(sys.get("react-agent")));
                system.setRag(asStr(sys.get("rag")));
                system.setTradeParser(asStr(sys.get("trade-parser")));
            }

            // 加载 templates
            @SuppressWarnings("unchecked")
            Map<String, Object> tmpl = (Map<String, Object>) prompts.get("templates");
            if (tmpl != null) {
                templates.setStockAnalysis(asStr(tmpl.get("stock-analysis")));
                templates.setFinanceAnalysis(asStr(tmpl.get("finance-analysis")));
                templates.setFinanceCompare(asStr(tmpl.get("finance-compare")));
                templates.setSentimentAnalysis(asStr(tmpl.get("sentiment-analysis")));
                templates.setRagUser(asStr(tmpl.get("rag-user")));
            }

            // 加载 fallback
            @SuppressWarnings("unchecked")
            Map<String, Object> fb = (Map<String, Object>) prompts.get("fallback");
            if (fb != null) {
                fallback.setLlmError(asStr(fb.get("llm-error")));
                fallback.setTimeout(asStr(fb.get("timeout")));
            }

            // 加载 reflexion
            @SuppressWarnings("unchecked")
            Map<String, Object> rf = (Map<String, Object>) prompts.get("reflexion");
            if (rf != null) {
                reflexion.setReviewer(asStr(rf.get("reviewer")));
            }

            log.info("PromptsConfig loaded: {} system, {} templates, {} fallbacks, {} reflexion",
                    countNonNull(system), countNonNull(templates), countNonNull(fallback), countNonNull(reflexion));
        } catch (Exception e) {
            log.error("Failed to load prompts.yml", e);
        }
    }

    private String asStr(Object val) {
        return val != null ? val.toString() : null;
    }

    private int countNonNull(Object obj) {
        int count = 0;
        try {
            for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if (f.get(obj) != null) count++;
            }
        } catch (Exception ignored) {}
        return count;
    }

    @Data
    public static class SystemPrompts {
        private String chatAssistant;
        private String stockContextInject;
        private String suggestionAgent;
        private String reactAgent;
        private String rag;
        private String tradeParser;
    }

    @Data
    public static class Templates {
        private String stockAnalysis;
        private String financeAnalysis;
        private String financeCompare;
        private String sentimentAnalysis;
        private String ragUser;
    }

    @Data
    public static class Fallback {
        private String llmError;
        private String timeout;
    }

    @Data
    public static class Reflexion {
        private String reviewer;
    }
}
