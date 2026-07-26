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
