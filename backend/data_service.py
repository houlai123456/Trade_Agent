"""
AKShare 数据服务
为Java后端提供股票数据API，端口5000
安装依赖：pip install flask akshare pandas flask-cors
启动方式：python data_service.py
"""
import json
import time
import logging
import threading
import concurrent.futures
from datetime import datetime
from functools import wraps

import akshare as ak
import pandas as pd
import requests
from flask import Flask, jsonify, request
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# ==================== RAG 服务 ====================
from rag_service import RAGService, DEEPSEEK_KEY, DEEPSEEK_MODEL
try:
    rag = RAGService()
    logger.info("RAG服务初始化成功")
except Exception as e:
    rag = None
    logger.warning(f"RAG服务初始化失败（不影响行情数据）: {e}")

# ==================== 请求计时中间件 ====================
@app.before_request
def log_request_start():
    request._start_time = time.time()

@app.after_request
def log_request_end(response):
    if hasattr(request, '_start_time'):
        elapsed = time.time() - request._start_time
        if elapsed > 1:
            logger.info(f"[HTTP] {request.method} {request.path} 耗时 {elapsed:.1f}s")
    return response

# ==================== 频率控制 ====================
last_request_time = 0
MIN_INTERVAL = 1.0  # AKShare要求每次请求间隔至少1秒

# ==================== 数据缓存 ====================
_cache = {"stock_list": None, "stock_list_time": 0}
_index_kline_cache = {}  # code -> {"data": df, "time": timestamp}
CACHE_TTL = 3600  # 缓存1小时（股票列表改动极少）
_cache_lock = threading.Lock()  # 防止并发加载全量行情


def get_stock_list():
    """获取并缓存全量股票列表，避免重复请求AKShare"""
    now = time.time()
    if _cache["stock_list"] is not None and now - _cache["stock_list_time"] < CACHE_TTL:
        return _cache["stock_list"]
    with _cache_lock:
        # 双重检查：获取锁后缓存可能已被其他线程写入
        if _cache["stock_list"] is not None and now - _cache["stock_list_time"] < CACHE_TTL:
            return _cache["stock_list"]
        df = akshare_call(ak.stock_zh_a_spot)
        _cache["stock_list"] = df
        _cache["stock_list_time"] = time.time()
        return df


def rate_limit(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        global last_request_time
        now = time.time()
        wait = MIN_INTERVAL - (now - last_request_time)
        if wait > 0:
            logger.info(f"[rate_limit] {func.__name__} 等待 {wait:.1f}s")
            time.sleep(wait)
        try:
            result = func(*args, **kwargs)
            last_request_time = time.time()
            return result
        except Exception as e:
            logger.error(f"AKShare调用失败: {e}")
            return jsonify({"error": str(e), "success": False}), 500
    return wrapper


# ==================== 工具函数 ====================

def akshare_call(func, *args, max_retries=3, **kwargs):
    """调用AKShare接口，自动重试"""
    for attempt in range(max_retries):
        try:
            return func(*args, **kwargs)
        except (requests.exceptions.ConnectionError, ConnectionError) as e:
            logger.warning(f"AKShare连接失败 (尝试 {attempt+1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                wait = 2 ** attempt
                time.sleep(wait)
            else:
                raise


def call_with_timeout(func, timeout, *args, **kwargs):
    """用线程池执行函数，超时则抛出 TimeoutError"""
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        future = pool.submit(func, *args, **kwargs)
        try:
            return future.result(timeout=timeout)
        except concurrent.futures.TimeoutError:
            raise TimeoutError(f"调用超时 ({timeout}s)")


def is_trading_time():
    """是否为A股交易时间（周一到周五，9:30-11:30或13:00-15:00）"""
    now = datetime.now()
    if now.weekday() >= 5:  # 周六日
        return False
    h, m = now.hour, now.minute
    t = h * 60 + m
    return (t >= 9*60+30 and t < 11*60+30) or (t >= 13*60 and t < 15*60)


def strip_exchange(code):
    """去掉交易所前缀，如 sh600519 -> 600519, bj920000 -> 920000"""
    return (code.replace("sh", "").replace("sz", "").replace("bj", "")
                .replace("SH", "").replace("SZ", "").replace("BJ", ""))


def add_exchange(code):
    """纯数字代码加前缀，如 600519 -> sh600519"""
    code = str(code).strip()
    if code.startswith("sh") or code.startswith("sz"):
        return code
    if code.startswith("6"):
        return f"sh{code}"
    return f"sz{code}"


def safe_json(df):
    """DataFrame转JSON，处理NaN等特殊值"""
    if df is None or df.empty:
        return []
    return df.fillna("").to_dict(orient="records")


# ==================== 股票列表 ====================

@app.route("/api/stock/list", methods=["GET"])
@rate_limit
def stock_list():
    """获取A股全部股票列表
    返回：代码、名称、行业、总市值、流通市值
    """
    df = get_stock_list()
    result = []
    for _, row in df.iterrows():
        result.append({
            "code": strip_exchange(row.get("代码", "")),
            "name": row.get("名称", ""),
        })
    return jsonify({"success": True, "data": result, "total": len(result)})


@app.route("/api/stock/search", methods=["GET"])
def stock_search():
    """搜索股票（无rate_limit，使用缓存数据）
    参数：keyword=茅台
    返回：按代码和名称模糊匹配的股票列表
    """
    keyword = request.args.get("keyword", "").strip()
    if not keyword:
        return jsonify({"success": True, "data": []})

    df = get_stock_list()
    results = []
    for _, row in df.iterrows():
        raw_code = str(row.get("代码", ""))
        name = str(row.get("名称", ""))
        clean_code = strip_exchange(raw_code)
        if keyword in raw_code or keyword in clean_code or keyword in name:
            results.append({
                "code": clean_code,
                "name": name,
            })
            if len(results) >= 50:
                break

    return jsonify({"success": True, "data": results})


# ==================== 实时行情 ====================

@app.route("/api/stock/quote", methods=["GET"])
@rate_limit
def stock_quote():
    """获取实时行情
    参数：codes=sh600519,sz000001（可选，不传则返回全部）
    返回：当前价、涨跌幅、成交量、成交额、最高/最低等
    """
    codes_param = request.args.get("codes", "")
    codes = [c.strip() for c in codes_param.split(",") if c.strip()] if codes_param else []

    df = get_stock_list()
    if df.empty:
        return jsonify({"success": False, "error": "未获取到数据", "data": []})

    results = []
    for _, row in df.iterrows():
        raw_code = str(row.get("代码", ""))
        code = strip_exchange(raw_code)

        if codes and raw_code not in codes and code not in codes:
            continue

        item = {
            "code": code,
            "name": row.get("名称", ""),
            "current_price": safe_float(row.get("最新价")),
            "open_price": safe_float(row.get("今开")),
            "yesterday_close": safe_float(row.get("昨收")),
            "high_price": safe_float(row.get("最高")),
            "low_price": safe_float(row.get("最低")),
            "volume": safe_int(row.get("成交量")),
            "amount": safe_float(row.get("成交额")),
            "change_percent": safe_float(row.get("涨跌幅")),
            "change_amount": safe_float(row.get("涨跌额")),
        }
        results.append(item)

    return jsonify({"success": True, "data": results})


# ==================== 指数行情 ====================

@app.route("/api/index/quote", methods=["GET"])
@rate_limit
def index_quote():
    """获取主要指数行情
    返回：上证指数、深证成指、创业板指、科创50、沪深300等
    """
    try:
        df = akshare_call(ak.stock_zh_index_spot_sina)
    except Exception as e:
        logger.error(f"获取指数失败: {e}")
        return jsonify({"success": False, "error": str(e), "data": []})

    if df.empty:
        return jsonify({"success": False, "data": []})

    # 关注的指数代码
    target_codes = {
        "sh000001": "上证指数", "sz399001": "深证成指",
        "sz399006": "创业板指", "sh000688": "科创50",
        "sh000300": "沪深300", "sh000016": "上证50", "sh000905": "中证500",
    }

    results = []
    for _, row in df.iterrows():
        code = str(row.get("代码", ""))
        if code not in target_codes:
            continue
        results.append({
            "code": code,
            "name": target_codes[code],
            "current_price": safe_float(row.get("最新价")),
            "change_percent": safe_float(row.get("涨跌幅")),
            "change_amount": safe_float(row.get("涨跌额")),
            "volume": safe_float(row.get("成交量")),
            "amount": safe_float(row.get("成交额")),
            "open_price": safe_float(row.get("今开")),
            "yesterday_close": safe_float(row.get("昨收")),
            "high_price": safe_float(row.get("最高")),
            "low_price": safe_float(row.get("最低")),
        })

    return jsonify({"success": True, "data": results})


@app.route("/api/index/quote/<code>", methods=["GET"])
@rate_limit
def index_quote_single(code):
    """获取单个指数行情"""
    try:
        df = akshare_call(ak.stock_zh_index_spot_sina)
    except Exception as e:
        logger.error(f"获取指数失败: {e}")
        return jsonify({"success": False, "error": str(e)})

    code = strip_exchange(code)
    target = {c: n for c, n in [
        ("sh000001", "上证指数"), ("sz399001", "深证成指"),
        ("sz399006", "创业板指"), ("sh000688", "科创50"),
        ("sh000300", "沪深300"), ("sh000016", "上证50"),
        ("sh000905", "中证500"),
    ]}

    for _, row in df.iterrows():
        raw_code = str(row.get("代码", ""))
        if strip_exchange(raw_code) != code:
            continue
        return jsonify({"success": True, "data": {
            "code": raw_code,
            "name": target.get(raw_code, row.get("名称", "")),
            "current_price": safe_float(row.get("最新价")),
            "change_percent": safe_float(row.get("涨跌幅")),
            "change_amount": safe_float(row.get("涨跌额")),
            "volume": safe_float(row.get("成交量")),
            "amount": safe_float(row.get("成交额")),
            "open_price": safe_float(row.get("今开")),
            "yesterday_close": safe_float(row.get("昨收")),
            "high_price": safe_float(row.get("最高")),
            "low_price": safe_float(row.get("最低")),
        }})
    return jsonify({"success": False, "error": "未找到该指数"}), 404


@app.route("/api/index/kline/<code>", methods=["GET"])
@rate_limit
def index_kline(code):
    """获取指数历史K线"""
    period = request.args.get("period", "daily")
    limit = int(request.args.get("limit", 200))

    now = time.time()
    cached = _index_kline_cache.get(code)
    if cached and now - cached["time"] < CACHE_TTL:
        df = cached["data"]
    else:
        try:
            df = akshare_call(ak.stock_zh_index_daily, symbol=code)
        except Exception as e:
            logger.error(f"获取指数K线失败 code={code}: {e}")
            return jsonify({"success": False, "error": str(e), "data": []})
        _index_kline_cache[code] = {"data": df, "time": now}

    if df.empty:
        return jsonify({"success": True, "data": []})

    # Sina 数据没有 amount 列，补一个避免聚合报错
    if "amount" not in df.columns:
        df["amount"] = 0

    # 聚合成周K/月K
    if period != "daily":
        df = _aggregate_kline(df, period)

    df = df.tail(limit)
    results = []
    for _, row in df.iterrows():
        results.append({
            "date": str(row.get("date", ""))[:10],
            "open": safe_float(row.get("open")),
            "close": safe_float(row.get("close")),
            "high": safe_float(row.get("high")),
            "low": safe_float(row.get("low")),
            "volume": safe_int(row.get("volume")),
            "amount": safe_float(row.get("amount")),
        })

    return jsonify({"success": True, "data": results})


# ==================== 市场概况 ====================

@app.route("/api/market/overview", methods=["GET"])
@rate_limit
def market_overview():
    """获取A股市场概况
    返回：上涨/下跌/平盘家数、涨停/跌停家数、总成交额
    """
    try:
        df = get_stock_list()
    except Exception as e:
        logger.error(f"获取市场概况失败: {e}")
        return jsonify({"success": False, "error": str(e), "data": {}})

    if df.empty:
        return jsonify({"success": False, "data": {}})

    total = len(df)
    up_count = 0
    down_count = 0
    flat_count = 0
    limit_up = 0
    limit_down = 0
    total_amount = 0.0

    for _, row in df.iterrows():
        change = safe_float(row.get("涨跌幅"))
        amount = safe_float(row.get("成交额")) or 0
        total_amount += amount

        if change is None:
            flat_count += 1
        elif change > 0:
            up_count += 1
            if change >= 9.8:
                limit_up += 1
        elif change < 0:
            down_count += 1
            if change <= -9.8:
                limit_down += 1
        else:
            flat_count += 1

    return jsonify({
        "success": True,
        "data": {
            "total": total,
            "up_count": up_count,
            "down_count": down_count,
            "flat_count": flat_count,
            "limit_up": limit_up,
            "limit_down": limit_down,
            "total_amount": round(total_amount, 2),
        }
    })


# ==================== K线数据 ====================

def _aggregate_kline(df, period):
    """将日K数据聚合为周K或月K，日期取周期内最后一个交易日
    df: 日K DataFrame (需含 date/open/close/high/low/volume/amount)
    period: 'weekly' 或 'monthly'
    """
    df = df.copy()
    df["date"] = pd.to_datetime(df["date"])

    if period == "weekly":
        # ISO 周作为分组键（仅用于排序分组，日期用每周最后一天）
        df["group"] = df["date"].dt.isocalendar().year.astype(str) + "-W" + df["date"].dt.isocalendar().week.astype(str).str.zfill(2)
    else:
        df["group"] = df["date"].dt.to_period("M").astype(str)

    agg = df.groupby("group", sort=True).agg(
        open=("open", "first"),
        close=("close", "last"),
        high=("high", "max"),
        low=("low", "min"),
        volume=("volume", "sum"),
        amount=("amount", "sum"),
        date=("date", "last"),  # 取周期内最后一个交易日
    ).reset_index(drop=True)

    agg["date"] = agg["date"].dt.strftime("%Y-%m-%d")
    return agg


@app.route("/api/stock/kline", methods=["GET"])
@rate_limit
def stock_kline():
    """获取历史K线
    参数：code=600519 period=daily|weekly|monthly limit=100
    """
    code = request.args.get("code", "")
    period = request.args.get("period", "daily")
    limit = int(request.args.get("limit", 200))

    if not code:
        return jsonify({"success": False, "error": "缺少code参数"}), 400

    raw_code = strip_exchange(code)

    period_map = {"daily": "daily", "weekly": "weekly", "monthly": "monthly"}
    akshare_period = period_map.get(period, "daily")

    try:
        # 统一用 stock_zh_a_daily 获取日K数据（Sina源，稳定可靠）
        # 周K/月K通过对日K聚合实现
        df = akshare_call(ak.stock_zh_a_daily, symbol=add_exchange(raw_code), adjust="qfq")
    except Exception as e:
        logger.error(f"获取K线失败 code={code}: {e}")
        return jsonify({"success": False, "error": str(e), "data": []})

    if df.empty:
        return jsonify({"success": True, "data": []})

    if akshare_period != "daily":
        df = _aggregate_kline(df, akshare_period)

    df = df.tail(limit)
    results = []
    for _, row in df.iterrows():
        results.append({
            "date": str(row.get("date", ""))[:10],
            "open": safe_float(row.get("open")),
            "close": safe_float(row.get("close")),
            "high": safe_float(row.get("high")),
            "low": safe_float(row.get("low")),
            "volume": safe_int(row.get("volume")),
            "amount": safe_float(row.get("amount")),
        })

    return jsonify({"success": True, "data": results})


# ==================== 热点板块排名 ====================

@app.route("/api/stock/hot-boards", methods=["GET"])
@rate_limit
def hot_boards():
    """获取热点板块排名（行业板块）
    返回：按涨跌幅排序的行业板块列表
    """
    try:
        df = akshare_call(ak.stock_board_industry_summary_ths)
    except Exception as e:
        logger.error(f"获取热点板块失败: {e}")
        return jsonify({"success": False, "error": str(e), "data": []})

    if df.empty:
        return jsonify({"success": False, "data": []})

    # 已按涨跌幅降序排列
    results = []
    for _, row in df.iterrows():
        results.append({
            "name": row.get("板块", ""),
            "change_percent": safe_float(row.get("涨跌幅")),
            "up_count": safe_int(row.get("上涨家数")),
            "down_count": safe_int(row.get("下跌家数")),
            "volume": safe_float(row.get("总成交量")),
            "amount": safe_float(row.get("总成交额")),
        })

    return jsonify({"success": True, "data": results})


# ==================== 板块行情 ====================

@app.route("/api/stock/board/<board_type>", methods=["GET"])
@rate_limit
def stock_board(board_type):
    """获取板块股票列表（带分页和排序）
    参数：board_type = main(主板) | chiNext(创业板) | star(科创板) | bj(北交所) | all(全部)
          page = 页码（默认1），size = 每页条数（默认10，最大200）
          sort_by = 排序字段，sort_order = asc(升序) 或 desc(降序)
    """
    df = get_stock_list()
    if df.empty:
        return jsonify({"success": False, "error": "未获取到数据", "data": []})

    # 先过滤
    filtered = []
    for _, row in df.iterrows():
        raw_code = str(row.get("代码", ""))
        num_code = strip_exchange(raw_code)

        if board_type == "all":
            # 全部板块，不过滤
            pass
        elif board_type == "main":
            # 主板：6开头（排除688科创板）或0开头（深市主板含中小板）
            if not ((num_code.startswith("6") and not num_code.startswith("688")) or num_code.startswith("0")):
                continue
        elif board_type == "chiNext":
            if not num_code.startswith("3"):
                continue
        elif board_type == "star":
            if not num_code.startswith("688"):
                continue
        elif board_type == "bj":
            if not raw_code.lower().startswith("bj"):
                continue
        else:
            return jsonify({"success": False, "error": f"未知板块: {board_type}"}), 400

        filtered.append({
            "code": strip_exchange(raw_code),
            "name": row.get("名称", ""),
            "current_price": safe_float(row.get("最新价")),
            "change_percent": safe_float(row.get("涨跌幅")),
            "change_amount": safe_float(row.get("涨跌额")),
            "volume": safe_int(row.get("成交量")),
            "amount": safe_float(row.get("成交额")),
            "high_price": safe_float(row.get("最高")),
            "low_price": safe_float(row.get("最低")),
            "open_price": safe_float(row.get("今开")),
            "yesterday_close": safe_float(row.get("昨收")),
        })

    total = len(filtered)
    page = request.args.get("page", 1, type=int)
    size = request.args.get("size", 10, type=int)
    size = max(1, min(size, 200))

    # 排序（前端传 asc / desc 或 ascending / descending 均可）
    sort_by = request.args.get("sort_by", "")
    sort_order = request.args.get("sort_order", "desc")
    if not sort_by and board_type == "all":
        # 全部板块默认按股票代码排序：0开头在前，3开头，6开头，8开头
        sort_by = "code"
        sort_order = "asc"
    if sort_by:
        reverse = sort_order and sort_order.startswith("desc")
        if sort_by == "code":
            filtered.sort(key=lambda x: str(x.get("code", "")), reverse=reverse)
        else:
            filtered.sort(key=lambda x: safe_float(x.get(sort_by, 0)), reverse=reverse)

    start = (page - 1) * size
    end = min(start + size, total)
    page_data = filtered[start:end] if start < total else []

    return jsonify({"success": True, "data": page_data, "total": total})


# ==================== 个股分时 ====================

@app.route("/api/stock/intraday/<code>", methods=["GET"])
@rate_limit
def stock_intraday(code):
    """获取个股今日分时数据
    参数：code = 600519 或 sz600519
    返回：今日5分钟K线数据（时间、收盘价、成交量、成交额）
    """
    raw_code = strip_exchange(code)
    prefixed = add_exchange(raw_code)
    try:
        df = akshare_call(ak.stock_zh_a_minute, symbol=prefixed, period="1")
    except Exception as e:
        logger.error(f"获取个股分时失败 code={code}: {e}")
        return jsonify({"success": False, "error": str(e), "data": []})

    if df.empty:
        return jsonify({"success": True, "data": []})

    today = datetime.now().strftime("%Y-%m-%d")
    df_today = df[df["day"].astype(str).str.startswith(today)]

    results = []
    for _, row in df_today.iterrows():
        dt = row.get("day", "")
        t = str(dt)
        # 只保留交易时段 9:30-11:30, 13:00-15:00
        if len(t) > 16:
            hm = t[11:16]
            if hm < "09:30" or ("11:30" < hm < "13:00") or hm > "15:00":
                continue
        results.append({
            "time": t,
            "price": safe_float(row.get("close")),
            "open": safe_float(row.get("open")),
            "volume": safe_int(row.get("volume")),
            "amount": safe_float(row.get("amount")),
        })

    return jsonify({"success": True, "data": results})


# ==================== 指数分时 ====================

@app.route("/api/index/intraday/<code>", methods=["GET"])
@rate_limit
def index_intraday(code):
    """获取指数今日分时数据
    参数：code = sh000001 或 000001
    返回：今日分时数据（时间、收盘价、成交量、成交额）
    """
    # 如果已带交易所前缀则直接使用，否则补全
    if code.startswith(("sh", "sz", "SH", "SZ")):
        prefixed = code
    else:
        prefixed = f"sh{code}" if code.startswith("000") else f"sz{code}"
    try:
        df = akshare_call(ak.stock_zh_a_minute, symbol=prefixed, period="1")
    except Exception as e:
        logger.error(f"获取指数分时失败 code={code}: {e}")
        return jsonify({"success": False, "error": str(e), "data": []})

    if df.empty:
        return jsonify({"success": True, "data": []})

    today = datetime.now().strftime("%Y-%m-%d")
    df_today = df[df["day"].astype(str).str.startswith(today)]

    results = []
    for _, row in df_today.iterrows():
        dt = row.get("day", "")
        t = str(dt)
        if len(t) > 16:
            hm = t[11:16]
            if hm < "09:30" or ("11:30" < hm < "13:00") or hm > "15:00":
                continue
        results.append({
            "time": t,
            "price": safe_float(row.get("close")),
            "open": safe_float(row.get("open")),
            "volume": safe_int(row.get("volume")),
            "amount": safe_float(row.get("amount")),
        })

    return jsonify({"success": True, "data": results})


# ==================== 概念板块排名 ====================

@app.route("/api/stock/hot-concepts", methods=["GET"])
@rate_limit
def hot_concepts():
    """获取概念板块排名
    返回：按涨跌幅排序的概念板块列表（前20）
    """
    try:
        df = call_with_timeout(lambda: akshare_call(ak.stock_board_change_em), 2)
    except Exception as e:
        logger.error(f"获取概念板块失败: {e}")
        return jsonify({"success": False, "error": str(e), "data": []})

    if df.empty:
        return jsonify({"success": False, "data": []})

    results = []
    for _, row in df.iterrows():
        results.append({
            "name": str(row.get("板块名称", row.iloc[0])),
            "change_percent": safe_float(row.get("涨跌幅", row.iloc[1])),
            "volume": None,
            "amount": None,
        })

    # 取前50条（过滤掉太多系统板块）
    return jsonify({"success": True, "data": results[:50]})


# ==================== 新闻 ====================

@app.route("/api/stock/news", methods=["GET"])
@rate_limit
def stock_news():
    """获取个股新闻
    参数：code=600519
    """
    code = request.args.get("code", "")
    if not code:
        return jsonify({"success": False, "error": "缺少code参数"}), 400

    raw_code = strip_exchange(code)
    try:
        df = akshare_call(ak.stock_news_em, symbol=raw_code)
    except Exception as e:
        logger.error(f"获取新闻失败 code={code}: {e}")
        return jsonify({"success": False, "error": str(e), "data": []})

    if df.empty:
        return jsonify({"success": True, "data": []})

    results = []
    for _, row in df.iterrows():
        content = row.get("新闻内容", "")
        results.append({
            "title": row.get("新闻标题", ""),
            "summary": content[:200] if content else "",  # 取前200字做摘要
            "content": content,
            "source": row.get("文章来源", ""),
            "url": row.get("新闻链接", ""),
            "publish_time": str(row.get("发布时间", ""))[:19],
        })

    return jsonify({"success": True, "data": results})


# ==================== 财务指标 ====================

@app.route("/api/stock/finance", methods=["GET"])
@rate_limit
def stock_finance():
    """获取个股财务指标
    参数：code=600519
    """
    code = request.args.get("code", "")
    if not code:
        return jsonify({"success": False, "error": "缺少code参数"}), 400

    raw_code = strip_exchange(code)
    try:
        df = akshare_call(ak.stock_financial_analysis_indicator, symbol=raw_code)
    except Exception as e:
        logger.error(f"获取财务数据失败 code={code}: {e}")
        return jsonify({"success": False, "error": str(e), "data": []})

    if df.empty:
        return jsonify({"success": True, "data": []})

    results = safe_json(df)
    return jsonify({"success": True, "data": results})


@app.route("/api/stock/fund-flow", methods=["GET"])
@rate_limit
def stock_fund_flow():
    """个股资金流向
    参数：code=600519
    """
    code = request.args.get("code", "")
    if not code:
        return jsonify({"success": False, "error": "缺少code参数"}), 400
    raw = strip_exchange(code)
    market = "sh" if raw.startswith(("6", "9")) else "sz"
    try:
        df = akshare_call(ak.stock_individual_fund_flow, stock=raw, market=market)
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})
    if df is None or df.empty:
        return jsonify({"success": True, "data": []})
    records = safe_json(df)
    return jsonify({"success": True, "data": records})


@app.route("/api/stock/north-flow", methods=["GET"])
@rate_limit
def stock_north_flow():
    """北向资金流向"""
    try:
        df = akshare_call(ak.stock_hsgt_hist_em)
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})
    if df is None or df.empty:
        return jsonify({"success": True, "data": []})
    records = safe_json(df)
    return jsonify({"success": True, "data": records})


# ==================== 盘口数据 ====================

@app.route("/api/stock/bid-ask", methods=["GET"])
@rate_limit
def stock_bid_ask():
    """获取个股盘口数据（买卖五档）
    参数：code=600519
    返回：买一~买五价/量，卖一~卖五价/量
    """
    code = request.args.get("code", "")
    if not code:
        return jsonify({"success": False, "error": "缺少code参数"}), 400
    raw_code = strip_exchange(code)
    try:
        df = akshare_call(ak.stock_bid_ask_em, symbol=raw_code)
    except Exception as e:
        logger.error(f"获取盘口失败 code={code}: {e}")
        return jsonify({"success": False, "error": str(e)})
    if df is None or df.empty:
        return jsonify({"success": True, "data": {}})
    # 返回格式为 item/value 两列，item 为中文名
    items = df["item"].tolist()
    vals = df["value"].tolist()
    kv = dict(zip(items, vals))
    result = {}
    for i in range(1, 6):
        result[f"sell{i}"] = safe_float(kv.get(f"sell_{i}"))
        result[f"sell{i}_vol"] = safe_int(kv.get(f"sell_{i}_vol"))
        result[f"buy{i}"] = safe_float(kv.get(f"buy_{i}"))
        result[f"buy{i}_vol"] = safe_int(kv.get(f"buy_{i}_vol"))
    # item/value 两列，前20行为英文买卖档，后续为中文统计字段（按固定顺序）
    # 中文字段位置：20=最新价 22=涨跌幅 24=成交量 25=成交额 28=最高 29=最低 30=今开 31=昨收
    if len(vals) >= 32:
        result["current_price"] = safe_float(vals[20])
        result["change_percent"] = safe_float(vals[22])
        result["volume"] = safe_float(vals[24])
        result["amount"] = safe_float(vals[25])
        result["high"] = safe_float(vals[28])
        result["low"] = safe_float(vals[29])
        result["open"] = safe_float(vals[30])
        result["yesterday_close"] = safe_float(vals[31])
    return jsonify({"success": True, "data": result})


# ==================== 板块成份股 ====================

# 申万行业分类代码 → 中文名映射，用于 stock_sector_detail 降级查询
_SECTOR_LABEL_MAP = {
    "new_blhy": "半导体",
    "new_cbzz": "船舶制造",
    "new_cmyl": "传媒娱乐",
    "new_dlhy": "电力行业",
    "new_dqhy": "电气行业",
    "new_dzqj": "电子器件",
    "new_dzxx": "电子信息",
    "new_fdc": "房地产",
    "new_fdsb": "纺织设备",
    "new_fjzz": "飞机制造",
    "new_fzhy": "纺织行业",
    "new_fzjx": "纺织机械",
    "new_fzxl": "服装鞋类",
    "new_glql": "公路桥梁",
    "new_gsgq": "供水供气",
    "new_gthy": "钢铁行业",
    "new_hbhy": "环保行业",
    "new_hghy": "化工行业",
    "new_hqhy": "化纤行业",
    "new_jdhy": "家电行业",
    "new_jdly": "酒店旅游",
    "new_jjhy": "家具行业",
    "new_jrhy": "金融行业",
    "new_jtys": "交通运输",
    "new_jxhy": "机械行业",
    "new_jzjc": "建筑材料",
    "new_kfq": "开发区",
    "new_ljhy": "林业行业",
    "new_mtc": "摩托车",
    "new_myhy": "贸易行业",
    "new_nfhy": "农药化肥",
    "new_nlmy": "农林牧渔",
    "new_pg": "苹果概念",
    "new_qcgl": "汽车工业",
    "new_qcqp": "汽车配件",
    "new_qjhy": "器件行业",
    "new_rjhy": "软件行业",
    "new_sbhy": "设备行业",
    "new_slhy": "水利行业",
    "new_spyl": "食品饮料",
    "new_sqkj": "生物科技",
    "new_swhy": "商业行业",
    "new_syhy": "石油行业",
    "new_tdhy": "陶瓷行业",
    "new_txhy": "通信行业",
    "new_tzhy": "特种行业",
    "new_wlhy": "物流行业",
    "new_wsly": "卫生旅游",
    "new_xcl": "锂电池",
    "new_xxhy": "橡胶行业",
    "new_yljx": "医疗器械",
    "new_ysjs": "有色金属",
    "new_yzhy": "养殖行业",
    "new_zbhy": "装备行业",
    "new_zfhy": "纸业行业",
    "new_zqhy": "证券行业",
    "new_zyhy": "制药行业",
    "new_zzhy": "制造行业",
}

# THS 行业板块名 → 申万 sector label 映射（覆盖90个同花顺行业板块）
# 用于东方财富API不可用时的stock_sector_detail降级查询
_THS_TO_SECTOR = {
    "半导体": "new_blhy",
    "白酒": "new_spyl",
    "房地产": "new_fdc",
    "钢铁": "new_gthy",
    "有色金属": "new_ysjs",
    "煤炭开采加工": "new_syhy",
    "化工": "new_hghy",
    "化学制品": "new_hghy",
    "电力": "new_dlhy",
    "汽车": "new_qcgl",
    "汽车零部件": "new_qcqp",
    "医药": "new_zyhy",
    "医疗器械": "new_yljx",
    "金融": "new_jrhy",
    "证券": "new_zqhy",
    "银行": "new_jrhy",
    "保险": "new_jrhy",
    "通信": "new_txhy",
    "计算机": "new_rjhy",
    "软件": "new_rjhy",
    "电子": "new_dzqj",
    "消费电子": "new_dzxx",
    "家电": "new_jdhy",
    "食品饮料": "new_spyl",
    "养殖": "new_yzhy",
    "农林牧渔": "new_nlmy",
    "军工": "new_fjzz",
    "机械设备": "new_jxhy",
    "环保": "new_hbhy",
    "传媒": "new_cmyl",
    "新能源": "new_dlhy",
    "锂电池": "new_xcl",
    "光伏": "new_dzqj",
    "人工智能": "new_rjhy",
    "大数据": "new_rjhy",
    "云计算": "new_rjhy",
    # 扩展映射：覆盖同花顺90个行业板块
    "能源金属": "new_ysjs",
    "自动化设备": "new_zbhy",
    "军工电子": "new_dzqj",
    "军工装备": "new_fjzz",
    "其他电子": "new_dzqj",
    "金属新材料": "new_ysjs",
    "服装家纺": "new_fzxl",
    "白色家电": "new_jdhy",
    "饮料制造": "new_spyl",
    "通用设备": "new_jxhy",
    "电机": "new_dqhy",
    "养殖业": "new_yzhy",
    "计算机设备": "new_dzxx",
    "影视院线": "new_cmyl",
    "风电设备": "new_dlhy",
    "包装印刷": "new_zfhy",
    "家居用品": "new_jjhy",
    "光伏设备": "new_dzqj",
    "种植业与林业": "new_nlmy",
    "电子化学品": "new_hghy",
    "工程机械": "new_jxhy",
    "化学纤维": "new_hqhy",
    "专用设备": "new_zbhy",
    "其他电源设备": "new_dqhy",
    "非金属材料": "new_jzjc",
    "互联网电商": "new_myhy",
    "软件开发": "new_rjhy",
    "化学制药": "new_zyhy",
    "零售": "new_swhy",
    "小金属": "new_ysjs",
    "石油加工贸易": "new_syhy",
    "中药": "new_zyhy",
    "汽车服务及其他": "new_qcgl",
    "农产品加工": "new_nlmy",
    "环境治理": "new_hbhy",
    "IT服务": "new_rjhy",
    "建筑装饰": "new_jzjc",
    "公路铁路运输": "new_jtys",
    "文化传媒": "new_cmyl",
    "黑色家电": "new_jdhy",
    "塑料制品": "new_zzhy",
    "造纸": "new_zfhy",
    "轨交设备": "new_jxhy",
    "小家电": "new_jdhy",
    "通信服务": "new_txhy",
    "光学光电子": "new_dzqj",
    "橡胶制品": "new_xxhy",
    "环保设备": "new_hbhy",
    "纺织制造": "new_fzhy",
    "食品加工制造": "new_spyl",
    "通信设备": "new_txhy",
    "物流": "new_wlhy",
    "医疗服务": "new_wsly",
    "保险及其他": "new_jrhy",
    "机场航运": "new_jtys",
    "多元金融": "new_jrhy",
    "建筑材料": "new_jzjc",
    "厨卫电器": "new_jdhy",
    "元件": "new_dzqj",
    "电网设备": "new_dqhy",
    "医药商业": "new_swhy",
    "生物制品": "new_sqkj",
    "贵金属": "new_ysjs",
    "美容护理": "new_spyl",
    "港口航运": "new_jtys",
    "化学原料": "new_hghy",
    "农化制品": "new_nfhy",
    "汽车整车": "new_qcgl",
    "油气开采及服务": "new_syhy",
    "工业金属": "new_ysjs",
    "综合": "new_zzhy",
    "燃气": "new_gsgq",
    "贸易": "new_myhy",
    "旅游及酒店": "new_jdly",
    "游戏": "new_cmyl",
}

# 概念板块名 → 搜索关键字（用于东方财富API不可用时的全量行情关键词匹配）
_CONCEPT_KEYWORDS = {
    "人工智能": ["智能", "AI", "机器", "算法"],
    "芯片": ["芯片", "半导体", "集成电路", "微"],
    "半导体": ["半导体", "芯片", "集成电路"],
    "新能源": ["新能源", "光伏", "风电", "氢能", "锂电", "电池"],
    "锂电池": ["锂电", "电池", "锂"],
    "光伏": ["光伏", "太阳能", "光储"],
    "新能源汽车": ["新能源", "汽车", "整车", "电动"],
    "机器人": ["机器人", "机器", "自动化"],
    "军工": ["军工", "国防", "航天", "航空", "兵工"],
    "5G": ["5G", "通信"],
    "大数据": ["大数据", "数据"],
    "云计算": ["云计算", "云"],
    "区块链": ["区块链", "链"],
    "元宇宙": ["元宇宙", "AR", "VR"],
    "信创": ["信创", "国产软件", "国产替代"],
    "数字经济": ["数字", "数据"],
    "减肥药": ["药", "医药", "生物"],
    "创新药": ["创新药", "新药", "生物", "医药"],
    "国企改革": ["国企", "国资", "央企"],
    "一带一路": ["一带一路", "基建"],
    "央国企": ["央企", "国企", "国资"],
    "中字头": ["中国", "中字"],
    "华为": ["华为"],
    "苹果产业链": ["苹果", "果链"],
    "特斯拉": ["特斯拉"],
    "储能": ["储能", "电力"],
    "充电桩": ["充电"],
    "风电": ["风电", "风能"],
    "氢能源": ["氢", "氢能"],
    "碳中和": ["碳中和", "节能", "环保"],
    "医美": ["医美", "美容"],
    "白酒": ["白酒", "酒"],
    "医疗器械": ["医疗", "器械", "医药"],
    "生物医药": ["生物", "医药", "创新药"],
}

# 行业板块名 → 搜索关键字（申万映射找不到时使用）
_INDUSTRY_KEYWORDS = {
    "半导体": ["半导体", "芯片", "集成电路"],
    "白酒": ["白酒", "酒"],
    "房地产": ["房地产", "地产", "置业"],
    "钢铁": ["钢铁", "钢"],
    "汽车零部件": ["汽车", "零部件", "汽配"],
    "医药": ["医药", "药", "生物"],
    "医疗器械": ["医疗", "器械"],
    "证券": ["证券"],
    "银行": ["银行"],
    "保险": ["保险"],
    "军工装备": ["军工", "国防", "航天"],
    "军工电子": ["军工", "电子", "雷达"],
    "光伏设备": ["光伏", "太阳能"],
    "锂电池": ["锂", "电池"],
    "能源金属": ["锂", "钴", "镍", "金属"],
    "电力": ["电力"],
    "煤炭开采加工": ["煤炭", "煤"],
    "食品饮料": ["食品", "饮料", "乳"],
    "中药": ["中药", "医药"],
    "汽车整车": ["汽车", "整车"],
    "通信设备": ["通信", "通讯"],
    "软件开发": ["软件", "科技"],
    "消费电子": ["电子", "消费"],
    "工程机械": ["机械"],
    "汽车服务及其他": ["汽车"],
    "文化传媒": ["传媒"],
    "化学制药": ["化学", "制药"],
    "医疗器械": ["医疗", "器械"],
    "物流": ["物流"],
    "建筑材料": ["建材", "水泥", "玻璃"],
    "互联网电商": ["互联", "电商"],
    "自动化设备": ["自动化", "智能装备"],
    "环保": ["环保"],
    "旅游及酒店": ["旅游", "酒店"],
    "纺织制造": ["纺织"],
    "仪器仪表": ["仪器", "仪表"],
    "家电": ["家电"],
    "种植业与林业": ["农业", "林业", "种植"],
    "农产品加工": ["农业", "加工"],
    "养殖业": ["养殖", "畜牧", "禽"],
    "生物制品": ["生物"],
    "医疗服务": ["医疗", "医院"],
    "多元金融": ["金融", "投资"],
    "港口航运": ["港口", "航运", "海运"],
    "公路铁路运输": ["公路", "铁路", "运输"],
    "机场航运": ["机场", "航空"],
    "燃气": ["燃气"],
    "贸易": ["贸易"],
    "综合": ["综合"],
    "造纸": ["造纸"],
    "游戏": ["游戏"],
    "教育": ["教育"],
    "零售": ["零售", "百货"],
    "小家电": ["家电"],
    "厨卫电器": ["家电"],
    "黑色家电": ["家电"],
    "化学原料": ["化学", "化工"],
    "橡胶制品": ["橡胶"],
    "塑料制品": ["塑料"],
    "包装印刷": ["包装", "印刷"],
    "钢铁": ["钢铁"],
    "工业金属": ["金属", "铝", "铜"],
    "贵金属": ["黄金", "贵金属"],
    "小金属": ["金属"],
    "金属新材料": ["金属", "新材料"],
}

_cache["sector_stocks"] = {}  # sector_label -> [stock_list]


def _fuzzy_match_sector(name):
    """模糊匹配同花顺板块名→申万sector label
    在_SECTOR_LABEL_MAP中找名称包含关系
    """
    name_lower = name.replace(" ", "")
    best_match = None
    best_len = 0
    for label, cn_name in _SECTOR_LABEL_MAP.items():
        cn_clean = cn_name.replace(" ", "")
        # 同花顺名包含申万名 或 申万名包含同花顺名
        if name_lower in cn_clean or cn_clean in name_lower:
            match_len = max(len(name_lower), len(cn_clean))
            if match_len > best_len:
                best_len = match_len
                best_match = label
    # 如未匹配到，尝试按首词匹配
    if not best_match:
        first_word = name.split("行业")[0].split("及")[0].split("与")[0].split("、")[0]
        if len(first_word) >= 2:
            for label, cn_name in _SECTOR_LABEL_MAP.items():
                if first_word in cn_name:
                    if len(first_word) > best_len:
                        best_len = len(first_word)
                        best_match = label
    return best_match


def _fetch_sector_stocks(sector_label):
    """从 stock_sector_detail 获取板块成份股（带缓存）"""
    now = time.time()
    if sector_label in _cache.get("sector_stocks", {}):
        entry = _cache["sector_stocks"][sector_label]
        if now - entry["time"] < 3600:  # 缓存1小时
            return entry["data"]
    try:
        df = akshare_call(ak.stock_sector_detail, sector=sector_label)
        if df is None or df.empty:
            return None
        results = []
        for _, row in df.iterrows():
            results.append({
                "code": str(row.get("code", "")),
                "name": row.get("name", ""),
                "current_price": safe_float(row.get("trade")),
                "change_percent": safe_float(row.get("changepercent")),
                "change_amount": safe_float(row.get("pricechange")),
                "volume": safe_float(row.get("volume")),
                "amount": safe_float(row.get("amount")),
            })
        _cache["sector_stocks"][sector_label] = {"data": results, "time": now}
        return results
    except Exception:
        return None


@app.route("/api/stock/board-industry-stocks", methods=["GET"])
@rate_limit
def board_industry_stocks():
    """获取行业板块成份股
    参数：name=半导体
    降级策略：东方财富cons_em → 申万行业分类 → 关键字匹配
    """
    _t_start = time.time()
    name = request.args.get("name", "")
    if not name:
        return jsonify({"success": False, "error": "缺少name参数"}), 400

    # 方案1: 尝试东方财富 cons_em（非交易时间跳过，交易时间2秒超时）
    if is_trading_time():
        try:
            t0 = time.time()
            df = call_with_timeout(lambda: akshare_call(ak.stock_board_industry_cons_em, max_retries=1, symbol=name), 2,)
            logger.info(f"EM cons_em 耗时 {time.time()-t0:.1f}s name={name}")
            if df is not None and not df.empty:
                results = []
                for _, row in df.iterrows():
                    results.append({
                        "code": row.get("代码", ""),
                        "name": row.get("名称", ""),
                        "current_price": safe_float(row.get("现价")),
                        "change_percent": safe_float(row.get("涨跌幅")),
                        "change_amount": safe_float(row.get("涨跌额")),
                        "volume": safe_float(row.get("总成交量")),
                        "amount": safe_float(row.get("总成交额")),
                    })
                logger.info(f"行业板块总耗时 {time.time()-_t_start:.1f}s name={name} source=em")
                return jsonify({"success": True, "data": results})
        except Exception:
            logger.info(f"EM cons_em 不可用，尝试申万降级 name={name}")
    else:
        logger.info(f"非交易时间，跳过EM API name={name}")

    # 方案2: 降级到申万行业分类（精确匹配）
    t0 = time.time()
    sector_label = _THS_TO_SECTOR.get(name)
    if not sector_label:
        sector_label = _fuzzy_match_sector(name)
    if sector_label:
        results = _fetch_sector_stocks(sector_label)
        if results is not None:
            logger.info(f"行业板块总耗时 {time.time()-_t_start:.1f}s name={name} source=shenwan")
            return jsonify({"success": True, "data": results, "source": "shenwan"})
    logger.info(f"申万耗时 {time.time()-t0:.1f}s name={name} sector={sector_label}")

    # 方案3: 关键字匹配（从全量行情中匹配）
    t0 = time.time()
    try:
        spot = get_stock_list()
        if spot is not None and not spot.empty:
            keywords = _INDUSTRY_KEYWORDS.get(name, [name])
            results = []
            matched_codes = set()
            for _, row in spot.iterrows():
                stock_name = str(row.get("名称", ""))
                for kw in keywords:
                    if kw in stock_name:
                        code = str(row.get("代码", ""))
                        if code not in matched_codes:
                            matched_codes.add(code)
                            results.append({
                                "code": str(row.get("代码", "")),
                                "name": stock_name,
                                "current_price": safe_float(row.get("最新价")),
                                "change_percent": safe_float(row.get("涨跌幅")),
                                "change_amount": safe_float(row.get("涨跌额")),
                                "volume": safe_float(row.get("成交量")),
                                "amount": safe_float(row.get("成交额")),
                            })
                        break
            if results:
                logger.info(f"关键字耗时 {time.time()-t0:.1f}s name={name} count={len(results)}")
                logger.info(f"行业板块总耗时 {time.time()-_t_start:.1f}s name={name} source=keyword")
                return jsonify({"success": True, "data": results, "source": "keyword"})
    except Exception:
        pass

    logger.info(f"行业板块全部降级失败 name={name}")
    return jsonify({"success": True, "data": [], "source": "fallback"})


@app.route("/api/stock/board-concept-stocks", methods=["GET"])
@rate_limit
def board_concept_stocks():
    """获取概念板块成份股
    参数：name=人工智能
    降级策略：东方财富cons_em → 关键字匹配
    """
    name = request.args.get("name", "")
    if not name:
        return jsonify({"success": False, "error": "缺少name参数"}), 400

    # 方案1: 尝试东方财富 cons_em（非交易时间跳过，交易时间2秒超时）
    if is_trading_time():
        try:
            t0 = time.time()
            df = call_with_timeout(lambda: akshare_call(ak.stock_board_concept_cons_em, max_retries=1, symbol=name), 2,)
            logger.info(f"EM cons_em 耗时 {time.time()-t0:.1f}s name={name}")
            if df is not None and not df.empty:
                results = []
                for _, row in df.iterrows():
                    results.append({
                        "code": row.get("代码", ""),
                        "name": row.get("名称", ""),
                        "current_price": safe_float(row.get("现价")),
                        "change_percent": safe_float(row.get("涨跌幅")),
                        "change_amount": safe_float(row.get("涨跌额")),
                        "volume": safe_float(row.get("总成交量")),
                        "amount": safe_float(row.get("总成交额")),
                    })
                return jsonify({"success": True, "data": results})
        except Exception:
            logger.info(f"EM cons_em 不可用，概念板块 name={name} 尝试关键字匹配")
    else:
        logger.info(f"非交易时间，跳过EM API name={name}")

    # 方案2: 按关键词从全量行情中匹配
    try:
        spot = get_stock_list()
        if spot is not None and not spot.empty:
            keywords = _CONCEPT_KEYWORDS.get(name, [name])
            results = []
            matched_codes = set()
            for _, row in spot.iterrows():
                stock_name = str(row.get("名称", ""))
                for kw in keywords:
                    if kw in stock_name:
                        code = str(row.get("代码", ""))
                        if code not in matched_codes:
                            matched_codes.add(code)
                            results.append({
                                "code": str(row.get("代码", "")),
                                "name": stock_name,
                                "current_price": safe_float(row.get("最新价")),
                                "change_percent": safe_float(row.get("涨跌幅")),
                                "change_amount": safe_float(row.get("涨跌额")),
                                "volume": safe_float(row.get("成交量")),
                                "amount": safe_float(row.get("成交额")),
                            })
                        break
            if results:
                return jsonify({"success": True, "data": results, "source": "keyword"})
    except Exception:
        pass

    return jsonify({"success": True, "data": [], "source": "fallback"})


# ==================== 健康检查 ====================

@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "service": "akshare-data-service"})


# ==================== RAG 智能问答 ====================

@app.route("/api/rag/info", methods=["GET"])
def rag_info():
    """获取 RAG 向量库状态"""
    if rag is None:
        return jsonify({"success": False, "error": "RAG服务未初始化（Qdrant未运行）"}), 503
    return jsonify({"success": True, "data": rag.get_collection_info()})


@app.route("/api/rag/ask", methods=["POST"])
def rag_ask():
    """RAG 问答
    POST JSON: {"question": "茅台最近业绩怎么样"}
    """
    if rag is None:
        return jsonify({"success": False, "error": "RAG服务未初始化（Qdrant未运行）"}), 503
    data = request.get_json(force=True, silent=True) or {}
    question = data.get("question", "").strip()
    if not question:
        return jsonify({"success": False, "error": "缺少 question 参数"}), 400
    top_k = int(data.get("top_k", 5))
    history = data.get("history") or None

    result = rag.ask(question, top_k=top_k, history=history)
    return jsonify({"success": True, "data": result})


@app.route("/api/rag/ask/stream", methods=["POST"])
def rag_ask_stream():
    """RAG 流式问答（SSE）"""
    if rag is None:
        return jsonify({"success": False, "error": "RAG服务未初始化（Qdrant未运行）"}), 503
    data = request.get_json(force=True, silent=True) or {}
    question = data.get("question", "").strip()
    if not question:
        return jsonify({"success": False, "error": "缺少 question 参数"}), 400
    top_k = int(data.get("top_k", 5))
    history = data.get("history") or None

    def generate():
        for evt_type, payload in rag.ask_stream(question, top_k=top_k, history=history):
            if evt_type == "answer":
                yield f"data: {json.dumps({'type': 'answer', 'content': payload}, ensure_ascii=False)}\n\n"
            elif evt_type == "sources":
                yield f"data: {json.dumps({'type': 'sources', 'sources': payload}, ensure_ascii=False)}\n\n"
        yield "data: {\"type\":\"done\"}\n\n"

    from flask import Response as FlaskResponse
    return FlaskResponse(generate(), mimetype="text/event-stream", headers={
        "Cache-Control": "no-cache",
        "X-Accel-Buffering": "no",
    })


@app.route("/api/rag/ask", methods=["GET"])
def rag_ask_get():
    """GET 方式问答（方便浏览器测试）"""
    if rag is None:
        return jsonify({"success": False, "error": "RAG服务未初始化（Qdrant未运行）"}), 503
    question = request.args.get("question", "").strip()
    if not question:
        return jsonify({"success": False, "error": "缺少 question 参数"}), 400
    result = rag.ask(question)
    return jsonify({"success": True, "data": result})


@app.route("/api/rag/ingest", methods=["POST"])
def rag_ingest():
    """触发数据索引"""
    if rag is None:
        return jsonify({"success": False, "error": "RAG服务未初始化（Qdrant未运行）"}), 503
    data = request.get_json(force=True, silent=True) or {}
    stock_list = data.get("stocks")

    if not stock_list:
        # 自动从热点板块拉前30只股票
        try:
            df = get_stock_list()
            if df is not None and not df.empty:
                # 取前30只（按成交额排序的大盘股）
                stock_list = []
                for _, row in df.head(30).iterrows():
                    code = strip_exchange(str(row.get("代码", "")))
                    name = str(row.get("名称", ""))
                    if code and name:
                        stock_list.append((code, name))
        except Exception as e:
            return jsonify({"success": False, "error": f"获取股票列表失败: {e}"}), 500

    if not stock_list:
        return jsonify({"success": False, "error": "无股票可索引"}), 400

    logger.info(f"开始索引 {len(stock_list)} 只股票...")
    result = rag.ingest_stock_list(stock_list)
    return jsonify({"success": True, "data": result})


@app.route("/api/rag/upload", methods=["POST"])
def rag_upload():
    """上传文档（PDF/TXT）并索引到向量库"""
    if rag is None:
        return jsonify({"success": False, "error": "RAG服务未初始化（Qdrant未运行）"}), 503
    import io

    if "file" not in request.files:
        return jsonify({"success": False, "error": "缺少 file 字段"}), 400

    file = request.files["file"]
    if file.filename == "":
        return jsonify({"success": False, "error": "文件名为空"}), 400

    filename = file.filename
    stock_name = request.form.get("stock_name", "")

    # 提取文本
    try:
        if filename.lower().endswith(".pdf"):
            import PyPDF2
            reader = PyPDF2.PdfReader(file)
            text = "\n".join(page.extract_text() or "" for page in reader.pages)
        elif filename.lower().endswith(".txt"):
            text = file.read().decode("utf-8", errors="replace")
        else:
            return jsonify({"success": False, "error": "仅支持 PDF/TXT 文件"}), 400
    except Exception as e:
        return jsonify({"success": False, "error": f"文件解析失败: {e}"}), 400

    text = text.strip()
    if not text:
        return jsonify({"success": False, "error": "未能提取到文本内容"}), 400

    count = rag.ingest_document(text, filename, stock_name=stock_name)
    return jsonify({"success": True, "data": {"filename": filename, "chunks": count}})


@app.route("/api/rag/finance-analysis", methods=["POST"])
def rag_finance_analysis():
    """AI 财报分析：获取财务数据后调用 LLM 解读"""
    data = request.get_json(force=True, silent=True) or {}
    code = data.get("code", "")
    name = data.get("name", "")
    if not code:
        return jsonify({"success": False, "error": "缺少 code 参数"}), 400

    raw_code = strip_exchange(code)
    try:
        df = akshare_call(ak.stock_financial_abstract, symbol=raw_code)
    except Exception as e:
        return jsonify({"success": False, "error": f"获取财务数据失败: {e}"}), 500

    if df is None or df.empty:
        return jsonify({"success": False, "error": "暂无财务数据"}), 404

    # 指标中文名映射（从第一列获取）
    records = safe_json(df)
    # 取最近两期（最后两列）+ 指标名
    cols = list(records[0].keys())
    date_cols = [c for c in cols if c not in ('选项', '指标')][-2:]  # 最近两期
    key_indicators = ['营业收入', '营业成本', '毛利润', '归属净利润', '每股收益',
                      '每股净资产', '净资产收益率(ROE)', '总资产报酬率(ROA)',
                      '毛利率', '销售净利率', '经营现金流净额', '股东权益合计(净资产)']
    summary = []
    for row in records:
        name = str(row.get('指标', ''))
        if any(k in name for k in key_indicators):
            vals = {d: row.get(d, '') for d in date_cols}
            summary.append({'指标': name, **vals})

    if not summary:
        summary = records[:5]
        date_cols = [c for c in cols if c not in ('选项', '指标')][-2:]

    info = json.dumps(summary, ensure_ascii=False, indent=2)
    period_info = f"数据期间: {' vs '.join(date_cols)}"

    prompt = (
        f"你是一位专业的股票分析师。请分析 {name}({code}) 的财务数据，给出专业解读。\n\n"
        f"{period_info}\n"
        f"财务指标数据：\n{info}\n\n"
        "请从以下角度分析：\n"
        "1. 盈利能力（ROE、净利率等）\n"
        "2. 成长性（营收/利润增速）\n"
        "3. 财务健康状况（资产负债率、流动比率等）\n"
        "4. 综合评估与投资建议\n\n"
        "用中文回答，简洁专业，引用具体数据。"
    )
    try:
        r = requests.post(
            "https://api.deepseek.com/v1/chat/completions",
            headers={"Authorization": f"Bearer {DEEPSEEK_KEY}", "Content-Type": "application/json"},
            json={"model": DEEPSEEK_MODEL, "messages": [{"role": "user", "content": prompt}], "temperature": 0.3, "max_tokens": 2000},
            timeout=30,
        )
        r.raise_for_status()
        answer = r.json()["choices"][0]["message"]["content"]
    except Exception as e:
        return jsonify({"success": False, "error": f"LLM 调用失败: {e}"}), 500

    return jsonify({"success": True, "data": {"analysis": answer}})


# ==================== 工具 ====================

def safe_float(val):
    try:
        v = float(val)
        if pd.isna(v):
            return None
        return round(v, 2)
    except (ValueError, TypeError):
        return None


def safe_int(val):
    try:
        v = int(float(str(val)))
        return v
    except (ValueError, TypeError):
        return None


# ==================== 启动 ====================

def warmup_cache():
    """后台预热股票缓存，避免首次搜索超时"""
    logger.info("后台预热股票缓存...")
    try:
        start = time.time()
        get_stock_list()
        logger.info(f"缓存预热完成，耗时 {time.time()-start:.1f} 秒，共 {len(_cache['stock_list'])} 只股票")
    except Exception as e:
        logger.warning(f"缓存预热失败（下次搜索时会自动加载）: {e}")

if __name__ == "__main__":
    # 同步预热缓存，启动时多等一会，换来后续请求秒回
    logger.info("正在加载全量股票数据（首次约30秒，后续请求秒回）...")
    warmup_cache()
    logger.info(f"启动AKShare数据服务，端口5000")
    app.run(host="0.0.0.0", port=5000, debug=False)
