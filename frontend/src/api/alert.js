import request from './request'

// 获取最近预警
export function getRecentAlerts(limit = 20) {
  return request.get('/alert/recent', { params: { limit } })
}

// 获取未读预警数
export function getUnreadAlertCount() {
  return request.get('/alert/unread/count')
}

// 标记已读
export function markAlertRead(id) {
  return request.put(`/alert/${id}/read`)
}

// 手动触发检查
export function checkAlerts() {
  return request.post('/alert/check')
}
