import request from './request'

/** 解析自然语言交易指令 */
export function parseTradeIntent(message) {
  return request.post('/trade-agent/parse', { message })
}

/** 执行已确认的交易 */
export function executeTrade(intent) {
  return request.post('/trade-agent/execute', intent)
}

/** 查询账户资产 */
export function getTradeAccount() {
  return request.get('/trade-agent/account')
}

/** 查询持仓列表 */
export function getTradePositions() {
  return request.get('/trade-agent/positions')
}
