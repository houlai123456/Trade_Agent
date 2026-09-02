"""
多数据源管理器
支持 AKShare（主源）+ Ashare（备源）自动故障切换
"""
import logging
import time
from typing import Optional, Dict, Any, List
import pandas as pd

logger = logging.getLogger(__name__)


class MultiSourceManager:
    """多数据源管理器，自动故障切换"""

    def __init__(self):
        self.primary_source = "akshare"
        self.backup_source = "ashare"
        self.akshare_available = True
        self.ashare_available = True
        self.last_switch_time = 0
        self.switch_cooldown = 60  # 切换冷却期60秒

        # 统计信息
        self.stats = {
            "akshare_success": 0,
            "akshare_failed": 0,
            "ashare_success": 0,
            "ashare_failed": 0,
            "fallback_count": 0
        }

    def get_quote(self, stock_code: str) -> Optional[Dict[str, Any]]:
        """获取实时行情（支持故障切换）"""
        # 先尝试主源
        if self.akshare_available:
            result = self._get_quote_from_akshare(stock_code)
            if result:
                self.stats["akshare_success"] += 1
                return result
            else:
                self.stats["akshare_failed"] += 1
                logger.warning(f"AKShare获取行情失败: {stock_code}")

        # 主源失败，切换到备源
        if self.ashare_available:
            logger.info(f"切换到Ashare备用源获取行情: {stock_code}")
            self.stats["fallback_count"] += 1
            result = self._get_quote_from_ashare(stock_code)
            if result:
                self.stats["ashare_success"] += 1
                return result
            else:
                self.stats["ashare_failed"] += 1

        logger.error(f"所有数据源均失败: {stock_code}")
        return None

    def get_kline(self, stock_code: str, period: str = "daily",
                  start_date: str = None, end_date: str = None) -> Optional[List[Dict]]:
        """获取K线数据（支持故障切换）"""
        # 先尝试主源
        if self.akshare_available:
            result = self._get_kline_from_akshare(stock_code, period, start_date, end_date)
            if result:
                self.stats["akshare_success"] += 1
                return result
            else:
                self.stats["akshare_failed"] += 1
                logger.warning(f"AKShare获取K线失败: {stock_code}")

        # 主源失败，切换到备源
        if self.ashare_available:
            logger.info(f"切换到Ashare备用源获取K线: {stock_code}")
            self.stats["fallback_count"] += 1
            result = self._get_kline_from_ashare(stock_code, period, start_date, end_date)
            if result:
                self.stats["ashare_success"] += 1
                return result
            else:
                self.stats["ashare_failed"] += 1

        logger.error(f"所有数据源均失败: {stock_code}")
        return None

    def _get_quote_from_akshare(self, stock_code: str) -> Optional[Dict[str, Any]]:
        """从AKShare获取实时行情"""
        try:
            import akshare as ak

            # AKShare实时行情接口
            df = ak.stock_zh_a_spot_em()

            # 查找目标股票
            code_clean = stock_code.replace("sh", "").replace("sz", "")
            stock_data = df[df['代码'] == code_clean]

            if stock_data.empty:
                return None

            row = stock_data.iloc[0]
            return {
                "code": stock_code,
                "name": str(row.get('名称', '')),
                "price": float(row.get('最新价', 0)),
                "change": float(row.get('涨跌额', 0)),
                "change_percent": float(row.get('涨跌幅', 0)),
                "volume": float(row.get('成交量', 0)),
                "amount": float(row.get('成交额', 0)),
                "open": float(row.get('今开', 0)),
                "high": float(row.get('最高', 0)),
                "low": float(row.get('最低', 0)),
                "pre_close": float(row.get('昨收', 0)),
                "source": "akshare"
            }
        except Exception as e:
            logger.error(f"AKShare获取行情异常: {e}")
            return None

    def _get_quote_from_ashare(self, stock_code: str) -> Optional[Dict[str, Any]]:
        """从Ashare获取实时行情（新浪+腾讯双核心）"""
        try:
            # 方案1：直接调用新浪API
            result = self._get_quote_from_sina(stock_code)
            if result:
                return result

            # 方案2：调用腾讯API作为备份
            result = self._get_quote_from_tencent(stock_code)
            if result:
                return result

            return None
        except Exception as e:
            logger.error(f"Ashare获取行情异常: {e}")
            return None

    def _get_quote_from_sina(self, stock_code: str) -> Optional[Dict[str, Any]]:
        """从新浪财经API获取实时行情"""
        try:
            import requests

            # 新浪实时行情API
            url = f"http://hq.sinajs.cn/list={stock_code}"
            response = requests.get(url, timeout=3)
            response.encoding = 'gbk'

            if response.status_code != 200:
                return None

            # 解析数据
            content = response.text
            if 'var hq_str_' not in content:
                return None

            data_str = content.split('"')[1]
            parts = data_str.split(',')

            if len(parts) < 32:
                return None

            return {
                "code": stock_code,
                "name": parts[0],
                "price": float(parts[3]) if parts[3] else 0,
                "change": float(parts[3]) - float(parts[2]) if parts[3] and parts[2] else 0,
                "change_percent": ((float(parts[3]) - float(parts[2])) / float(parts[2]) * 100) if parts[3] and parts[2] and float(parts[2]) > 0 else 0,
                "volume": float(parts[8]) if parts[8] else 0,
                "amount": float(parts[9]) if parts[9] else 0,
                "open": float(parts[1]) if parts[1] else 0,
                "high": float(parts[4]) if parts[4] else 0,
                "low": float(parts[5]) if parts[5] else 0,
                "pre_close": float(parts[2]) if parts[2] else 0,
                "source": "sina"
            }
        except Exception as e:
            logger.error(f"新浪API获取行情失败: {e}")
            return None

    def _get_quote_from_tencent(self, stock_code: str) -> Optional[Dict[str, Any]]:
        """从腾讯财经API获取实时行情"""
        try:
            import requests

            # 转换股票代码格式（sh600519 -> sh600519）
            code_with_market = stock_code
            if not stock_code.startswith(('sh', 'sz')):
                if stock_code.startswith('6'):
                    code_with_market = f"sh{stock_code}"
                else:
                    code_with_market = f"sz{stock_code}"

            # 腾讯实时行情API
            url = f"http://qt.gtimg.cn/q={code_with_market}"
            response = requests.get(url, timeout=3)
            response.encoding = 'gbk'

            if response.status_code != 200:
                return None

            # 解析数据
            content = response.text
            if 'v_' not in content:
                return None

            data_str = content.split('"')[1]
            parts = data_str.split('~')

            if len(parts) < 50:
                return None

            return {
                "code": stock_code,
                "name": parts[1],
                "price": float(parts[3]) if parts[3] else 0,
                "change": float(parts[31]) if parts[31] else 0,
                "change_percent": float(parts[32]) if parts[32] else 0,
                "volume": float(parts[6]) * 100 if parts[6] else 0,  # 手转股
                "amount": float(parts[37]) if parts[37] else 0,
                "open": float(parts[5]) if parts[5] else 0,
                "high": float(parts[33]) if parts[33] else 0,
                "low": float(parts[34]) if parts[34] else 0,
                "pre_close": float(parts[4]) if parts[4] else 0,
                "source": "tencent"
            }
        except Exception as e:
            logger.error(f"腾讯API获取行情失败: {e}")
            return None

    def _get_kline_from_akshare(self, stock_code: str, period: str,
                                 start_date: str, end_date: str) -> Optional[List[Dict]]:
        """从AKShare获取K线数据"""
        try:
            import akshare as ak

            code_clean = stock_code.replace("sh", "").replace("sz", "")

            # 根据周期选择接口
            if period == "daily":
                df = ak.stock_zh_a_hist(symbol=code_clean, period="daily",
                                       start_date=start_date, end_date=end_date, adjust="qfq")
            elif period == "weekly":
                df = ak.stock_zh_a_hist(symbol=code_clean, period="weekly",
                                       start_date=start_date, end_date=end_date, adjust="qfq")
            elif period == "monthly":
                df = ak.stock_zh_a_hist(symbol=code_clean, period="monthly",
                                       start_date=start_date, end_date=end_date, adjust="qfq")
            else:
                return None

            if df.empty:
                return None

            result = []
            for _, row in df.iterrows():
                result.append({
                    "date": str(row['日期']),
                    "open": float(row['开盘']),
                    "close": float(row['收盘']),
                    "high": float(row['最高']),
                    "low": float(row['最低']),
                    "volume": float(row['成交量']),
                    "amount": float(row['成交额']),
                    "change_percent": float(row.get('涨跌幅', 0))
                })

            return result
        except Exception as e:
            logger.error(f"AKShare获取K线异常: {e}")
            return None

    def _get_kline_from_ashare(self, stock_code: str, period: str,
                                start_date: str, end_date: str) -> Optional[List[Dict]]:
        """从Ashare获取K线数据（通过新浪接口）"""
        try:
            import requests
            import json
            from datetime import datetime

            # 新浪K线接口
            code_clean = stock_code.replace("sh", "").replace("sz", "")

            # 判断市场
            if stock_code.startswith('sh') or code_clean.startswith('6'):
                market_code = f"sh{code_clean}"
            else:
                market_code = f"sz{code_clean}"

            # 新浪K线API（日线）
            if period == "daily":
                scale = 240  # 日线
            elif period == "weekly":
                scale = 1200  # 周线
            elif period == "monthly":
                scale = 7200  # 月线
            else:
                return None

            url = f"http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol={market_code}&scale={scale}&datalen=1000"

            response = requests.get(url, timeout=5)
            if response.status_code != 200:
                return None

            data = json.loads(response.text)
            if not data:
                return None

            result = []
            for item in data:
                result.append({
                    "date": item['day'],
                    "open": float(item['open']),
                    "close": float(item['close']),
                    "high": float(item['high']),
                    "low": float(item['low']),
                    "volume": float(item['volume']),
                    "amount": 0,  # 新浪接口不提供成交额
                    "change_percent": 0  # 需要计算
                })

            return result
        except Exception as e:
            logger.error(f"Ashare获取K线异常: {e}")
            return None

    def get_stats(self) -> Dict[str, Any]:
        """获取数据源统计信息"""
        total_requests = (self.stats["akshare_success"] + self.stats["akshare_failed"] +
                         self.stats["ashare_success"] + self.stats["ashare_failed"])

        return {
            "total_requests": total_requests,
            "akshare_success_rate": f"{self.stats['akshare_success'] / max(1, self.stats['akshare_success'] + self.stats['akshare_failed']) * 100:.1f}%",
            "ashare_success_rate": f"{self.stats['ashare_success'] / max(1, self.stats['ashare_success'] + self.stats['ashare_failed']) * 100:.1f}%",
            "fallback_count": self.stats["fallback_count"],
            "fallback_rate": f"{self.stats['fallback_count'] / max(1, total_requests) * 100:.1f}%",
            "primary_source": self.primary_source,
            "backup_source": self.backup_source,
            **self.stats
        }


# 全局单例
_manager = MultiSourceManager()


def get_manager() -> MultiSourceManager:
    """获取多数据源管理器单例"""
    return _manager
