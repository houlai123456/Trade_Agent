package com.quantai.service.impl;

import com.quantai.model.entity.StockQuote;
import com.quantai.service.AiAnalysisService;
import com.quantai.service.DataServiceClient;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final OpenAiChatModel chatModel;
    private final StockService stockService;
    private final DataServiceClient dataServiceClient;
    private final RedisTemplate<String, Object> redisTemplate;

    // ========== Redis 缓存 Key 前缀 & TTL ==========
    private static final String CACHE_PREFIX = "ai:";
    private static final long TTL_STOCK = 300;        // 行情分析 5分钟
    private static final long TTL_FINANCE = 3600;     // 财报解读 1小时
    private static final long TTL_SENTIMENT = 3600;   // 情感分析 1小时

    private String cacheGet(String key) {
        try {
            return (String) redisTemplate.opsForValue().get(CACHE_PREFIX + key);
        } catch (Exception e) {
            log.warn("Redis读取失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void cachePut(String key, String value, long ttlSec) {
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + key, value, ttlSec, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Redis不可用时静默降级
        }
    }

    @Override
    public Flux<ChatResponse> chatStream(String message, String stockCode) {
        List<Message> messages = buildMessages(message, stockCode);
        Prompt prompt = new Prompt(messages);
        return chatModel.stream(prompt);
    }

    @Override
    public String chat(String message, String stockCode) {
        List<Message> messages = buildMessages(message, stockCode);
        Prompt prompt = new Prompt(messages);
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getContent();
    }

    @Override
    public String analyzeStock(String code) {
        String cacheKey = "stock:" + code;
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            log.debug("Redis命中缓存 analyzeStock({})", code);
            return cached;
        }

        StockQuote quote = stockService.getQuote(code);
        if (quote == null || quote.getCurrentPrice() == null) {
            return "未能获取到股票【" + code + "】的实时行情数据。\n" +
                    "可能原因：\n" +
                    "1. 股票代码格式不正确（上证用sh前缀，深证用sz前缀，如 sh600519）\n" +
                    "2. 当前为非交易时段，新浪API无数据返回\n" +
                    "3. 网络连接问题";
        }

        String marketContext = buildMarketContext(quote);
        String prompt = String.format(
                "你是一位专业的中国A股市场分析师。以下是通过新浪财经API实时获取的行情数据（数据获取时间：%s）：\n\n" +
                        "%s\n\n" +
                        "请基于以上真实的实时数据对股票【%s(%s)】进行解读分析，注意：\n" +
                        "1. 这是从新浪财经API实时拉取的数据，不是你的训练数据\n" +
                        "2. 今日行情概况\n" +
                        "3. 技术面简析（涨跌幅、量价关系）\n" +
                        "4. 短期走势判断\n" +
                        "5. 风险提示\n\n" +
                        "请用简洁专业的语言回答，控制在500字以内。",
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                marketContext,
                quote.getName(), quote.getCode()
        );

        String result = chat(prompt, code);
        cachePut(cacheKey, result, TTL_STOCK);
        return result;
    }

    @Override
    public String analyzeFinance(String code) {
        String cacheKey = "finance:" + code;
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            log.debug("Redis命中缓存 analyzeFinance({})", code);
            return cached;
        }

        StockQuote quote = stockService.getQuote(code);
        String stockName = quote != null && quote.getName() != null ? quote.getName() : code;

        java.util.List<java.util.Map<String, Object>> financeList = dataServiceClient.fetchFinance(code);
        if (financeList == null || financeList.isEmpty()) {
            return "未能获取到股票【" + stockName + "(" + code + ")】的财务数据，请稍后重试。";
        }

        int endIdx = Math.min(financeList.size(), 20);
        int startIdx = Math.max(0, endIdx - 4);
        java.util.List<java.util.Map<String, Object>> recentData = financeList.subList(startIdx, endIdx);

        StringBuilder financeText = new StringBuilder();
        for (java.util.Map<String, Object> row : recentData) {
            financeText.append("报告期：").append(row.getOrDefault("报告期", "")).append("\n");
            String[] keys = {
                "营业总收入", "营业总收入同比增长", "净利润", "净利润同比增长",
                "基本每股收益", "每股净资产", "净资产收益率",
                "资产负债率", "流动比率", "速动比率",
                "毛利率", "净利率",
                "经营活动现金流量净额", "投资活动现金流量净额", "筹资活动现金流量净额",
                "研发费用", "研发费用占营业总收入比例",
                "存货周转率", "应收账款周转率", "总资产周转率",
                "营业总收入环比增长率", "净利润环比增长率"
            };
            for (String key : keys) {
                Object val = row.get(key);
                if (val != null && !val.toString().isEmpty()) {
                    financeText.append("  ").append(key).append("：").append(val).append("\n");
                }
            }
            financeText.append("\n");
        }

        if (financeText.length() == 0) {
            return "股票【" + stockName + "(" + code + ")】的财务数据为空。";
        }

        String prompt = String.format("""
                你是一位资深的A股财务分析师。以下是股票【%s(%s)】最近几个报告期的财务指标数据：

                %s

                请从以下维度进行专业解读（用Markdown格式输出，控制在800字以内）：

                ### 一、营收与利润趋势
                分析营业总收入和净利润的变化趋势，同比增长情况，环比变化

                ### 二、盈利能力
                分析毛利率、净利率、净资产收益率(ROE)的水平与变化

                ### 三、财务健康度
                分析资产负债率、流动比率、速动比率，判断偿债能力和财务风险

                ### 四、现金流状况
                分析经营/投资/筹资三大现金流情况，判断现金流健康度

                ### 五、运营效率
                分析存货周转、应收账款周转、总资产周转等指标

                ### 六、综合评价
                给出整体评价和需要关注的风险点

                注意：
                - 使用具体数据说话，不要泛泛而谈
                - 趋势对比要指明变动方向和幅度
                - 风险提示要具体明确
                - 不推荐买卖，仅做基本面分析
                """,
                stockName, code, financeText.toString());

        String result = chat(prompt, code);
        cachePut(cacheKey, result, TTL_FINANCE);
        return result;
    }

    @Override
    public String analyzeFinanceCompare(String code1, String code2) {
        String cacheKey = "compare:" + code1 + ":" + code2;
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            log.debug("Redis命中缓存 analyzeFinanceCompare({}, {})", code1, code2);
            return cached;
        }

        StockQuote q1 = stockService.getQuote(code1);
        StockQuote q2 = stockService.getQuote(code2);
        String n1 = q1 != null && q1.getName() != null ? q1.getName() : code1;
        String n2 = q2 != null && q2.getName() != null ? q2.getName() : code2;

        String f1 = buildFinanceSummary(code1, n1);
        String f2 = buildFinanceSummary(code2, n2);
        if (f1 == null || f2 == null) {
            return "未能获取财务数据，请检查股票代码。";
        }

        String prompt = String.format("""
                你是一位资深的A股财务分析师。请对比分析以下两只股票的财务数据。

                【%s(%s)】
                %s

                【%s(%s)】
                %s

                请从以下维度进行对比分析（Markdown格式，控制在600字以内）：

                ### 一、营收规模对比
                比较两家公司的营业总收入和体量差异

                ### 二、盈利能力对比
                比较毛利率、净利率、ROE

                ### 三、增长对比
                比较营收和净利润的同比增长

                ### 四、财务健康度对比
                比较资产负债率、现金流状况

                ### 五、综合评价
                给出对比结论和各自的优势/风险点

                注意：使用具体数据，客观对比，不推荐买卖。
                """,
                n1, code1, f1, n2, code2, f2);

        String result = chat(prompt, code1);
        cachePut(cacheKey, result, TTL_FINANCE);
        return result;
    }

    /** 提取财报关键数据摘要，供对比使用 */
    private String buildFinanceSummary(String code, String name) {
        java.util.List<java.util.Map<String, Object>> list = dataServiceClient.fetchFinance(code);
        if (list == null || list.isEmpty()) return null;

        int idx = Math.min(list.size() - 1, 3);
        StringBuilder sb = new StringBuilder();
        java.util.Map<String, Object> latest = list.get(idx);
        sb.append("报告期：").append(latest.getOrDefault("报告期", "")).append("\n");
        String[] keys = {
            "营业总收入", "营业总收入同比增长", "净利润", "净利润同比增长",
            "基本每股收益", "每股净资产", "净资产收益率",
            "资产负债率", "毛利率", "净利率",
            "经营活动现金流量净额", "研发费用"
        };
        for (String k : keys) {
            Object v = latest.get(k);
            if (v != null && !v.toString().isEmpty()) {
                sb.append(k).append("：").append(v).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String analyzeSentiment(String title, String content) {
        String cacheKey = "sentiment:" + md5((title != null ? title : "") + (content != null ? content : ""));
        String cached = cacheGet(cacheKey);
        if (cached != null) {
            log.debug("Redis命中缓存 analyzeSentiment");
            return cached;
        }

        String prompt = String.format(
                "你是一位金融舆情分析师。请分析以下财经新闻的情绪倾向，以及它影响哪些股票或板块。\n" +
                        "标题：%s\n内容：%s\n\n" +
                        "请严格按照以下JSON格式返回（不要包含其他文字）：\n" +
                        "{\"sentiment\": \"POSITIVE|NEGATIVE|NEUTRAL\", \"score\": 0.0, \"summary\": \"一句话总结\", \"affected_stocks\": [\"股票代码或板块名\"]}\n" +
                        "sentiment取值：POSITIVE(利好), NEGATIVE(利空), NEUTRAL(中性)\n" +
                        "score范围：-1.0(极度利空) 到 1.0(极度利好)\n" +
                        "affected_stocks：列出该新闻可能影响的股票代码或板块名称，如不确定则为空数组\n\n" +
                        "注意：请严格区分有利好和中性。常规财报发布、例行公告、指数常规涨跌、行业常规数据披露等无明显利好信号的新闻，应判为NEUTRAL，不要因为公司业绩增长就自动判为POSITIVE，只有明确超出预期或包含正面引导的才判为POSITIVE。同理，只有明确提及重大利空（亏损、罚款、诉讼、监管处罚等）才判为NEGATIVE。",
                title, content
        );
        String result = chat(prompt, null);
        cachePut(cacheKey, result, TTL_SENTIMENT);
        return result;
    }

    private static String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private List<Message> buildMessages(String message, String stockCode) {
        List<Message> messages = new ArrayList<>();

        String systemPrompt = """
                你是QuantAI智能股票助手，一位专业的中国A股市场分析师。
                你的核心能力：
                1. 解读通过新浪财经API实时获取的股票行情数据，提供专业分析
                2. 回答股票投资相关问题（不提供具体买卖建议）
                3. 解释金融专业术语和概念
                4. 分析财经新闻对股市的影响

                回答要求：
                - 基于我提供的实时数据进行分析，不要依赖你的训练数据中的历史价格
                - 如果我在对话中提供了实时行情数据，你必须以那个数据为准
                - 明确指出分析的不确定性
                - 不承诺收益，不推荐具体买卖时点
                - 用语专业、简洁、易懂
                - 涉及数据时给出具体数值
                """;
        messages.add(new SystemMessage(systemPrompt));

        if (stockCode != null && !stockCode.trim().isEmpty()) {
            StockQuote quote = stockService.getQuote(stockCode);
            if (quote != null && quote.getCurrentPrice() != null) {
                String context = buildMarketContext(quote);
                messages.add(new SystemMessage("【系统】当前关联股票的实时行情数据（来自新浪财经API，获取时间："
                        + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        + "）：\n" + context));
            }
        }

        messages.add(new UserMessage(message));
        return messages;
    }

    private String buildMarketContext(StockQuote quote) {
        StringBuilder sb = new StringBuilder();
        sb.append("股票代码：").append(quote.getCode()).append("\n");
        sb.append("股票名称：").append(nullToEmpty(quote.getName())).append("\n");
        sb.append("当前价格：").append(nvl(quote.getCurrentPrice())).append("\n");
        sb.append("今日开盘：").append(nvl(quote.getOpenPrice())).append("\n");
        sb.append("昨日收盘：").append(nvl(quote.getYesterdayClose())).append("\n");
        sb.append("今日最高：").append(nvl(quote.getHighPrice())).append("\n");
        sb.append("今日最低：").append(nvl(quote.getLowPrice())).append("\n");
        sb.append("涨跌幅：").append(nvl(quote.getChangePercent())).append("%\n");
        sb.append("涨跌额：").append(nvl(quote.getChangeAmount())).append("\n");
        sb.append("成交量：").append(quote.getVolume() != null ? quote.getVolume() : 0).append("股\n");
        sb.append("成交额：").append(nvl(quote.getAmount())).append("元\n");
        if (quote.getTime() != null) {
            sb.append("数据时间：").append(quote.getTime()).append("\n");
        }
        sb.append("数据来源：新浪财经免费API（实时数据）");
        return sb.toString();
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private double nvl(BigDecimal val) {
        return val != null ? val.doubleValue() : 0.0;
    }
}
