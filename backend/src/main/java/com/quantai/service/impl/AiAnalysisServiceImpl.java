package com.quantai.service.impl;

import com.quantai.model.entity.StockQuote;
import com.quantai.service.AiAnalysisService;
import com.quantai.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final OpenAiChatModel chatModel;
    private final StockService stockService;

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

        return chat(prompt, code);
    }

    @Override
    public String analyzeSentiment(String title, String content) {
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
        return chat(prompt, null);
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

        if (stockCode != null && !stockCode.isBlank()) {
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
