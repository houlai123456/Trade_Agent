import request from './request'

// 获取股票相关新闻
export function getNewsByStock(stockCode, limit = 20) {
  return request.get(`/news/stock/${stockCode}`, { params: { limit }, timeout: 120000 })
}

// 获取最新新闻
export function getLatestNews(limit = 20) {
  return request.get('/news/latest', { params: { limit }, timeout: 120000 })
}

// 获取新闻详情
export function getNewsDetail(id) {
  return request.get(`/news/${id}`)
}

// 分析新闻情绪
export function analyzeSentiment(id) {
  return request.post(`/news/${id}/sentiment`)
}
