import request from './request'

// ========== 盯盘规则 ==========

export function getWatchRules() {
  return request.get('/watch/rules')
}

export function addWatchRule(code, name, conditionType, targetPrice) {
  return request.post('/watch/rules', { code, name, conditionType, targetPrice })
}

export function updateWatchRule(id, data) {
  return request.put(`/watch/rules/${id}`, data)
}

export function deleteWatchRule(id) {
  return request.delete(`/watch/rules/${id}`)
}

// ========== 条件单 ==========

export function getConditionOrders() {
  return request.get('/watch/orders')
}

export function addConditionOrder(code, name, direction, conditionType, triggerPrice, quantity, orderPrice) {
  return request.post('/watch/orders', { code, name, direction, conditionType, triggerPrice, quantity, orderPrice })
}

export function cancelConditionOrder(id) {
  return request.post(`/watch/orders/${id}/cancel`)
}
