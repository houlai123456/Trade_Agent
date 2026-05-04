# Trade Agent — A股智能交易助手

基于 **Spring Boot + DeepSeek + Vue 3 + Python** 的多Agent A股智能交易辅助系统，通过多Agent协同分析 + 实时行情 + 自然语言交互，为用户提供舆情分析、技术诊断、交易建议的全链路服务，并支持自定义盯盘规则与条件单自动交易。

## 架构

```
┌──────────┐   ┌──────────────┐   ┌─────────────────────┐
│ 前端     │   │ 后端          │   │ Python 数据层        │
│ Vue 3    │   │ Spring Boot   │   │ Flask + AKShare     │
│ Element+ │←─→│ WebSocket    │←─→│ 行情 / K线 / 资金流向│
│ ECharts  │   │ 多Agent编排   │   │ 新闻爬虫 / 搜索     │
│          │   │ ReAct Agent   │   └─────────────────────┘
└──────────┘   │ RAG 知识库    │
               │ 盯盘/条件单   │
               └───────┬───────┘
                       │
               ┌───────▼───────┐
               │  外部服务      │
               │ DeepSeek LLM  │
               │ Qdrant 向量库 │
               │ Redis 缓存    │
               └───────────────┘
```

## 功能模块

### 1. 多Agent协作分析
三步分析流水线：**舆情分析 → 技术分析 → 综合建议**，前两步并行执行，降低端到端延迟。

| Agent | 功能 | 数据源 |
|-------|------|--------|
| 新闻舆情Agent | 新闻情绪分析（利好/利空/中性） | DeepSeek LLM |
| 市场分析Agent | K线趋势、均线、量能分析 | AKShare 实时数据 |
| 交易建议Agent | 综合生成买卖建议 + 风险提示 | 前两个Agent输出 |

内置校验机制：Agent输出异常时自动降级为规则兜底方案。

### 2. ReAct 自主推理
基于 DeepSeek 实现 Thought→Action→Observation 循环，Agent 根据用户提问自动决策调用哪些工具，无需预设分析路径。

内置工具：`getQuote` / `getKline` / `getFundFlow` / `getNewsSentiment` / `searchRag`

### 3. RAG 知识库
支持上传 PDF/TXT 文档，通过 Qdrant 向量库 + 阿里云 Embedding 构建私有知识索引，并封装为 ReAct Agent 的可调用工具，使分析结论可引用内部非公开资料。

### 4. 实时行情看板
- 自选股实时行情（WebSocket 推送，1秒刷新）
- 主要指数（上证、深证、创业板、科创50）
- 市场概况（涨跌家数、涨跌停、成交额）
- A股板块行情（主板/创业板/科创板/北交所）
- 个股K线（日/周/月 + MA均线 + 量能）
- 资金流向、北向资金、五日分时图
- 热点板块排名、概念板块
- 异动预警推送

### 5. 盯盘与条件单
- 自定义价格预警规则（高于/低于指定价），触发时实时推送通知
- 条件单支持涨破/跌破自动触发限价挂单，打通监控到交易执行闭环
- 交易时段每10秒自动扫描，无需人工值守

## 快速启动

### 前置条件

- JDK 17+
- Node.js 18+
- Python 3.10+
- Redis（可选，支持降级）
- Qdrant（可选，仅RAG需要）

### API Key

1. 复制 `.env.example` 为 `.env`
2. 填入你的 API Key：

```
DEEPSEEK_API_KEY=sk-your-key-here
ALIYUN_API_KEY=sk-your-key-here
```

### 启动

**后端（Spring Boot）：**
```bash
cd backend
mvn spring-boot:run
```

**Python 数据服务：**
```bash
cd backend
pip install -r requirements.txt
python data_service.py
```

**前端：**
```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3, Vite 5, Element Plus, ECharts, Pinia |
| 后端 | Spring Boot 3.2, MyBatis-Plus, Redis, WebSocket |
| AI | DeepSeek (ReAct), Spring AI, Qdrant |
| 数据 | Python Flask, AKShare, H2/MySQL |

## License

MIT
