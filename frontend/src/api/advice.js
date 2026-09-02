import request from './request'

/**
 * 查询股票的历史建议和准确率
 */
export function getAdviceHistory(stockCode) {
  return request.get(`/api/agent-advice/history/${stockCode}`)
}

/**
 * 查询某个建议的监控历史
 */
export function getAdviceMonitor(adviceId) {
  return request.get(`/api/agent-advice/monitor/${adviceId}`)
}

/**
 * 查询所有活跃建议
 */
export function getActiveAdvices() {
  return request.get('/api/agent-advice/active')
}

/**
 * 手动关闭建议监控
 */
export function closeAdvice(adviceId) {
  return request.post(`/api/agent-advice/close/${adviceId}`)
}

/**
 * 查询股票最新的监控状态
 */
export function getLatestMonitor(stockCode) {
  return request.get(`/api/agent-advice/latest-monitor/${stockCode}`)
}

/**
 * 查询股票的准确率统计
 */
export function getAccuracyStats(stockCode) {
  return request.get(`/api/agent-advice/accuracy/${stockCode}`)
}
