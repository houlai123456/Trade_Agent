import request from './request'
import axios from 'axios'

// RAG 专用请求实例（超时60秒，需要等待LLM回复）
const ragRequest = axios.create({ baseURL: '/api', timeout: 60000 })
ragRequest.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('RAG请求失败:', error.message)
    return Promise.reject(error)
  }
)

// RAG 智能问答（基于新闻的检索增强生成）
export async function ragChat(question) {
  const res = await ragRequest.post('/rag/ask', { question })
  return res.data || res
}

// AI对话（一次性返回，调用Java后端）
export function chat(message, stockCode = null) {
  return request.post('/ai/chat', { message, stockCode })
}

// AI对话（SSE流式 - 通过EventSource实现，这里导出配置用于组件内自定义）
export const AI_API = {
  chatStream: '/api/ai/chat/stream',
}

// 股票分析（Python RAG 财务解读）
export function analyzeStock(code, name = '') {
  return request.post('/rag/finance-analysis', { code, name })
}

// 股票分析（Java 后端，旧版）
export function analyzeStockJava(code) {
  return request.get(`/ai/analyze/${code}`)
}

// 多Agent协同分析
export function agentCollaborate(stockCode, newsTitle = '', newsContent = '') {
  return request.post('/agent/collaborate', { stockCode, newsTitle, newsContent })
}

// ReAct Agent 自主分析
export function reactAgent(message) {
  return request.post('/agent/react', { message }, { timeout: 60000 })
}

// 智能分析（自动选模式：Plan-and-Execute 或 ReAct）
export function smartAnalyze(message) {
  return request.post('/agent/analyze', { message }, { timeout: 300000 })
}
