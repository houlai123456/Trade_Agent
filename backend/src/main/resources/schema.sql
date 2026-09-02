-- 股票基本信息
CREATE TABLE IF NOT EXISTS stock_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    industry VARCHAR(100),
    exchange VARCHAR(10),
    total_market_cap DECIMAL(20, 2),
    circulating_market_cap DECIMAL(20, 2),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
);

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户 ADMIN-管理员',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-活跃 LOCKED-锁定 DISABLED-禁用',
    api_quota_daily INT DEFAULT 1000 COMMENT '每日API调用限额',
    api_used_today INT DEFAULT 0 COMMENT '今日已使用次数',
    quota_reset_date DATE COMMENT '配额重置日期',
    last_login_time TIMESTAMP COMMENT '最后登录时间',
    last_login_ip VARCHAR(50) COMMENT '最后登录IP',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
);

-- 初始化管理员账号（密码: admin123）
INSERT INTO users (id, username, password_hash, role, api_quota_daily)
VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'ADMIN', 10000)
ON DUPLICATE KEY UPDATE username=username;

-- K线数据
CREATE TABLE IF NOT EXISTS stock_kline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    period VARCHAR(10) NOT NULL COMMENT 'DAY-日K WEEK-周K MONTH-月K',
    open_price DECIMAL(10, 2),
    close_price DECIMAL(10, 2),
    high_price DECIMAL(10, 2),
    low_price DECIMAL(10, 2),
    volume BIGINT,
    amount DECIMAL(20, 2),
    change_percent DECIMAL(10, 4),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code_date_period (code, date, period)
);

-- 用户自选股
CREATE TABLE IF NOT EXISTS user_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(20) NOT NULL,
    remark VARCHAR(200),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_code (user_id, code)
);

-- 新闻数据
CREATE TABLE IF NOT EXISTS news (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    content TEXT,
    source VARCHAR(100),
    url VARCHAR(1000),
    stock_code VARCHAR(20),
    stock_name VARCHAR(100),
    sentiment VARCHAR(20) COMMENT 'POSITIVE-利好 NEGATIVE-利空 NEUTRAL-中性',
    sentiment_score DOUBLE,
    affected_stocks VARCHAR(1000) COMMENT '影响的股票或板块（JSON数组）',
    publish_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stock_code (stock_code),
    INDEX idx_publish_time (publish_time)
);

-- 虚拟账户
CREATE TABLE IF NOT EXISTS trade_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    total_assets DECIMAL(20, 2) DEFAULT 0 COMMENT '总资产',
    available_balance DECIMAL(20, 2) DEFAULT 0 COMMENT '可用资金',
    frozen_balance DECIMAL(20, 2) DEFAULT 0 COMMENT '冻结资金',
    market_value DECIMAL(20, 2) DEFAULT 0 COMMENT '持仓市值',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id)
);

-- 持仓记录
CREATE TABLE IF NOT EXISTS trade_position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100),
    quantity INT NOT NULL DEFAULT 0 COMMENT '持有数量',
    available_quantity INT NOT NULL DEFAULT 0 COMMENT '可用数量',
    cost_price DECIMAL(10, 4) COMMENT '成本均价',
    total_cost DECIMAL(20, 4) COMMENT '总成本',
    current_price DECIMAL(10, 4) COMMENT '当前价格',
    market_value DECIMAL(20, 2) COMMENT '最新市值',
    profit_loss DECIMAL(20, 2) COMMENT '浮动盈亏',
    pl_ratio DECIMAL(10, 4) COMMENT '盈亏百分比',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tp_user_code (user_id, code)
);

-- 交易流水
CREATE TABLE IF NOT EXISTS trade_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100),
    trade_type VARCHAR(10) NOT NULL COMMENT 'BUY-买入 SELL-卖出',
    price DECIMAL(10, 4) NOT NULL COMMENT '成交价格',
    quantity INT NOT NULL COMMENT '成交数量',
    amount DECIMAL(20, 2) NOT NULL COMMENT '成交金额',
    profit_loss DECIMAL(20, 2) COMMENT '盈亏（卖出时计算）',
    status VARCHAR(10) DEFAULT 'DONE' COMMENT '状态',
    trade_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_code (code),
    INDEX idx_trade_time (trade_time)
);

-- 预警记录
CREATE TABLE IF NOT EXISTS alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100),
    alert_type VARCHAR(20) COMMENT 'PRICE-涨跌幅异动 VOLUME-成交量异动',
    description VARCHAR(500),
    current_price DECIMAL(10, 2),
    change_percent DECIMAL(10, 4),
    volume BIGINT,
    read_flag INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_create_time (create_time)
);

-- 盯盘规则
CREATE TABLE IF NOT EXISTS watch_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100),
    condition_type VARCHAR(10) NOT NULL COMMENT 'ABOVE-高于 BELOW-低于',
    target_price DECIMAL(10, 2) NOT NULL,
    enabled INT DEFAULT 1,
    last_triggered_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_code (code)
);

-- 条件单
CREATE TABLE IF NOT EXISTS condition_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100),
    direction VARCHAR(10) NOT NULL COMMENT 'BUY-买入 SELL-卖出',
    condition_type VARCHAR(10) NOT NULL COMMENT 'ABOVE-高于 BELOW-低于',
    trigger_price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    order_price DECIMAL(10, 2) COMMENT '限价(null=市价)',
    status VARCHAR(20) DEFAULT 'PENDING',
    triggered_order_id BIGINT,
    trigger_time TIMESTAMP,
    expire_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_code (code),
    INDEX idx_status (status)
);

-- 聊天会话
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    title VARCHAR(200),
    stock_code VARCHAR(20),
    message_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_update_time (update_time)
);

-- 聊天消息
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content CLOB NOT NULL,
    message_type VARCHAR(30),
    token_count INT DEFAULT 0,
    metadata_json VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cm_session_id ON chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_cm_create_time ON chat_message(create_time);

-- Agent投资建议记录
CREATE TABLE IF NOT EXISTS agent_advice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL COMMENT '股票代码',
    stock_name VARCHAR(100) NOT NULL COMMENT '股票名称',
    advice_type VARCHAR(20) NOT NULL COMMENT '建议类型：BUY-买入 SELL-卖出 HOLD-观望',
    confidence_score DECIMAL(5, 2) COMMENT '置信度分数(0-100)',

    -- 价格相关
    advice_price DECIMAL(10, 2) NOT NULL COMMENT '建议时的价格',
    target_price DECIMAL(10, 2) COMMENT '目标价',
    stop_loss_price DECIMAL(10, 2) COMMENT '止损价',
    expected_return DECIMAL(10, 4) COMMENT '预期收益率(%)',

    -- 分析维度
    fundamental_score DECIMAL(5, 2) COMMENT '基本面得分(0-100)',
    technical_score DECIMAL(5, 2) COMMENT '技术面得分(0-100)',
    sentiment_score DECIMAL(5, 2) COMMENT '情绪面得分(0-100)',

    -- 风险条件（JSON格式）
    risk_conditions TEXT COMMENT '风险条件JSON: [{type:"MA_BREAK", threshold:2450, description:"跌破MA20"}]',

    -- 状态跟踪
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE-活跃 TRIGGERED-风险触发 EXPIRED-已过期 CLOSED-已关闭',
    triggered_condition VARCHAR(500) COMMENT '触发的风险条件描述',
    trigger_time TIMESTAMP COMMENT '风险触发时间',

    -- 回测数据
    review_7d_price DECIMAL(10, 2) COMMENT '7天后价格',
    review_7d_return DECIMAL(10, 4) COMMENT '7天实际收益率(%)',
    review_7d_correct BOOLEAN COMMENT '7天建议是否正确',
    review_30d_price DECIMAL(10, 2) COMMENT '30天后价格',
    review_30d_return DECIMAL(10, 4) COMMENT '30天实际收益率(%)',
    review_30d_correct BOOLEAN COMMENT '30天建议是否正确',

    -- 完整分析报告
    full_report TEXT COMMENT '完整分析报告',
    agent_version VARCHAR(50) COMMENT 'Agent版本',

    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_stock_code (stock_code),
    INDEX idx_advice_type (advice_type),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
);

-- 风险监控日志
CREATE TABLE IF NOT EXISTS risk_monitor_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    advice_id BIGINT NOT NULL COMMENT '关联的建议ID',
    stock_code VARCHAR(20) NOT NULL,
    check_date DATE NOT NULL COMMENT '检查日期',

    -- 当日市场数据
    current_price DECIMAL(10, 2) COMMENT '当前价格',
    ma5 DECIMAL(10, 2) COMMENT 'MA5均线',
    ma20 DECIMAL(10, 2) COMMENT 'MA20均线',
    volume BIGINT COMMENT '成交量',

    -- 风险检查结果
    risk_triggered BOOLEAN DEFAULT FALSE COMMENT '是否触发风险',
    triggered_rules TEXT COMMENT '触发的规则列表',
    risk_level VARCHAR(20) COMMENT '风险等级：LOW-低 MEDIUM-中 HIGH-高 CRITICAL-严重',

    -- 通知状态
    alert_sent BOOLEAN DEFAULT FALSE COMMENT '是否已发送告警',
    alert_channel VARCHAR(50) COMMENT '告警渠道：FEISHU-飞书',

    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_advice_id (advice_id),
    INDEX idx_stock_code (stock_code),
    INDEX idx_check_date (check_date),
    INDEX idx_risk_triggered (risk_triggered),
    FOREIGN KEY (advice_id) REFERENCES agent_advice(id) ON DELETE CASCADE
);

-- Agent建议追踪表（用于回测和自适应权重）
CREATE TABLE IF NOT EXISTS suggestion_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL COMMENT '股票代码',
    stock_name VARCHAR(100) NOT NULL COMMENT '股票名称',
    suggestion VARCHAR(10) NOT NULL COMMENT '建议类型：BUY/SELL/HOLD',
    confidence VARCHAR(20) NOT NULL COMMENT '置信度：HIGH/MEDIUM/LOW',

    -- 建议时的状态
    suggested_at TIMESTAMP NOT NULL COMMENT '建议时间',
    suggested_price DECIMAL(10, 2) NOT NULL COMMENT '建议时价格',
    target_price DECIMAL(10, 2) COMMENT '目标价格',

    -- 维度评分（用于自适应权重）
    weighted_score INT NOT NULL COMMENT '加权评分(0-100)',
    fundamental_score INT COMMENT '基本面评分(0-100)',
    technical_score INT COMMENT '技术面评分(0-100)',
    sentiment_score INT COMMENT '情绪面评分(0-100)',
    risk_score INT COMMENT '风险评分(0-100)',

    -- 风险干预记录
    risk_override BOOLEAN DEFAULT FALSE COMMENT '是否发生风险干预',
    original_suggestion VARCHAR(10) COMMENT '风险干预前的原始建议',

    -- 回测字段
    actual_price_7d DECIMAL(10, 2) COMMENT '7天后实际价格',
    actual_price_30d DECIMAL(10, 2) COMMENT '30天后实际价格',
    return_7d DECIMAL(10, 4) COMMENT '7天收益率(%)',
    return_30d DECIMAL(10, 4) COMMENT '30天收益率(%)',
    accuracy_7d BOOLEAN COMMENT '7天建议是否准确',
    accuracy_30d BOOLEAN COMMENT '30天建议是否准确',

    -- 回测状态
    backtest_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING-待回测 PARTIAL-7天完成 COMPLETED-全部完成',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_stock_code (stock_code),
    INDEX idx_suggested_at (suggested_at),
    INDEX idx_backtest_status (backtest_status),
    INDEX idx_suggestion (suggestion)
);
