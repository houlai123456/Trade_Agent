import request from './request'
import axios from 'axios'

// 搜索股票（用独立实例，超时30秒以应对首次缓存预热）
const searchRequest = axios.create({
  baseURL: '/api',
  timeout: 30000,
})
searchRequest.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const msg = error.response?.data?.message || error.message || '请求失败'
    console.error('搜索请求失败:', msg)
    return Promise.reject(error)
  }
)

export function searchStock(keyword) {
  return searchRequest.get('/stock/search', { params: { keyword } })
}

// 获取实时行情（Java→Python链路，超时30秒）
export function getQuote(code) {
  return request.get(`/stock/quote/${code}`, { timeout: 30000 })
}

// 批量获取行情
export function getQuotes(codes) {
  return request.get('/stock/quotes', { params: { codes: codes.join(',') } })
}

// 获取K线数据
export function getKline(code, period = 'DAY', limit = 120) {
  return request.get(`/stock/kline/${code}`, { params: { period, limit } })
}

// 获取自选股列表
export function getWatchlist() {
  return request.get('/stock/watchlist')
}

// 添加自选股
export function addWatchlist(code, remark = '') {
  return request.post('/stock/watchlist', { code, remark })
}

// 删除自选股
export function removeWatchlist(code) {
  return request.delete(`/stock/watchlist/${code}`)
}

// 获取指数行情
export function getIndexQuotes() {
  return request.get('/stock/index')
}

// Python数据源请求（超时60秒，AKShare首次调用较慢）
const pythonRequest = axios.create({
  baseURL: '/api',
  timeout: 60000,
})
pythonRequest.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const msg = error.response?.data?.message || error.message || '请求失败'
    console.error('Python数据请求失败:', msg)
    return Promise.reject(error)
  }
)

// 获取单个指数行情（Python数据源）
export async function getIndexQuote(code) {
  const res = await pythonRequest.get(`/index/quote/${code}`)
  return res.data || res
}

// 获取指数K线（Python数据源）
export async function getIndexKline(code, period = 'daily', limit = 200) {
  const res = await pythonRequest.get(`/index/kline/${code}`, { params: { period, limit } })
  return res.data || res
}

// 获取市场概况
export function getMarketOverview() {
  return request.get('/stock/market/overview')
}

// 获取板块股票列表（分页，支持排序）
export async function getBoardStocks(board, page = 1, size = 10, sortBy = '', sortOrder = '') {
  const params = { page, size }
  if (sortBy) params.sort_by = sortBy
  if (sortOrder) params.sort_order = sortOrder
  const res = await request.get(`/stock/board/${board}`, { params })
  return res // { total, data }
}

// 获取热点板块排名
export async function getHotBoards() {
  const res = await pythonRequest.get('/stock/hot-boards')
  return res.data || res
}

// 获取概念板块排名
export async function getHotConcepts() {
  const res = await pythonRequest.get('/stock/hot-concepts')
  return res.data || res
}

// 获取行业板块成份股
export async function getBoardIndustryStocks(name) {
  const res = await pythonRequest.get('/stock/board-industry-stocks', { params: { name } })
  return res.data || res
}

// 获取概念板块成份股
export async function getBoardConceptStocks(name) {
  const res = await pythonRequest.get('/stock/board-concept-stocks', { params: { name } })
  return res.data || res
}

// 获取个股分时数据
export async function getIntraday(code) {
  const res = await pythonRequest.get(`/stock/intraday/${code}`)
  return res.data || res
}

// 获取指数分时数据
export async function getIndexIntraday(code) {
  const res = await pythonRequest.get(`/index/intraday/${code}`)
  return res.data || res
}

// 获取个股资金流向
export async function getFundFlow(code) {
  const res = await pythonRequest.get('/stock/fund-flow', { params: { code } })
  return res.data || res
}

// 获取盘口数据（买卖五档）
export async function getBidAsk(code) {
  const res = await pythonRequest.get('/stock/bid-ask', { params: { code } })
  return res.data || res
}

// 获取北向资金流向
export async function getNorthFlow() {
  const res = await pythonRequest.get('/stock/north-flow')
  return res.data || res
}
