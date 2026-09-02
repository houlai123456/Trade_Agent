package com.quantai.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析服务 — 按页提取文本 + 字号分析识别标题层级
 */
@Slf4j
@Service
public class PdfParserService {

    /** 解析结果：文档结构 */
    public static class ParsedDocument {
        public String docId;
        public String filename;
        public List<PageContent> pages = new ArrayList<>();
    }

    /** 单页内容 */
    public static class PageContent {
        public int pageNumber;
        public String text;
        /** 该页检测到的标题（按出现顺序） */
        public List<Heading> headings = new ArrayList<>();
    }

    /** 标题信息 */
    public static class Heading {
        public int pageNumber;
        public String text;
        public float fontSize;
    }

    /**
     * 解析 PDF 文件
     * @return 解析后的文档结构，页面从 1 开始编号
     */
    public ParsedDocument parse(InputStream pdfStream, String filename) throws IOException {
        ParsedDocument doc = new ParsedDocument();
        doc.docId = "doc_" + System.currentTimeMillis();
        doc.filename = filename;

        try (PDDocument pdf = PDDocument.load(pdfStream)) {
            int totalPages = pdf.getNumberOfPages();

            // 第一遍：整篇提取，计算正文字号基线
            FontAwareStripper probe = new FontAwareStripper();
            probe.setSortByPosition(true);
            probe.getText(pdf);  // 触发 processTextPosition，收集所有字号
            float bodyFontSize = probe.estimateBodyFontSize();

            // 第二遍：按页提取，识别标题
            for (int i = 0; i < totalPages; i++) {
                FontAwareStripper stripper = new FontAwareStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                stripper.setBodyFontSize(bodyFontSize);

                String pageText = stripper.getText(pdf);
                if (pageText == null || pageText.isBlank()) continue;

                PageContent page = new PageContent();
                page.pageNumber = i + 1;
                page.text = pageText.trim();
                page.headings = stripper.extractHeadings();
                doc.pages.add(page);
            }

            log.info("PDF解析完成: {} 共{}页, 正文字号{}pt, 检测到{}个标题",
                    filename, doc.pages.size(), String.format("%.1f", bodyFontSize),
                    doc.pages.stream().mapToInt(p -> p.headings.size()).sum());
        }
        return doc;
    }

    /** 检测扫描件：没有任何可提取文字 */
    public boolean isScanned(ParsedDocument doc) {
        return doc.pages.isEmpty();
    }

    /**
     * 自定义 TextStripper：收集字号信息，用于区分标题和正文
     */
    private static class FontAwareStripper extends PDFTextStripper {
        private final List<TextPosition> textPositions = new ArrayList<>();
        private float bodyFontSize = -1;

        FontAwareStripper() throws IOException {
            super();
        }

        void setBodyFontSize(float size) {
            this.bodyFontSize = size;
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            textPositions.add(text);
            super.processTextPosition(text);
        }

        /** 估算正文字号：取出现次数最多的字号（加权字数） */
        float estimateBodyFontSize() {
            if (textPositions.isEmpty()) return 11f;
            java.util.Map<Float, Integer> count = new java.util.LinkedHashMap<>();
            for (TextPosition tp : textPositions) {
                float size = Math.round(tp.getFontSizeInPt() * 2) / 2f;
                count.merge(size, 1, Integer::sum);
            }
            return count.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse(11f);
        }

        /** 提取标题：字号明显大于正文的文本行 */
        List<Heading> extractHeadings() {
            List<Heading> headings = new ArrayList<>();
            if (bodyFontSize <= 0) return headings;

            try {
                String fullText = getText(null);
                // 重新逐行分析 — 简化方案：按行判断首字符字号
                String[] lines = fullText.split("\\r?\\n");
                java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.length() > 40) continue;
                    // 标题特征：短行 + 字号大于正文
                    float lineSize = findFirstCharSize(trimmed);
                    if (lineSize > bodyFontSize + 1.5f && !seen.contains(trimmed)) {
                        seen.add(trimmed);
                        Heading h = new Heading();
                        h.text = trimmed;
                        h.fontSize = lineSize;
                        h.pageNumber = getCurrentPageNo();
                        headings.add(h);
                    }
                }
            } catch (Exception ignored) {}
            return headings;
        }

        private float findFirstCharSize(String text) {
            for (TextPosition tp : textPositions) {
                if (tp.getUnicode() != null && !tp.getUnicode().isBlank()
                        && text.contains(tp.getUnicode().trim())) {
                    return tp.getFontSizeInPt();
                }
            }
            return bodyFontSize;
        }
    }
}
