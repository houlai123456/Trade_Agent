package com.quantai.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG知识库服务（简化版）
 * 存储股票基础知识，支持关键词检索
 * 完整RAG需要向量数据库，这里用关键词匹配简化实现
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final Map<String, List<KnowledgeEntry>> knowledgeMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        initBasicKnowledge();
        log.info("知识库初始化完成，共加载 {} 条知识", countEntries());
    }

    /**
     * 检索相关知识
     */
    public List<String> search(String query, int limit) {
        if (query == null || query.isBlank()) return Collections.emptyList();

        String q = query.toLowerCase();
        List<KnowledgeEntry> results = new ArrayList<>();

        for (Map.Entry<String, List<KnowledgeEntry>> entry : knowledgeMap.entrySet()) {
            if (q.contains(entry.getKey()) || entry.getKey().contains(q)) {
                results.addAll(entry.getValue());
            }
        }

        return results.stream()
                .limit(limit)
                .map(KnowledgeEntry::content)
                .toList();
    }

    private void addKnowledge(String keyword, String title, String content) {
        knowledgeMap.computeIfAbsent(keyword, k -> new ArrayList<>())
                .add(new KnowledgeEntry(title, content));
    }

    private void initBasicKnowledge() {
        addKnowledge("市盈率", "市盈率（PE）",
                "市盈率 = 股价 / 每股收益。是评估股票估值水平的重要指标。" +
                "低市盈率可能表示股票被低估，高市盈率可能表示市场对公司未来增长有较高预期。");

        addKnowledge("市净率", "市净率（PB）",
                "市净率 = 股价 / 每股净资产。用于衡量股票价格相对于公司净资产的价值。" +
                "PB < 1 通常被视为低估值。");

        addKnowledge("换手率", "换手率",
                "换手率 = 成交量 / 流通股本 × 100%。反映股票交易的活跃程度。" +
                "高换手率通常意味着市场关注度高，但过高的换手率可能暗示投机氛围浓厚。");

        addKnowledge("成交量", "成交量",
                "成交量指在一定时间内股票交易的数量。是判断市场活跃度和趋势强度的重要指标。" +
                "价涨量增是健康的上涨趋势，价涨量缩则需警惕。");

        addKnowledge("MA", "移动平均线（MA）",
                "移动平均线是平滑价格走势的常用技术指标。常见周期有MA5（5日均线）、MA10、MA20、MA60等。" +
                "短期均线上穿长期均线（金叉）通常被视为买入信号，反之（死叉）为卖出信号。");

        addKnowledge("KDJ", "KDJ指标",
                "KDJ是一种随机指标，用于判断市场的超买超卖状态。" +
                "K值在0-100之间，D值大于80为超买区，小于20为超卖区。J值超过100或低于0时往往意味着行情反转。");

        addKnowledge("MACD", "MACD指标",
                "MACD（指数平滑异同移动平均线）是追踪趋势动量的技术指标。" +
                "由快线（DIF）、慢线（DEA）和柱状图（MACD柱）组成。" +
                "DIF上穿DEA为金叉，下穿为死叉。");

        addKnowledge("涨停", "涨停板制度",
                "A股市场实行涨停板制度，主板股票涨跌幅限制为10%，创业板和科创板为20%。" +
                "涨停时股票达到当日最大涨幅限制并暂停交易。");

        addKnowledge("跌停", "跌停板制度",
                "跌停是指股票价格下跌达到当日最大跌幅限制。" +
                "主板股票跌停幅度为10%，创业板和科创板为20%。");

        addKnowledge("上证指数", "上证指数",
                "上证综合指数（简称上证指数）是反映上海证券交易所所有上市股票整体表现的指数。" +
                "是中国股市最重要的基准指数之一。");

        addKnowledge("北向资金", "北向资金",
                "北向资金是指通过沪港通、深港通从香港股市流入A股市场的资金。" +
                "北向资金的流入流出情况被视为外资对A股市场态度的重要参考指标。");
    }

    private int countEntries() {
        return knowledgeMap.values().stream().mapToInt(List::size).sum();
    }

    private record KnowledgeEntry(String title, String content) {
    }
}
