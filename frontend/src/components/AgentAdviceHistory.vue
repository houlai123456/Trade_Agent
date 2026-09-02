<template>
  <el-card class="advice-history-card">
    <template #header>
      <div class="advice-header">
        <div class="advice-title">
          <span>AI建议历史</span>
          <el-tag v-if="accuracyStats" type="success" size="small" effect="plain">
            历史准确率: {{ accuracyStats.winRate }}%
          </el-tag>
        </div>
        <el-button size="small" @click="loadHistory" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </template>

    <div v-if="loading && !advices.length" v-loading="true" style="height: 100px"></div>

    <div v-if="!loading && !advices.length" class="empty-state">
      <el-empty description="暂无AI建议记录" :image-size="80" />
    </div>

    <div v-if="accuracyStats && advices.length > 0" class="accuracy-banner">
      <div class="accuracy-item">
        <span class="accuracy-label">总建议数</span>
        <span class="accuracy-value">{{ accuracyStats.total }}</span>
      </div>
      <div class="accuracy-item">
        <span class="accuracy-label">准确建议</span>
        <span class="accuracy-value success">{{ accuracyStats.correct }}</span>
      </div>
      <div class="accuracy-item">
        <span class="accuracy-label">准确率</span>
        <span class="accuracy-value highlight">{{ accuracyStats.winRate }}%</span>
      </div>
      <div class="accuracy-item">
        <span class="accuracy-label">平均收益</span>
        <span class="accuracy-value" :class="accuracyStats.avgReturn >= 0 ? 'profit' : 'loss'">
          {{ accuracyStats.avgReturn >= 0 ? '+' : '' }}{{ accuracyStats.avgReturn }}%
        </span>
      </div>
    </div>

    <div v-if="advices.length > 0" class="advice-list">
      <div
        v-for="advice in advices"
        :key="advice.id"
        class="advice-item"
        :class="{ 'triggered': advice.status === 'TRIGGERED' }"
      >
        <div class="advice-main">
          <div class="advice-action">
            <el-tag
              :type="getActionType(advice.adviceType)"
              size="large"
              effect="dark"
            >
              {{ formatActionType(advice.adviceType) }}
            </el-tag>
          </div>
          <div class="advice-content">
            <div class="advice-row">
              <span class="advice-label">建议价格</span>
              <span class="advice-price">{{ advice.advicePrice?.toFixed(2) }} 元</span>
            </div>
            <div class="advice-row" v-if="advice.targetPrice">
              <span class="advice-label">目标价</span>
              <span class="advice-target">{{ advice.targetPrice?.toFixed(2) }} 元</span>
            </div>
            <div class="advice-row" v-if="advice.stopLossPrice">
              <span class="advice-label">止损价</span>
              <span class="advice-stoploss">{{ advice.stopLossPrice?.toFixed(2) }} 元</span>
            </div>
            <div class="advice-row">
              <span class="advice-label">置信度</span>
              <el-progress
                :percentage="advice.confidenceScore"
                :color="getConfidenceColor(advice.confidenceScore)"
                :stroke-width="8"
                :show-text="true"
                style="width: 120px"
              />
            </div>
          </div>
          <div class="advice-status">
            <el-tag
              :type="getStatusType(advice.status)"
              size="small"
              effect="plain"
            >
              {{ formatStatus(advice.status) }}
            </el-tag>
            <div class="advice-time">{{ formatTime(advice.createTime) }}</div>
          </div>
        </div>

        <div v-if="advice.status === 'TRIGGERED'" class="advice-alert">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
          >
            <template #title>
              <span class="alert-title">风险触发</span>
            </template>
            <div class="alert-content">
              {{ advice.triggeredCondition }}
              <el-button
                link
                type="primary"
                size="small"
                @click="showMonitorDetails(advice.id)"
              >
                查看监控详情
              </el-button>
            </div>
          </el-alert>
        </div>

        <div v-if="advice.review30dCorrect !== null" class="advice-review">
          <div class="review-label">30日复盘</div>
          <div class="review-result">
            <el-tag
              :type="advice.review30dCorrect ? 'success' : 'danger'"
              size="small"
            >
              {{ advice.review30dCorrect ? '✓ 准确' : '✗ 失误' }}
            </el-tag>
            <span class="review-return" :class="advice.review30dReturn >= 0 ? 'profit' : 'loss'">
              收益率: {{ advice.review30dReturn >= 0 ? '+' : '' }}{{ advice.review30dReturn?.toFixed(2) }}%
            </span>
          </div>
        </div>

        <div v-if="advice.status === 'ACTIVE'" class="advice-actions">
          <el-button
            size="small"
            type="warning"
            plain
            @click="handleCloseAdvice(advice.id)"
          >
            停止监控
          </el-button>
        </div>
      </div>
    </div>

    <!-- 监控详情对话框 -->
    <el-dialog
      v-model="monitorDialogVisible"
      title="监控详情"
      width="800px"
    >
      <div v-loading="monitorLoading">
        <el-timeline v-if="monitorLogs.length > 0">
          <el-timeline-item
            v-for="log in monitorLogs"
            :key="log.id"
            :timestamp="log.checkDate"
            placement="top"
            :type="log.riskTriggered ? 'danger' : 'success'"
          >
            <el-card shadow="hover">
              <div class="monitor-log-item">
                <div class="monitor-row">
                  <span class="monitor-label">当日价格</span>
                  <span class="monitor-value">{{ log.currentPrice?.toFixed(2) }} 元</span>
                </div>
                <div class="monitor-row">
                  <span class="monitor-label">MA5</span>
                  <span class="monitor-value">{{ log.ma5?.toFixed(2) }}</span>
                </div>
                <div class="monitor-row">
                  <span class="monitor-label">MA20</span>
                  <span class="monitor-value">{{ log.ma20?.toFixed(2) }}</span>
                </div>
                <div class="monitor-row">
                  <span class="monitor-label">风险状态</span>
                  <el-tag :type="log.riskTriggered ? 'danger' : 'success'" size="small">
                    {{ log.riskTriggered ? '⚠️ 触发' : '✓ 正常' }}
                  </el-tag>
                </div>
                <div v-if="log.riskTriggered && log.triggeredRules" class="triggered-rules">
                  <div class="rules-title">触发条件:</div>
                  <div v-for="(rule, idx) in parseRules(log.triggeredRules)" :key="idx" class="rule-item">
                    <el-tag type="warning" size="small" effect="plain">
                      {{ rule.description }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无监控记录" :image-size="60" />
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdviceHistory, getAdviceMonitor, closeAdvice, getAccuracyStats } from '../api/advice'

const props = defineProps({
  stockCode: {
    type: String,
    required: true
  }
})

const loading = ref(false)
const advices = ref([])
const accuracyStats = ref(null)
const monitorDialogVisible = ref(false)
const monitorLoading = ref(false)
const monitorLogs = ref([])

onMounted(() => {
  loadHistory()
  loadAccuracyStats()
})

async function loadHistory() {
  loading.value = true
  try {
    const res = await getAdviceHistory(props.stockCode)
    advices.value = res.advices || []
  } catch (e) {
    console.error('加载建议历史失败', e)
    ElMessage.error('加载建议历史失败')
  } finally {
    loading.value = false
  }
}

async function loadAccuracyStats() {
  try {
    const res = await getAccuracyStats(props.stockCode)
    if (res.total > 0) {
      accuracyStats.value = res
    }
  } catch (e) {
    console.error('加载准确率失败', e)
  }
}

async function showMonitorDetails(adviceId) {
  monitorDialogVisible.value = true
  monitorLoading.value = true
  monitorLogs.value = []
  try {
    const res = await getAdviceMonitor(adviceId)
    monitorLogs.value = res.logs || []
  } catch (e) {
    console.error('加载监控详情失败', e)
    ElMessage.error('加载监控详情失败')
  } finally {
    monitorLoading.value = false
  }
}

async function handleCloseAdvice(adviceId) {
  try {
    await ElMessageBox.confirm(
      '确认停止该建议的风险监控？停止后将不再发送风险提醒。',
      '停止监控',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
    await closeAdvice(adviceId)
    ElMessage.success('已停止监控')
    loadHistory()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('停止监控失败', e)
      ElMessage.error('停止监控失败')
    }
  }
}

function parseRules(json) {
  try {
    return JSON.parse(json)
  } catch (e) {
    return []
  }
}

function getActionType(type) {
  const map = { 'BUY': 'success', 'SELL': 'danger', 'HOLD': 'info' }
  return map[type] || 'info'
}

function formatActionType(type) {
  const map = { 'BUY': '买入', 'SELL': '卖出', 'HOLD': '观望' }
  return map[type] || type
}

function getStatusType(status) {
  const map = {
    'ACTIVE': 'success',
    'TRIGGERED': 'warning',
    'CLOSED': 'info',
    'EXPIRED': 'info'
  }
  return map[status] || 'info'
}

function formatStatus(status) {
  const map = {
    'ACTIVE': '监控中',
    'TRIGGERED': '已触发',
    'CLOSED': '已关闭',
    'EXPIRED': '已过期'
  }
  return map[status] || status
}

function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function getConfidenceColor(score) {
  if (score >= 75) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}
</script>

<style scoped>
.advice-history-card {
  margin-top: 16px;
}

.advice-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.advice-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
}

.empty-state {
  padding: 20px 0;
}

.accuracy-banner {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  margin-bottom: 20px;
}

.accuracy-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.accuracy-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  font-weight: 500;
}

.accuracy-value {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
}

.accuracy-value.success {
  color: #a8f5d5;
}

.accuracy-value.highlight {
  color: #ffd700;
}

.accuracy-value.profit {
  color: #a8f5d5;
}

.accuracy-value.loss {
  color: #ffb3ba;
}

.advice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.advice-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
  transition: all 0.3s;
}

.advice-item:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.advice-item.triggered {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.advice-main {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.advice-action {
  flex-shrink: 0;
}

.advice-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.advice-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.advice-label {
  font-size: 13px;
  color: #909399;
  width: 80px;
}

.advice-price {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
  font-family: monospace;
}

.advice-target {
  font-size: 14px;
  font-weight: 600;
  color: #67c23a;
  font-family: monospace;
}

.advice-stoploss {
  font-size: 14px;
  font-weight: 600;
  color: #f56c6c;
  font-family: monospace;
}

.advice-status {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.advice-time {
  font-size: 12px;
  color: #909399;
}

.advice-alert {
  margin-top: 12px;
}

.alert-title {
  font-weight: 600;
}

.alert-content {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.advice-review {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-label {
  font-size: 13px;
  color: #606266;
  font-weight: 600;
}

.review-result {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-return {
  font-size: 14px;
  font-weight: 600;
  font-family: monospace;
}

.review-return.profit {
  color: #67c23a;
}

.review-return.loss {
  color: #f56c6c;
}

.advice-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.monitor-log-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.monitor-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.monitor-label {
  font-size: 13px;
  color: #909399;
}

.monitor-value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  font-family: monospace;
}

.triggered-rules {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}

.rules-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.rule-item {
  margin-bottom: 4px;
}
</style>
