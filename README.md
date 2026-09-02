# Trading Helper — 股票智能分析助手

基于 **Spring AI + DeepSeek + Vue 3 + Python** 的股票分析平台。针对个人投资者难以综合多维度信息做出理性决策的痛点，构建**维度型 Agent 协作架构**，通过基本面、技术面、情绪面、风险四维度并行分析 + 加权投票决策，配合实时行情推送、飞书消息触达，提供全链路投资决策支持。

## 架构

```
┌──────────┐   ┌───────────────────────────────────┐   ┌─────────────────────┐
│ 前端     │   │ 后端 (Spring Boot 3.2)            │   │ Python 数据层        │
│ Vue 3    │   │                                   │   │ Flask + Multi-Source│
│ Element+ │   │ ┌───────────────────────────────┐ │   │ AKShare (主源)      │
│ ECharts  │←─→│ │ 维度型 Agent 协作架构          │ │←─→│ Ashare (备源)       │
│          │   │ │                               │ │   │ 行情/K线/资金流向   │
└──────────┘   │ │ ┌─────────────────────────┐   │ │   │ 新闻/财报/龙虎榜    │
               │ │ │ 基本面 Agent            │   │ │   └─────────────────────┘
               │ │ │ 技术面 Agent            │   │ │
               │ │ │ 情绪面 Agent            │   │ │   ┌─────────────────────┐
               │ │ │ 风险评估 Agent          │   │ │   │ 外部服务              │
               │ │ └─────────────────────────┘   │ │   │ DeepSeek LLM         │
               │ │           ↓                   │ │   │ Qdrant 向量库        │
               │ │ ┌─────────────────────────┐   │ │   │ Redis 缓存           │
               │ │ │ 投资顾问 Agent          │   │ │   │ MySQL 数据库         │
               │ │ │ • 置信度传播            │   │ │──→│ 飞书消息推送          │
               │ │ │ • 自适应权重            │   │ │   │ Prometheus 监控      │
               │ │ │ • 加权投票决策          │   │ │   └─────────────────────┘
               │ │ │ • 风险一票否决          │   │ │
               │ │ └─────────────────────────┘   │ │
               │ └───────────────────────────────┘ │
               │ ┌───────────────────────────────┐ │
               │ │ Agent 工程基座                 │ │
               │ │ ToolRegistry (15+ 工具)       │ │
               │ │ 全链路 Trace + Token 监控      │ │
               │ │ Harness 四层安全护栏           │ │
               │ │ Resilience4j 熔断降级          │ │
               │ │ Redisson 分布式锁              │ │
               │ └───────────────────────────────┘ │
               │ 盯盘/条件单/回测引擎/模拟交易      │
               └───────────────────────────────────┘
```

## 核心亮点

### 1. 维度型多 Agent 协作架构

设计四维度专家 Agent（基本面、技术面、情绪面、风险）并行分析。核心创新：

- **置信度传播机制**：数据质量影响决策权重，LOW 置信度降权 40%，HIGH 增权 20%
- **自适应权重系统**：基于历史准确率动态调整维度占比，系统会从历史中学习
- **风险一票否决**：极端风险直接覆盖其他建议（如财务造假、退市风险）
- **关键假设输出**：每维度输出 3-4 条关键假设（如"假设 ROE 保持 30% 以上"），让用户理解决策链路
- **回测引擎**：支持参数扫描、夏普比率、最大回撤等 10 项指标，实现闭环验证

**工作流程**：
```
用户输入股票代码
    ↓
四维度 Agent 并行分析（各自获取数据 + 评估数据质量 + 输出置信度）
    ↓
投资顾问 Agent 收集结果
    ↓
置信度传播（根据数据质量调整权重）
    ↓
自适应权重（根据历史准确率调整）
    ↓
加权投票 + 风险一票否决
    ↓
输出最终建议（BUY/SELL/HOLD + 置信度 + 关键假设 + 风险提示）
```

### 2. Agent 工程基础设施

- **ToolRegistry 工具注册中心**：统一管理 15 个工具（行情查询、财务数据、RAG 检索、技术指标计算等），JSON Schema 校验、敏感操作拦截
- **全链路 LLM 调用追踪**：记录每轮交互的 Token 消耗与工具耗时，失败场景自动捕获上下文快照
- **Prompt 配置中心**：Prompt 与业务代码解耦，支持热更新与版本管理
- **熔断降级**：Resilience4j 熔断器，LLM 调用失败自动降级到规则引擎兜底
- **指数退避重试**：瞬态网络故障自动恢复

### 3. Harness Engineering 全链路安全护栏

从注入检测、上下文净化 → 参数校验、敏感拦截 → Reflexion 自省、枚举约束 → 全链路 Trace、故障快照四个层面构建 LLM 工程化管控闭环，解决裸调 API 不可控、难追溯的工程痛点。

### 4. 多源知识增强搜索（RAG）

基于 Qdrant 向量库、阿里云 Embedding 构建私有知识索引，采用 PDFBox 按页解析、字号分析识别标题层级，实现语义边界切片、页码元数据绑定。将检索封装为 Agent 工具链（向量粗筛 → 回读原文 → 抽取表格），配合 Reflexion 审查机制验证 LLM 输出与原文一致性，实现可溯源的主动式知识增强。

### 5. 生产级可靠性工程

- **多数据源容灾**：AKShare（主源）+ Ashare（新浪、腾讯双核心备源）自动故障切换，统计成功率
- **分布式锁**：Redisson 防止定时任务并发
- **幂等性保护**：防重复提交
- **缓存优化**：布隆过滤器、随机 TTL 防雪崩
- **数据库**：H2 → MySQL 迁移（1886 条 K 线完整迁移）
- **可观测性**：Prometheus + Actuator 监控 LLM Token 消耗、接口性能

## 功能模块

### 1. 实时行情看板

- 7 大指数实时推送（上证、深证、创业板、科创50 等，WebSocket 每秒刷新）
- 市场概况（涨跌家数、涨跌停、成交额）
- A股板块行情（主板/创业板/科创板/北交所）
- 热点行业/概念板块（按成交额排序）
- 个股K线（日/周/月 + 分时图 + MA均线）
- 盘口买卖五档 + 资金流向 + 北向资金
- 龙虎榜数据（净买额、机构解读）
- 异动预警实时推送

### 2. 智能分析与决策

- **维度型分析**：基本面、技术面、情绪面、风险四维度并行分析
- **置信度传播**：数据质量影响最终决策权重
- **自适应权重**：根据历史准确率动态调整
- **关键假设**：每维度输出 3-4 条关键假设，决策链路可解释
- **风险一票否决**：极端风险直接覆盖其他建议
- **输出格式**：BUY/SELL/HOLD + 置信度 + 目标价位 + 风险提示

### 3. AI 财报解读

基于最近几个季度财务数据，AI 从营收利润、盈利能力、财务健康度、现金流、运营效率五维度进行基本面分析。支持两只股票横向对比。集成 TA4j 专业技术指标库，行业对比、估值分位数分析。Redis 缓存 1 小时。

### 4. 回测引擎

- **参数扫描**：批量测试不同权重组合
- **性能指标**：夏普比率、最大回撤、胜率、盈亏比等 10 项指标
- **历史验证**：验证策略在历史数据上的表现
- **权重优化**：找出最优的维度权重配置

### 5. 盯盘与条件单

- 自定义价格预警规则，触发时 WebSocket + 飞书双通道推送
- 五种条件单：涨破/跌破目标价、均线金叉/死叉、放量突破
- 交易时段每 10 秒自动扫描，幂等校验防重复触发

### 6. 其他功能

- AI 对话（SSE 流式 + 交易意图识别 + 文档上传问答）
- 会话持久化（刷新页面对话不丢失、历史搜索）
- 飞书消息推送（规则触发、条件单执行时自动推送）
- 暗色模式 / 模拟交易（100 万初始资金）
- RAG 知识库（PDF 上传、语义检索、可溯源）

## 快速启动

### 前置条件

- JDK 17+
- Node.js 18+
- Python 3.10+
- MySQL 8.0+（生产环境）
- Redis（可选，支持降级）
- Qdrant（可选，仅 RAG 需要）

### 环境变量

复制 `.env.example` 为 `.env` 并填入真实配置：

```bash
# DeepSeek API（必填）
DEEPSEEK_API_KEY=sk-your-deepseek-key-here

# 阿里云 Embedding（可选，RAG 模块需要）
ALIYUN_API_KEY=sk-your-aliyun-key-here

# 飞书消息推送（可选）
FEISHU_APP_ID=cli_your_app_id
FEISHU_APP_SECRET=your_app_secret
FEISHU_TARGET_OPEN_ID=ou_your_open_id

# MySQL（必填）
MYSQL_USERNAME=trade_user
MYSQL_PASSWORD=your_password
```

飞书配置在[飞书开放平台](https://open.feishu.cn/)创建企业自建应用，开启 `im:message:send_as_bot` 权限。

### 数据库初始化

```bash
# 创建数据库和用户
mysql -u root -p
CREATE DATABASE trading_helper CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'trade_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON trading_helper.* TO 'trade_user'@'localhost';
FLUSH PRIVILEGES;

# 导入表结构
mysql -u trade_user -p trading_helper < backend/src/main/resources/schema.sql
```

### 启动服务

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
| Agent | 维度型协作架构, 置信度传播, 自适应权重, ToolRegistry |
| AI | DeepSeek Chat, Qdrant 向量库, 阿里云 Embedding |
| 数据 | Python Flask, AKShare（主源）, Ashare（备源）|
| 数据库 | MySQL 8.0, Redis（分级缓存，故障静默降级）|
| 可靠性 | Redisson 分布式锁, Resilience4j 熔断器, 幂等性保护 |
| 推送 | 飞书开放平台 API, WebSocket 实时推送 |
| 可观测 | Prometheus + Actuator, 全链路 Trace, Token 监控 |

## 项目亮点

1. **维度型多 Agent 协作架构**：置信度传播 + 自适应权重 + 回测验证，系统会从历史中学习
2. **Agent 工程基础设施**：ToolRegistry + 全链路追踪 + Prompt 配置中心 + 熔断降级
3. **Harness Engineering**：四层安全护栏，解决裸调 LLM API 不可控的工程痛点
4. **多源知识增强搜索**：语义切片 + 可溯源 + Reflexion 审查，主动式知识增强
5. **生产级可靠性**：多数据源容灾 + 分布式锁 + 幂等性保护 + 缓存优化

## License

MIT
