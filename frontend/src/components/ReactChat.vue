<template>
  <el-card class="card" style="margin-top: 16px">
    <template #header>
      <div class="react-header" @click="open = !open" style="cursor: pointer">
        <span>AI 对话助手</span>
        <div class="react-header-right">
          <span class="react-hint" v-if="!open">输入问题深度分析当前股票</span>
          <el-icon :class="['react-toggle', { open }]"><ArrowDown /></el-icon>
        </div>
      </div>
    </template>
    <div v-show="open" class="react-panel">
      <div class="react-messages" ref="msgRef">
        <div v-for="(msg, i) in messages" :key="i"
          :class="['react-msg', msg.role === 'user' ? 'react-msg-user' : 'react-msg-ai']">
          <div class="react-msg-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
          <div class="react-msg-content" v-html="msg.role === 'user' ? escapeHtml(msg.content) : renderMarkdown(msg.content)"></div>
        </div>
        <div v-if="loading" class="react-msg react-msg-ai">
          <div class="react-msg-avatar">AI</div>
          <div class="react-msg-content thinking">思考中<span class="dots"><span>.</span><span>.</span><span>.</span></span></div>
        </div>
      </div>
      <div class="react-input-row">
        <el-input v-model="input" placeholder="例如：分析这只股票的压力位和支撑位" size="default" :disabled="loading" @keyup.enter="send" />
        <el-button type="primary" :loading="loading" @click="send" :disabled="!input.trim()">发送</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { reactAgent } from '../api/ai'

const props = defineProps({
  stockCode: { type: String, required: true },
  stockName: { type: String, default: '' },
})

const open = ref(false)
const messages = ref([])
const input = ref('')
const loading = ref(false)
const msgRef = ref(null)

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  scroll()

  try {
    const name = props.stockName || props.stockCode
    const res = await reactAgent(`[当前股票: ${name}(${props.stockCode})] ${text}`)
    const data = res.data || res
    messages.value.push({ role: 'assistant', content: data.finalAnswer || '分析完成，但没有生成结论。' })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '抱歉，AI 分析出错了，请稍后重试。' })
  } finally {
    loading.value = false
    scroll()
  }
}

function scroll() {
  nextTick(() => {
    const el = msgRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function escapeHtml(text) {
  if (!text) return ''
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderMarkdown(text) {
  if (!text) return ''
  const escaped = escapeHtml(text)
  return escaped
    .replace(/### (.+)/g, '<h3>$1</h3>')
    .replace(/## (.+)/g, '<h2>$1</h2>')
    .replace(/# (.+)/g, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}
</script>

<style scoped>
.react-header { display: flex; justify-content: space-between; align-items: center; user-select: none; }
.react-header-right { display: flex; align-items: center; gap: 8px; }
.react-hint { font-size: 12px; color: #c0c4cc; font-weight: normal; }
.react-toggle { transition: transform 0.25s; font-size: 16px; }
.react-toggle.open { transform: rotate(180deg); }
.react-panel { display: flex; flex-direction: column; gap: 12px; }
.react-messages { max-height: 400px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; padding: 8px 0; }
.react-msg { display: flex; gap: 8px; max-width: 85%; }
.react-msg-user { align-self: flex-end; flex-direction: row-reverse; }
.react-msg-ai { align-self: flex-start; }
.react-msg-avatar { width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; flex-shrink: 0; }
.react-msg-user .react-msg-avatar { background: #409eff; color: #fff; }
.react-msg-ai .react-msg-avatar { background: #f0f2f5; color: #606266; }
.react-msg-content { padding: 8px 12px; border-radius: 8px; font-size: 14px; line-height: 1.6; }
.react-msg-user .react-msg-content { background: #409eff; color: #fff; }
.react-msg-ai .react-msg-content { background: #f5f7fa; color: #303133; }
.react-msg-content.thinking { color: #909399; }
.dots span { animation: dot-pulse 1.4s infinite; opacity: 0; }
.dots span:nth-child(2) { animation-delay: 0.2s; }
.dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-pulse { 0%, 60%, 100% { opacity: 0; } 30% { opacity: 1; } }
.react-input-row { display: flex; gap: 8px; }
</style>
