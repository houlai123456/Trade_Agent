<template>
  <el-card class="card" style="margin-top: 16px">
    <template #header>
      <div class="agent-header">
        <span>AI 分析</span>
        <el-button type="primary" size="small" @click="run" :loading="loading" :disabled="!!result">
          {{ result ? '重新分析' : '一键分析' }}
        </el-button>
      </div>
    </template>

    <div v-if="!result && !loading" class="agent-placeholder">
      <el-icon :size="32" color="#c0c4cc"><Monitor /></el-icon>
      <span>点击"一键分析"，AI 将从舆情、技术面、资金面综合分析</span>
    </div>

    <div v-if="loading" class="collaborating">
      <el-steps direction="vertical" :active="stepIndex" finish-status="success" process-status="process" class="agent-steps">
        <el-step v-for="(s, i) in steps" :key="i" :title="s.label" :description="s.desc" />
      </el-steps>
    </div>

    <div v-if="result" class="collaboration-result">
      <div class="suggestion-card" :class="suggestionClass">
        <div class="suggestion-main">
          <el-tag :type="suggestionTagType" size="large" effect="dark" class="suggestion-tag">
            {{ result.suggestion.actionLabel || result.suggestion.action }}
          </el-tag>
          <span class="suggestion-confidence" :class="confidenceClass">{{ confidenceLabel }}</span>
          <span class="suggestion-duration" v-if="result.totalDurationMs">耗时 {{ (result.totalDurationMs / 1000).toFixed(1) }}s</span>
        </div>
        <div class="suggestion-summary" v-if="result.suggestion.suggestionSummary">{{ result.suggestion.suggestionSummary }}</div>
        <div class="suggestion-reason" v-if="result.suggestion.reason">
          <div class="reason-label">分析理由</div>
          <div class="reason-content" v-html="renderMarkdown(result.suggestion.reason)"></div>
        </div>
        <div class="suggestion-risk" v-if="result.suggestion.riskWarning">
          <span class="risk-label">风险提示</span>{{ result.suggestion.riskWarning }}
        </div>
      </div>

      <el-collapse v-model="openPanels" class="step-collapse">
        <el-collapse-item v-for="(step, i) in result.steps" :key="i"
          :title="`${step.agentName} — ${step.outputSummary || step.description}`" :name="i">
          <div class="step-detail">
            <div class="step-row"><label>描述</label><span>{{ step.description }}</span></div>
            <div class="step-row"><label>状态</label><el-tag :type="step.status === 'completed' ? 'success' : 'info'" size="small">{{ step.status }}</el-tag></div>
            <div class="step-row" v-if="step.durationMs"><label>耗时</label><span>{{ step.durationMs }}ms</span></div>
            <div class="step-row" v-if="step.inputSummary"><label>输入</label><span>{{ step.inputSummary }}</span></div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Monitor } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { agentCollaborate } from '../api/ai'

const props = defineProps({
  stockCode: { type: String, required: true },
})

const loading = ref(false)
const result = ref(null)
const stepIndex = ref(0)
const openPanels = ref([])
const steps = [
  { label: '新闻舆情分析', desc: '分析新闻情绪倾向（利好/利空/中性）' },
  { label: '市场技术分析', desc: '分析K线趋势、均线位置、量能变化' },
  { label: '综合交易建议', desc: '生成操作建议' },
]

async function run() {
  loading.value = true
  result.value = null
  stepIndex.value = 0
  openPanels.value = []

  const timer = setInterval(() => {
    if (stepIndex.value < steps.length) stepIndex.value++
    else clearInterval(timer)
  }, 1800)

  try {
    const res = await agentCollaborate(props.stockCode)
    result.value = res.data || res
    openPanels.value = result.value.steps.map((_, i) => i)
  } catch (e) {
    ElMessage.error('AI分析失败')
    result.value = null
  } finally {
    clearInterval(timer)
    stepIndex.value = steps.length
    loading.value = false
  }
}

const suggestionTagType = computed(() => {
  const a = result.value?.suggestion?.action
  if (a === 'BUY') return 'danger'
  if (a === 'SELL') return 'success'
  return 'info'
})

const suggestionClass = computed(() => {
  const a = result.value?.suggestion?.action
  if (a === 'BUY') return 'suggestion-buy'
  if (a === 'SELL') return 'suggestion-sell'
  return 'suggestion-hold'
})

const confidenceClass = computed(() => {
  const c = result.value?.suggestion?.confidence
  if (c === 'HIGH') return 'confidence-high'
  if (c === 'MEDIUM') return 'confidence-medium'
  return 'confidence-low'
})

const confidenceLabel = computed(() => {
  const c = result.value?.suggestion?.confidence
  if (c === 'HIGH') return '高置信度'
  if (c === 'MEDIUM') return '中置信度'
  return '低置信度'
})

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
.agent-header { display: flex; justify-content: space-between; align-items: center; }
.agent-placeholder { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 32px 0; color: #c0c4cc; font-size: 14px; }
.collaborating { padding: 8px 0; }
.agent-steps { max-width: 400px; margin: 0 auto; }
.suggestion-card { border-radius: 8px; padding: 16px; margin-bottom: 12px; }
.suggestion-buy { background: #fff1f0; border: 1px solid #ffccc7; }
.suggestion-sell { background: #f0fff0; border: 1px solid #b7eb8f; }
.suggestion-hold { background: #fffbe6; border: 1px solid #ffe58f; }
.suggestion-main { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.suggestion-tag { font-size: 16px !important; font-weight: 700; padding: 6px 16px !important; }
.suggestion-confidence { font-size: 13px; font-weight: 600; }
.confidence-high { color: #ef5350; }
.confidence-medium { color: #faad14; }
.confidence-low { color: #909399; }
.suggestion-duration { margin-left: auto; font-size: 12px; color: #909399; }
.suggestion-summary { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 12px; }
.suggestion-reason { background: #fff; border-radius: 6px; padding: 12px; margin-bottom: 8px; }
.reason-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.reason-content { font-size: 14px; line-height: 1.7; color: #303133; }
.suggestion-risk { font-size: 12px; color: #faad14; background: #fffbe6; border-radius: 4px; padding: 6px 10px; }
.risk-label { font-weight: 700; margin-right: 4px; }
.step-collapse { margin-top: 8px; }
.step-detail { font-size: 13px; }
.step-row { display: flex; gap: 8px; padding: 3px 0; }
.step-row label { color: #909399; min-width: 36px; flex-shrink: 0; }
</style>
