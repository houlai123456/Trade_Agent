import request from './request'

/** 查询账户资产 */
export function getAccount() {
  return request.get('/trade/account')
}

/** 模拟买入 */
export function buyStock(code, quantity, price) {
  return request.post('/trade/buy', { code, quantity, price })
}

/** 模拟卖出 */
export function sellStock(code, quantity, price) {
  return request.post('/trade/sell', { code, quantity, price })
}

/** 查询持仓 */
export function getPositions() {
  return request.get('/trade/positions')
}

/** 查询交易流水 */
export function getOrders() {
  return request.get('/trade/orders')
}

/** 创建限价挂单 */
export function placeOrder(code, quantity, price, direction) {
  return request.post('/trade/place-order', { code, quantity, price, direction })
}

/** 撤销挂单 */
export function cancelOrder(orderId) {
  return request.post(`/trade/cancel-order/${orderId}`)
}

/** 查询挂单列表 */
export function getPendingOrders() {
  return request.get('/trade/pending-orders')
}
