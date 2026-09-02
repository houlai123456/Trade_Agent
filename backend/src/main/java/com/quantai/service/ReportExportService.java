package com.quantai.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 报告导出服务
 */
@Slf4j
@Service
public class ReportExportService {

    /**
     * 导出为Markdown文件
     */
    public byte[] exportMarkdown(String report) {
        return report.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 导出为PDF文件（支持中文）
     */
    public byte[] exportPdf(String markdownContent) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDFont font = loadChineseFont(document);

            String[] lines = markdownContent.split("\n");
            List<String> processedLines = preprocessMarkdown(lines);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            float margin = 50;
            float yStart = page.getMediaBox().getHeight() - margin;
            float yPosition = yStart;
            float lineHeight = 18;
            float fontSize = 12;

            contentStream.setFont(font, fontSize);
            contentStream.beginText();
            contentStream.newLineAtOffset(margin, yPosition);

            for (String line : processedLines) {
                if (yPosition < margin + lineHeight) {
                    contentStream.endText();
                    contentStream.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    yPosition = yStart;

                    contentStream.setFont(font, fontSize);
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin, yPosition);
                }

                float currentFontSize = fontSize;

                if (line.startsWith("# ")) {
                    currentFontSize = 20;
                    line = line.substring(2);
                } else if (line.startsWith("## ")) {
                    currentFontSize = 16;
                    line = line.substring(3);
                } else if (line.startsWith("### ")) {
                    currentFontSize = 14;
                    line = line.substring(4);
                }

                if (currentFontSize != fontSize) {
                    contentStream.setFont(font, currentFontSize);
                }

                line = line.replace("**", "").replace("*", "");

                List<String> wrappedLines = wrapText(line, font, currentFontSize,
                        page.getMediaBox().getWidth() - 2 * margin);

                for (String wrappedLine : wrappedLines) {
                    contentStream.showText(wrappedLine);
                    contentStream.newLineAtOffset(0, -lineHeight);
                    yPosition -= lineHeight;

                    if (yPosition < margin + lineHeight) {
                        contentStream.endText();
                        contentStream.close();

                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        yPosition = yStart;

                        contentStream.setFont(font, currentFontSize);
                        contentStream.beginText();
                        contentStream.newLineAtOffset(margin, yPosition);
                    }
                }

                if (currentFontSize != fontSize) {
                    contentStream.setFont(font, fontSize);
                }
            }

            contentStream.endText();
            contentStream.close();

            document.save(baos);
            return baos.toByteArray();
        }
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource("fonts/NotoSansSC-Regular.ttf");
            if (resource.exists()) {
                try (InputStream fontStream = resource.getInputStream()) {
                    return PDType0Font.load(document, fontStream);
                }
            }
        } catch (Exception e) {
            log.warn("无法加载中文字体，使用系统字体", e);
        }

        try {
            String fontPath = "C:/Windows/Fonts/msyh.ttc";
            return PDType0Font.load(document, new java.io.File(fontPath));
        } catch (Exception e) {
            log.error("加载中文字体失败", e);
            throw new IOException("无法加载中文字体，请确保系统安装了中文字体");
        }
    }

    private List<String> preprocessMarkdown(String[] lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                result.add("");
            } else if (line.startsWith("|") || line.contains("---|")) {
                continue;
            } else if (line.startsWith("- ") || line.matches("^\\d+\\.\\s.*")) {
                result.add(line);
            } else {
                result.add(line);
            }
        }
        return result;
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text.trim().isEmpty()) {
            lines.add("");
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        for (char c : text.toCharArray()) {
            String testLine = currentLine.toString() + c;
            float width = font.getStringWidth(testLine) / 1000 * fontSize;

            if (width > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                currentLine.append(c);
            } else {
                currentLine.append(c);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}
