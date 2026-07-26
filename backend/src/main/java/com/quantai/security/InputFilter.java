package com.quantai.security;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 用户输入安全过滤
 */
@Slf4j
public final class InputFilter {

    private InputFilter() {}

    private static final int MAX_INPUT_LENGTH = 5000;

    // Prompt injection 检测模式
    private static final Pattern[] INJECTION_PATTERNS = {
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|prior|system)\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)(you\\s+are|act\\s+as|pretend\\s+to\\s+be|roleplay\\s+as)\\s+(now|from\\s+now)"),
            Pattern.compile("(?i)(forget|disregard|override|delete)\\s+(all|your|the)\\s+(instructions?|rules?|prompts?)"),
            Pattern.compile("(?i)system\\s*(prompt|message|instruction)\\s*:"),
            Pattern.compile("(?i)\\bDAN\\b|\\bJailbreak\\b"),
            Pattern.compile("(?i)respond\\s+(as|like)\\s+a\\s+(different|another)\\s+(ai|assistant|model)"),
    };

    // 投资合规敏感模式（输出侧检测）
    private static final Pattern[] COMPLIANCE_PATTERNS = {
            Pattern.compile("(一定|肯定|绝对|100%|百分百)\\s*(会|能|可以)\\s*(涨|涨到|赚钱|盈利)"),
            Pattern.compile("(保证|承诺)\\s*(收益|盈利|赚钱|翻倍)"),
            Pattern.compile("(无风险|零风险|稳赚|包赚)"),
    };

    public static FilterResult check(String input) {
        if (input == null || input.isBlank()) {
            return FilterResult.PASS;
        }

        // 长度限制
        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("Input too long: {} chars", input.length());
            return new FilterResult(false, truncate(input, MAX_INPUT_LENGTH),
                    "输入过长，已自动截断至" + MAX_INPUT_LENGTH + "字符");
        }

        // Injection 检测
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(input).find()) {
                log.warn("Prompt injection detected: {}", p.pattern());
                return new FilterResult(false, sanitize(input),
                        "检测到异常输入模式，已进行安全处理");
            }
        }

        return FilterResult.PASS;
    }

    /** 检测输出是否包含违规投资承诺 */
    public static boolean hasComplianceIssue(String output) {
        if (output == null) return false;
        for (Pattern p : COMPLIANCE_PATTERNS) {
            if (p.matcher(output).find()) {
                log.warn("Compliance issue in output: {}", p.pattern());
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String input) {
        if (input == null) return null;
        return input.length() > 2000 ? truncate(input, 2000) : input;
    }

    private static String truncate(String s, int maxLen) {
        return s.substring(0, maxLen) + "...(已截断)";
    }

    public static class FilterResult {
        public static final FilterResult PASS = new FilterResult(true, null, null);

        public final boolean passed;
        public final String sanitizedInput;
        public final String warning;

        public FilterResult(boolean passed, String sanitizedInput, String warning) {
            this.passed = passed;
            this.sanitizedInput = sanitizedInput;
            this.warning = warning;
        }
    }
}
