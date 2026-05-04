<template>
  <div class="chat-window">
    <!-- header -->
    <div class="chat-header">
      <span class="chat-title">智能投研助手</span>
      <div class="chat-header-actions">
        <el-tooltip content="上传文档" placement="top">
          <el-upload
            :action="uploadUrl"
            :show-file-list="false"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :before-upload="handleBeforeUpload"
            accept=".pdf,.txt"
          >
            <el-button text :icon="UploadFilled" />
          </el-upload>
        </el-tooltip>
        <el-tooltip content="清空对话" placement="top">
          <el-button text :icon="Delete" @click="clearChat" />
        </el-tooltip>
      </div>
    </div>

    <!-- messages -->
    <div class="chat-messages" ref="messagesRef">
      <div
        v-for="(msg, i) in messages"
        :key="i"
        :class="['message', msg.role === 'user' ? 'message-user' : 'message-assistant']"
      >
        <div class="message-avatar">
          {{ msg.role === 'user' ? '我' : 'AI' }}
        </div>
        <div class="message-content">
          <div class="message-text" v-if="msg.role === 'user'">{{ msg.content }}</div>
          <div class="message-text message-markdown" v-else-if="msg.type !== 'react-agent' && msg.type !== 'trade-confirm' && msg.type !== 'trade-processing' && msg.type !== 'trade-result' && msg.type !== 'trade-error' && msg.type !== 'trade-cancelled'" v-html="renderMarkdown(msg.content, msg.sources)"></div>

          <!-- ReAct Agent 分析结果 -->
          <div v-else-if="msg.type === 'react-agent'" class="message-text message-markdown" v-html="renderMarkdown(formatFinalAnswer(msg.finalAnswer))"></div>

          <!-- 交易确认卡片 -->
          <div v-else-if="msg.type === 'trade-confirm'" class="trade-confirm-card">
            <div class="trade-confirm-header">
              <el-tag :type="msg.intent.action === 'BUY' ? 'danger' : 'success'" size="small" effect="dark" class="trade-action-tag">
                {{ msg.intent.action === 'BUY' ? '买入' : '卖出' }}
              </el-tag>
              <span class="trade-confirm-title">确认交易指令</span>
            </div>
            <div class="trade-confirm-body">
              <div class="trade-confirm-row">
                <span class="trade-label">股票</span>
                <span class="trade-value">{{ msg.intent.stock_name }} <span class="trade-code">{{ msg.intent.stock_code }}</span></span>
              </div>
              <div class="trade-confirm-row">
                <span class="trade-label">数量</span>
                <span class="trade-value">{{ msg.intent.quantity }} 股</span>
              </div>
              <div class="trade-confirm-row">
                <span class="trade-label">预估金额</span>
                <span class="trade-value trade-price">¥{{ formatAmount(msg.intent.estimated_amount) }}</span>
              </div>
            </div>
            <div class="trade-confirm-actions">
              <el-button type="primary" size="small" :icon="Check" @click="confirmTrade(msg.intent, i)" :disabled="isProcessingTrade">
                {{ msg.intent.action === 'BUY' ? '确认买入' : '确认卖出' }}
              </el-button>
              <el-button size="small" :icon="Close" @click="cancelTrade(i)" :disabled="isProcessingTrade">取消</el-button>
            </div>
          </div>

          <!-- 交易处理中 -->
          <div v-else-if="msg.type === 'trade-processing'" class="trade-processing">
            <el-icon class="is-loading"><Promotion /></el-icon>
            <span>正在执行交易...</span>
          </div>

          <!-- 交易结果 -->
          <div v-else-if="msg.type === 'trade-result'" class="trade-result-card">
            <div class="trade-result-icon">✅</div>
            <div class="trade-result-content">
              <div class="trade-result-text">{{ msg.content }}</div>
              <div v-if="msg.result && msg.result.order" class="trade-result-detail">
                成交价：{{ msg.result.order.price }} 元 |
                金额：{{ msg.result.order.amount }} 元 |
                时间：{{ msg.result.order.trade_time || msg.result.order.create_time }}
              </div>
            </div>
          </div>

          <!-- 交易错误 -->
          <div v-else-if="msg.type === 'trade-error'" class="trade-error-card">
            <div class="trade-result-icon">❌</div>
            <div class="trade-result-content">
              <div class="trade-error-text">{{ msg.content }}</div>
            </div>
          </div>

          <!-- 交易已取消 -->
          <div v-else-if="msg.type === 'trade-cancelled'" class="trade-cancelled-text">
            {{ msg.content }}
          </div>
          <div v-if="msg.sources && msg.sources.length" class="message-sources">
            <el-tag
              v-for="(s, si) in msg.sources"
              :key="si"
              size="small"
              :type="s.score > 0.7 ? 'success' : s.score > 0.5 ? 'warning' : 'info'"
              class="source-tag"
            >
              {{ s.stock_name }} {{ (s.score * 100).toFixed(0) }}%
            </el-tag>
          </div>
          <div v-if="msg.role === 'assistant' && msg.content !== '思考中...'" class="message-actions">
            <el-tooltip content="复制" placement="top">
              <el-button text size="small" :icon="CopyDocument" @click="copyContent(msg.content)" />
            </el-tooltip>
          </div>
        </div>
      </div>
      <div v-if="loading" class="message message-assistant">
        <div class="message-avatar">AI</div>
        <div class="message-content">
          <div class="message-text thinking">
            <span class="dot-pulse"></span>
          </div>
        </div>
      </div>

      <!-- 新手引导：仅首次打开时显示 -->
      <div v-if="!hasUserMessage" class="chat-hints">
        <p class="hints-title">输入股票名称或问题开始对话</p>
        <div class="hints-tags">
          <el-tag @click="quickAsk('分析一下贵州茅台，给出买卖建议')" class="hint-tag">分析贵州茅台</el-tag>
          <el-tag @click="quickAsk('宁德时代走势分析')" class="hint-tag">宁德时代</el-tag>
          <el-tag @click="quickAsk('招商银行估值分析')" class="hint-tag">招商银行</el-tag>
          <el-tag @click="quickAsk('分析一下白酒板块走势')" class="hint-tag">白酒板块</el-tag>
        </div>
      </div>
    </div>

    <!-- toolbar -->
    <div v-if="uploadStatus" class="chat-toolbar" :class="uploadStatus.type">
      {{ uploadStatus.msg }}
    </div>

    <!-- input -->
    <div class="chat-input">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="1"
        :autosize="{ minRows: 1, maxRows: 4 }"
        placeholder="输入股票名称或问题，Enter 发送"
        @keydown.enter.exact.prevent="sendMessage"
      />
      <el-button type="primary" :loading="loading" @click="sendMessage" class="send-btn" :disabled="!inputText.trim() && !loading">
        <el-icon v-if="!loading"><Promotion /></el-icon>
        <span v-else>...</span>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, computed, onMounted } from 'vue'
import { UploadFilled, Delete, CopyDocument, Promotion, Check, Close } from '@element-plus/icons-vue'
import { marked } from 'marked'
import axios from 'axios'
import { parseTradeIntent, executeTrade } from '@/api/trade-agent.js'
import { reactAgent } from '@/api/ai'

// 安全渲染 markdown（只允许安全的 HTML 标签）
const renderer = new marked.Renderer()
renderer.link = ({ href, text }) => {
  return `<a href="${href}" target="_blank" rel="noopener noreferrer">${text}</a>`
}
marked.setOptions({
  renderer,
  breaks: true,
  gfm: true,
})

function renderMarkdown(text, sources) {
  if (!text) return ''
  try {
    let html = marked.parse(text)
    // 高亮有来源的股票名称
    if (sources && sources.length) {
      const names = [...new Set(sources.map(s => s.stock_name).filter(Boolean))]
      names.forEach(name => {
        // 只替换未被 HTML 标签包裹的股票名
        const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
        html = html.replace(
          new RegExp(`(?<!<[^>]*>)(?:${escaped})(?![^<]*>)`, 'g'),
          (match) => `<span class="citation-highlight" title="来源: ${name}">${match}</span>`
        )
      })
    }
    return html
  } catch {
    return text
  }
}

const messages = ref([
  { role: 'assistant', content: '你好！我是智能投研助手，可以查询股票新闻、分析财报数据。试试点击下方的快捷标签或直接提问。', sources: [] },
])
const hasUserMessage = computed(() => messages.value.some(m => m.role === 'user'))
const inputText = ref('')
const loading = ref(false)
const messagesRef = ref(null)
const uploadStatus = ref(null)
const isProcessingTrade = ref(false)

const uploadUrl = computed(() => '/api/rag/upload')

function quickAsk(text) {
  inputText.value = text
  sendMessage()
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value || isProcessingTrade.value) return

  loading.value = true
  const history = messages.value
    .filter(m => m.role !== 'assistant' || m.content !== '思考中...')
    .slice(-10)

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  scrollToBottom()

  // ===== 第一步：检查是否为交易指令 =====
  try {
    const parseRes = await parseTradeIntent(text)
    const intent = parseRes.data || parseRes

    if (intent && intent.trade) {
      messages.value.push({
        role: 'assistant',
        type: 'trade-confirm',
        intent: intent,
        content: '',
      })
      loading.value = false
      scrollToBottom()
      return // 不继续走RAG
    }
  } catch (e) {
    // 交易解析失败，正常走RAG
    console.debug('交易指令解析跳过:', e.message)
  }

  // ===== 1.5 检测是否为股票分析问题，走 ReAct Agent =====
  const stockPattern = /(分析|走势|行情|\d{6}[A-Za-z]?|资金流向|K线|技术面|基本面|买卖建议)/
  if (stockPattern.test(text)) {
    try {
      const res = await reactAgent(text)
      const data = res.data || res
      messages.value.push({
        role: 'assistant',
        content: data.finalAnswer || '',
      })

      loading.value = false
      scrollToBottom()
      return
    } catch (e) {
      console.debug('ReAct 分析失败，回退到 RAG:', e.message)
    }
  }

  // ===== 第二步：不是交易指令，走RAG对话 =====
  const msgIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '', sources: [] })

  try {
    const resp = await fetch('/api/rag/ask/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: text, history }),
    })

    if (!resp.ok || !resp.body) {
      throw new Error('Stream not available')
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.startsWith('data: ')) continue
        const payload = line.slice(6)
        if (payload.trim() === '') continue
        try {
          const evt = JSON.parse(payload)
          if (evt.type === 'answer') {
            messages.value[msgIdx].content += evt.content
            scrollToBottom()
          } else if (evt.type === 'sources') {
            messages.value[msgIdx].sources = evt.sources || []
          }
        } catch { /* skip malformed */ }
      }
    }
  } catch (e) {
    messages.value.pop()
    try {
      const res = await axios.post('/api/rag/ask', { question: text, history }, { timeout: 60000 })
      const data = res.data?.data || res.data
      const reply = data.answer || data.response || '无法获取回答'
      const sources = data.sources || []
      messages.value.push({ role: 'assistant', content: reply, sources })
    } catch (e2) {
      messages.value.push({ role: 'assistant', content: '抱歉，暂时无法获取回答，请稍后重试。', sources: [] })
    }
  } finally {
    if (messages.value[msgIdx] && !messages.value[msgIdx].content) {
      messages.value[msgIdx].content = '暂无回答'
    }
    loading.value = false
    scrollToBottom()
  }
}

/** 确认交易 */
async function confirmTrade(intent, msgIndex) {
  isProcessingTrade.value = true
  // 把确认卡片替换为"处理中..."
  messages.value[msgIndex] = {
    role: 'assistant',
    type: 'trade-processing',
    content: '',
  }

  try {
    const res = await executeTrade({
      action: intent.action,
      stock_code: intent.stock_code,
      stock_name: intent.stock_name,
      quantity: intent.quantity,
      price: intent.price,
      trade: true,
    })
    const data = res.data || res
    const msg = data.message || '交易执行成功'

    messages.value[msgIndex] = {
      role: 'assistant',
      type: 'trade-result',
      result: data,
      content: msg,
    }
  } catch (e) {
    const errMsg = e.response?.data?.message || e.message || '交易执行失败'
    messages.value[msgIndex] = {
      role: 'assistant',
      type: 'trade-error',
      content: errMsg,
    }
  } finally {
    isProcessingTrade.value = false
    scrollToBottom()
  }
}

/** 取消交易 */
function cancelTrade(msgIndex) {
  messages.value[msgIndex] = {
    role: 'assistant',
    content: '已取消交易指令',
    type: 'trade-cancelled',
  }
}

function clearChat() {
  messages.value = [
    { role: 'assistant', content: '你好！我是智能投研助手，可以查询股票新闻、分析财报数据。试试点击下方的快捷标签或直接提问。', sources: [] },
  ]
  uploadStatus.value = null
}

function copyContent(text) {
  navigator.clipboard.writeText(text).then(() => {
    // 简单反馈
  }).catch(() => {})
}

function formatAmount(val) {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatFinalAnswer(answer) {
  return answer || ''
}

function handleBeforeUpload(file) {
  uploadStatus.value = { type: 'info', msg: `上传中 ${file.name}...` }
  return true
}

function handleUploadSuccess(response) {
  if (response?.success) {
    uploadStatus.value = { type: 'success', msg: `${response.data.filename} 已导入，共 ${response.data.chunks} 条片段，可直接提问相关内容` }
  } else {
    uploadStatus.value = { type: 'error', msg: `上传失败: ${response?.error || '未知错误'}` }
  }
  setTimeout(() => { uploadStatus.value = null }, 6000)
}

function handleUploadError(err) {
  uploadStatus.value = { type: 'error', msg: `上传失败: ${err.message}` }
  setTimeout(() => { uploadStatus.value = null }, 6000)
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* header */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 12px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
}
.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.chat-header-actions {
  display: flex;
  gap: 4px;
}

/* messages */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 10px;
}

/* hints */
.chat-hints {
  text-align: center;
  padding: 24px 0 12px;
  animation: fadeIn 0.4s ease;
}
.hints-title {
  color: #909399;
  font-size: 14px;
  margin: 0 0 12px 0;
}
.hints-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.hint-tag {
  cursor: pointer;
  transition: transform 0.15s;
}
.hint-tag:hover {
  transform: scale(1.05);
}

/* messages */
.message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  animation: fadeIn 0.25s ease;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.message-user {
  flex-direction: row-reverse;
}
.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: bold;
  flex-shrink: 0;
}
.message-user .message-avatar {
  background: #409eff;
  color: #fff;
}
.message-assistant .message-avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
}
.message-content {
  max-width: 80%;
  min-width: 0;
}
.message-text {
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.65;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}
:deep(.message-markdown) {
  white-space: normal;
}
:deep(.message-markdown p) {
  margin: 0 0 8px 0;
}
:deep(.message-markdown p:last-child) {
  margin-bottom: 0;
}
:deep(.message-markdown code) {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Courier New', monospace;
}
:deep(.message-markdown pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0;
}
:deep(.message-markdown pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
:deep(.message-markdown ul), :deep(.message-markdown ol) {
  padding-left: 20px;
  margin: 6px 0;
}
:deep(.message-markdown li) {
  margin: 3px 0;
}
:deep(.message-markdown strong) {
  font-weight: 600;
}
:deep(.message-markdown a) {
  color: #409eff;
  text-decoration: none;
}
:deep(.message-markdown h1), :deep(.message-markdown h2), :deep(.message-markdown h3), :deep(.message-markdown h4) {
  margin: 12px 0 6px 0;
  font-weight: 600;
}
:deep(.citation-highlight) {
  background: linear-gradient(180deg, transparent 60%, rgba(64,158,255,0.2) 60%);
  cursor: help;
  border-bottom: 1px dashed #409eff;
  padding: 0 2px;
  font-weight: 500;
}
:deep(.message-markdown hr) {
  border: none;
  border-top: 1px solid #e0e0e0;
  margin: 12px 0;
}
.message-user .message-text {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.message-assistant .message-text {
  background: #fff;
  color: #303133;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

/* sources */
.message-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}
.source-tag {
  cursor: default;
}

/* message actions (hover) */
.message-actions {
  opacity: 0;
  transition: opacity 0.15s;
  margin-top: 4px;
  display: flex;
  gap: 2px;
}
.message-content:hover .message-actions {
  opacity: 1;
}

/* thinking animation */
.thinking {
  display: flex;
  align-items: center;
  min-height: 24px;
}
.dot-pulse {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: #909399;
  border-radius: 50%;
  animation: pulse 1.2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

/* toolbar */
.chat-toolbar {
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 6px;
  margin-bottom: 8px;
  text-align: center;
}
.chat-toolbar.info {
  background: #ecf5ff;
  color: #409eff;
}
.chat-toolbar.success {
  background: #f0f9eb;
  color: #67c23a;
}
.chat-toolbar.error {
  background: #fef0f0;
  color: #f56c6c;
}

/* input */
.chat-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.chat-input .el-textarea {
  flex: 1;
}
.send-btn {
  height: 36px;
  width: 52px;
  padding: 0;
}

/* ===== 交易确认卡片 ===== */
.trade-confirm-card {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  padding: 14px;
  min-width: 260px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.trade-confirm-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}
.trade-action-tag {
  font-weight: 600;
}
.trade-confirm-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.trade-confirm-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}
.trade-confirm-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
}
.trade-label {
  color: #909399;
}
.trade-value {
  color: #303133;
  font-weight: 500;
}
.trade-code {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}
.trade-price {
  color: #e6a23c;
  font-weight: 700;
  font-size: 15px;
}
.trade-confirm-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* 交易处理中 */
.trade-processing {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  color: #909399;
  font-size: 14px;
}

/* 交易结果 */
.trade-result-card {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  background: #f0f9eb;
  border-radius: 8px;
  border: 1px solid #e1f3d8;
}
.trade-result-icon {
  font-size: 20px;
  flex-shrink: 0;
  line-height: 1.4;
}
.trade-result-content {
  flex: 1;
}
.trade-result-text {
  color: #303133;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
}
.trade-result-detail {
  color: #909399;
  font-size: 12px;
}

/* 交易错误 */
.trade-error-card {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  background: #fef0f0;
  border-radius: 8px;
  border: 1px solid #fde2e2;
}
.trade-error-text {
  color: #f56c6c;
  font-size: 14px;
}

/* 已取消 */
.trade-cancelled-text {
  color: #909399;
  font-size: 13px;
  font-style: italic;
  padding: 4px 0;
}

</style>
