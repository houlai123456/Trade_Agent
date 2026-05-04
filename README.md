# QuantAI — 多Agent A股投研助手

基于 **Spring Boot + DeepSeek + Vue 3 + python** 的个人投研辅助系统，通过多Agent协作分析 + 实时数据 + 自然语言交互，辅助股票投研决策。

## 架构

```
┌──────────┐   ┌──────────────┐   ┌─────────────────────┐
│ 前端     │   │ 后端          │   │ Python 数据层        │
│ Vue 3    │   │ Spring Boot   │   │ Flask + AKShare     │
│ Element+ │←─→│ WebSocket    │←─→│ 行情 / K线 / 资金流向│
│ ECharts  │   │ 多Agent编排   │   │ 新闻爬虫            │
│          │   │ RAG 知识问答  │   └─────────────────────┘
└──────────┘   │ 交易校验       │
               └───────┬───────┘
                       │
               ┌───────▼───────┐
               │  外部服务      │
               │ DeepSeek LLM  │
               │ Qdrant 向量库 │
               │ Redis 缓存    │
               └───────────────┘
```

## Agent 系统

### 1. 多Agent协作流水线
三步分析流程：**舆情分析 → 技术分析 → 综合建议**，前两步**并行执行**。

| Agent | 功能 | 数据源 |
|-------|------|--------|
| 新闻舆情Agent | 新闻情绪分析（利好/利空/中性） | DeepSeek LLM |
| 市场分析Agent | K线趋势、均线、量能分析 | AKShare 实时数据 |
| 交易建议Agent | 综合生成买卖建议 + 风险提示 | 前两个Agent输出 |

### 2. ReAct Agent
自由对话式分析，DeepSeek 自主决定调什么工具、何时回答。

内置工具：`getQuote` / `getKline` / `getFundFlow` / `getNewsSentiment`

### 3. RAG 知识问答
新闻向量化存储（Qdrant + 阿里云 Embedding），支持基于实时新闻的语义检索问答。

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
python app.py
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
| AI | DeepSeek V4 (ReAct), Spring AI, Qdrant |
| 数据 | Python Flask, AKShare, H2/MySQL |

## License

MIT
