# Trade Agent — A股智能交易助手

基于 **Spring Boot + DeepSeek + Vue 3 + Python** 的智能交易辅助系统。以 ReAct 自主推理引擎为核心，配合实时行情、自然语言交互、飞书消息触达，为用户提供行情分析、财务诊断、交易建议的全链路服务。

## 架构

```
┌──────────┐   ┌───────────────────────────┐   ┌─────────────────────┐
│ 前端     │   │ 后端 (Spring Boot 3.2)    │   │ Python 数据层        │
│ Vue 3    │   │                           │   │ Flask + AKShare     │
│ Element+ │   │ ┌───────────────────────┐ │   │ 行情 / K线 / 资金流向│
│ ECharts  │←─→│ │ Agent 推理引擎         │ │←─→│ 新闻 / 财报 / 龙虎榜│
│          │   │ │ ReAct + Plan-Execute  │ │   └─────────────────────┘
└──────────┘   │ │ Reflexion 自省审查    │ │
               │ └───────────────────────┘ │
               │ ┌───────────────────────┐ │   ┌─────────────────────┐
               │ │ Agent 工程基座         │ │   │ 外部服务              │
               │ │ ToolRegistry 工具注册 │ │──→│ DeepSeek LLM         │
               │ │ 全链路 Trace 追踪     │ │   │ Qdrant 向量库        │
               │ │ Harness 安全护栏      │ │   │ Redis 缓存           │
               │ │ Prompt 配置中心       │ │   │ 飞书消息推送          │
               │ │ 会话持久化 (H2/MySQL) │ │   └─────────────────────┘
               │ └───────────────────────┘ │
               │ 盯盘 / 条件单 / 模拟交易  │
               └───────────────────────────┘
```

## 功能模块

### 1. Agent 推理引擎

**ReAct 自主推理**：状态机模式驱动 Thought→Action→Observation 循环，Agent 根据问题语义自主决策调用哪些工具、调多少次、何时结束，无需预设分析路径。内置 5 个工具：`getQuote` / `getKline` / `getFundFlow` / `getNewsSentiment` / `searchRag`。

**Plan-and-Execute 分层执行**：对复杂问题先规划子任务再分步执行，简单问题自动回退 ReAct。智能模式切换，前端无感知。

**Reflexion 自省审查**：Agent 输出后由独立审查模型校验数据引用准确性与逻辑一致性，不通过则自动修正。

**四层防御机制**：JSON 正则提取 + 参数 Schema 校验 + 上下文截断（500 字）+ 最大 10 轮终止，保障 Agent 稳定运行。

### 2. Agent 工程基础设施

**ToolRegistry 工具注册中心**：统一管理工具注册、入参校验、敏感操作拦截与调用审计。新工具只需实现 `Tool` 接口即可自动注册到 LLM 可用工具列表。

**全链路 Trace 追踪**：记录每轮 Thought→Action→Observation 的 Token 消耗与工具耗时，失败场景自动捕获对话上下文与 LLM 原始响应用于复盘。

**Prompt 配置中心**：所有 Prompt 集中在 `prompts.yml`，与业务代码解耦，支持热更新与 Git 版本管理。

**Harness 安全护栏**：Input Guardrail（注入检测 + 上下文净化）→ Tool Sandbox（参数校验 + 敏感拦截）→ Output Guardrail（Reflexion 自省 + 枚举约束）→ Observability（全链路 Trace + 故障快照），四层闭环管控。

### 3. RAG 知识库（Agentic RAG）

基于 Qdrant 向量库 + 阿里云 Embedding 构建私有知识索引，支持 PDF 自动切片入库。检索能力封装为 Agent 可调用工具，LLM 自主判断何时需要检索文档，而非固定流程触发。

### 4. 实时行情看板

- 7 大指数实时推送（上证、深证、创业板、科创50 等，WebSocket 每秒刷新）
- 市场概况（涨跌家数、涨跌停、成交额）
- A股板块行情（主板/创业板/科创板/北交所）
- 热点行业/概念板块（按成交额排序）
- 个股K线（日/周/月 + 分时图 + MA均线）
- 盘口买卖五档 + 资金流向 + 北向资金
- 龙虎榜数据（净买额、机构解读）
- 异动预警实时推送

### 5. AI 财报解读

基于最近几个季度财务数据，AI 从营收利润、盈利能力、财务健康度、现金流、运营效率五维度进行基本面分析。支持两只股票横向对比。Redis 缓存 1 小时。

### 6. 协同分析流水线

三步固定流水线：新闻舆情分析 → 市场技术分析 → 综合交易建议，输出 BUY/SELL/HOLD 建议 + 置信度 + 风险提示。Agent 输出异常时自动降级为规则引擎兜底。

### 7. 盯盘与条件单

- 自定义价格预警规则，触发时 WebSocket + 飞书双通道推送
- 五种条件单：涨破/跌破目标价、均线金叉/死叉、放量突破
- 交易时段每 10 秒自动扫描，幂等校验防重复触发

### 8. 其他功能

- AI 对话（SSE 流式 + 交易意图识别 + 文档上传问答）
- 会话持久化（刷新页面对话不丢失、历史搜索）
- 飞书消息推送（规则触发、条件单执行时自动推送）
- 暗色模式 / 模拟交易（100 万初始资金）

## 快速启动

### 前置条件

- JDK 17+
- Node.js 18+
- Python 3.10+
- Redis（可选，支持降级）
- Qdrant（可选，仅 RAG 需要）

### 环境变量

```bash
# 必填
export DEEPSEEK_API_KEY=sk-your-key-here

# 可选：阿里云 Embedding（RAG 模块）
export ALIYUN_API_KEY=sk-your-key-here

# 可选：飞书消息推送
export FEISHU_APP_ID=your_app_id
export FEISHU_APP_SECRET=your_app_secret
export FEISHU_TARGET_OPEN_ID=your_open_id
```

飞书配置在[飞书开放平台](https://open.feishu.cn/)创建企业自建应用，开启 `im:message:send_as_bot` 权限即可。

### 启动

**Python 数据服务：**
```bash
cd backend
pip install -r requirements.txt
python data_service.py
```

**后端（Spring Boot）：**
```bash
cd backend
mvn spring-boot:run
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
| 后端 | Spring Boot 3.2, Spring AI, MyBatis-Plus, WebSocket |
| Agent | ReAct 状态机, Plan-and-Execute, Reflexion, ToolRegistry |
| AI | DeepSeek Chat, Qdrant 向量库, 阿里云 Embedding |
| 数据 | Python Flask, AKShare, H2 / MySQL |
| 缓存 | Redis（45min~1h 分级缓存，故障静默降级） |
| 推送 | 飞书开放平台 API |
| 可观测 | 全链路 Trace 追踪, Token 统计, 失败快照 |

## License

MIT
