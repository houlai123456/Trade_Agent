-- 财务指标历史数据表
CREATE TABLE IF NOT EXISTS stock_fundamental (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL COMMENT '股票代码',
    report_date DATE NOT NULL COMMENT '报告期',
    trade_date DATE NOT NULL COMMENT '交易日（用于计算分位数）',

    -- 估值指标
    pe_ratio DECIMAL(10, 2) COMMENT '市盈率(PE)',
    pb_ratio DECIMAL(10, 2) COMMENT '市净率(PB)',
    ps_ratio DECIMAL(10, 2) COMMENT '市销率(PS)',
    pcf_ratio DECIMAL(10, 2) COMMENT '市现率(PCF)',

    -- 盈利能力
    roe DECIMAL(10, 4) COMMENT '净资产收益率(%)',
    roa DECIMAL(10, 4) COMMENT '总资产收益率(%)',
    gross_margin DECIMAL(10, 4) COMMENT '毛利率(%)',
    net_margin DECIMAL(10, 4) COMMENT '净利率(%)',

    -- 成长性
    revenue_yoy DECIMAL(10, 4) COMMENT '营收同比增长率(%)',
    profit_yoy DECIMAL(10, 4) COMMENT '净利润同比增长率(%)',

    -- 财务安全
    debt_ratio DECIMAL(10, 4) COMMENT '资产负债率(%)',
    current_ratio DECIMAL(10, 4) COMMENT '流动比率',
    quick_ratio DECIMAL(10, 4) COMMENT '速动比率',

    -- 营收和利润（亿元）
    total_revenue DECIMAL(20, 2) COMMENT '营业总收入(亿元)',
    net_profit DECIMAL(20, 2) COMMENT '净利润(亿元)',

    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_code_date (stock_code, trade_date),
    INDEX idx_report_date (report_date),
    INDEX idx_trade_date (trade_date),
    INDEX idx_pe_ratio (pe_ratio),
    INDEX idx_roe (roe)
);

-- 行业估值统计表（每日更新）
CREATE TABLE IF NOT EXISTS industry_valuation_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    industry VARCHAR(100) NOT NULL COMMENT '行业名称',
    trade_date DATE NOT NULL COMMENT '交易日',

    -- PE统计
    pe_median DECIMAL(10, 2) COMMENT 'PE中位数',
    pe_mean DECIMAL(10, 2) COMMENT 'PE平均值',
    pe_p25 DECIMAL(10, 2) COMMENT 'PE 25%分位数',
    pe_p75 DECIMAL(10, 2) COMMENT 'PE 75%分位数',

    -- PB统计
    pb_median DECIMAL(10, 2) COMMENT 'PB中位数',
    pb_mean DECIMAL(10, 2) COMMENT 'PB平均值',

    -- ROE统计
    roe_median DECIMAL(10, 4) COMMENT 'ROE中位数',
    roe_mean DECIMAL(10, 4) COMMENT 'ROE平均值',
    roe_p75 DECIMAL(10, 4) COMMENT 'ROE 75%分位数（行业优秀水平）',

    -- 成长性统计
    revenue_yoy_median DECIMAL(10, 4) COMMENT '营收增速中位数',
    profit_yoy_median DECIMAL(10, 4) COMMENT '净利润增速中位数',

    stock_count INT COMMENT '样本股票数量',

    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_industry_date (industry, trade_date),
    INDEX idx_trade_date (trade_date)
);
