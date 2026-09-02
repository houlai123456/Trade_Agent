<template>
  <div class="active-advices-page">
    <el-card class="header-card">
      <template #header>
        <div class="page-header">
          <div class="header-title">
            <h2>活跃监控建议</h2>
            <el-tag type="success" size="large">{{ advices.length }} 条监控中</el-tag>
          </div>
          <el-button type="primary" @click="loadAdvices" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <div class="filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索股票代码或名称"
          clearable
          style="width: 300px"
          @input="filterAdvices"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="filterType" placeholder="建议类型" clearable style="width: 150px" @change="filterAdvices">
          <el-option label="全部" value="" />
          <el-option label="买入" value="BUY" />
          <el-option label="卖出" value="SELL" />
          <el-option label="观望" value="HOLD" />
        </el-select>
      </div>
    </el-card>

    <div v-loading="loading" style="min-height: 200px">
      <el-empty v-if="!loading && filteredAdvices.length === 0" description="暂无活跃监控" :image-size="100" />

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col
          v-for="advice in filteredAdvices"
          :key="advice.id"
          :xs="24"
          :sm="12"
          :lg="8"
          style="margin-bottom: 16px"
        >
          <el-card class="advice-card" shadow="hover">
            <div class="advice-header">
              <div class="stock-info">
                <router-link :to="`/stock/${advice.stockCode}`" class="stock-link">
                  <h3>{{ advice.stockName }}</h3>
                  <span class="stock-code">{{ advice.stockCode }}</span>
                </router-link>
              </div>
              <el-tag
                :type="getActionType(advice.adviceType)"
                size="large"
                effect="dark"
              >
                {{ formatActionType(advice.adviceType) }}
              </el-tag>
            </div>

            <div class="advice-details">
              <div class="detail-row">
                <span class="label">建议价格</span>
                <span class="value price">{{ advice.advicePrice?.toFixed(2) }} 元</span>
              </div>
              <div class="detail-row" v-if="advice.targetPrice">
                <span class="label">目标价</span>
                <span class="value target">{{ advice.targetPrice?.toFixed(2) }} 元</span>
              </div>
              <div class="detail-row" v-if="advice.stopLossPrice">
                <span class="label">止损价</span>
                <span class="value stoploss">{{ advice.stopLossPrice?.toFixed(2) }} 元</span>
              </div>
              <div class="detail-row">
                <span class="label">预期收益</span>
                <span class="value" :class="advice.expectedReturn >= 0 ? 'profit' : 'loss'">
                  {{ advice.expectedReturn >= 0 ? '+' : '' }}{{ advice.expectedReturn?.toFixed(2) }}%
                </span>
              </div>
              <div class="detail-row">
                <span class="label">置信度</span>
                <el-progress
                  :percentage="advice.confidenceScore"
                  :color="getConfidenceColor(advice.confidenceScore)"
                  :stroke-width="6"
                  style="width: 100px"
                />
              </div>
              <div class="detail-row">
                <span class="label">创建时间</span>
                <span class="value time">{{ formatTime(advice.createTime) }}</span>
              </div>
            </div>

            <div class="advice-footer">
              <el-button
                size="small"
                type="primary"
                plain
                @click="viewMonitor(advice)"
              >
                查看监控
              </el-button>
              <el-button
                size="small"
                type="warning"
                plain
                @click="handleCloseAdvice(advice.id)"
              >
                停止监控
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 监控详情对话框 -->
    <el-dialog
      v-model="monitorDialogVisible"
      :title="`${currentAdvice?.stockName} (${currentAdvice?.stockCode}) - 监控详情`"
      width="900px"
    >
      <div v-loading="monitorLoading">
        <div v-if="currentAdvice" class="monitor-summary">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="建议类型">
              <el-tag :type="getActionType(currentAdvice.adviceType)" size="small">
                {{ formatActionType(currentAdvice.adviceType) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="建议价格">
              {{ currentAdvice.advicePrice?.toFixed(2) }} 元
            </el-descriptions-item>
            <el-descriptions-item label="目标价">
              {{ currentAdvice.targetPrice?.toFixed(2) }} 元
            </el-descriptions-item>
            <el-descriptions-item label="止损价">
              {{ currentAdvice.stopLossPrice?.toFixed(2) }} 元
            </el-descriptions-item>
            <el-descriptions-item label="置信度">
              {{ currentAdvice.confidenceScore }}%
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatTime(currentAdvice.createTime) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <el-divider>监控记录</el-divider>

        <el-timeline v-if="monitorLogs.length > 0">
          <el-timeline-item
            v-for="log in monitorLogs"
            :key="log.id"
            :timestamp="log.checkDate"
            placement="top"
            :type="log.riskTriggered ? 'danger' : 'success'"
            :hollow="!log.riskTriggered"
          >
            <el-card shadow="hover" :class="{ 'risk-triggered': log.riskTriggered }">
              <div class="monitor-log">
                <div class="log-header">
                  <el-tag :type="log.riskTriggered ? 'danger' : 'success'" size="small">
                    {{ log.riskTriggered ? '⚠️ 风险触发' : '✓ 正常' }}
                  </el-tag>
                  <el-tag type="info" size="small" effect="plain">
                    风险等级: {{ formatRiskLevel(log.riskLevel) }}
                  </el-tag>
                </div>
                <div class="log-data">
                  <div class="data-row">
                    <span class="data-label">当日价格</span>
                    <span class="data-value">{{ log.currentPrice?.toFixed(2) }} 元</span>
                  </div>
                  <div class="data-row">
                    <span class="data-label">MA5</span>
                    <span class="data-value">{{ log.ma5?.toFixed(2) }}</span>
                  </div>
                  <div class="data-row">
                    <span class="data-label">MA20</span>
                    <span class="data-value">{{ log.ma20?.toFixed(2) }}</span>
                  </div>
                  <div class="data-row">
                    <span class="data-label">成交量</span>
                    <span class="data-value">{{ formatVolume(log.volume) }}</span>
                  </div>
                </div>
                <div v-if="log.riskTriggered && log.triggeredRules" class="triggered-rules">
                  <div class="rules-title">触发的风险条件:</div>
                  <div v-for="(rule, idx) in parseRules(log.triggeredRules)" :key="idx" class="rule-item">
                    <el-tag type="warning" size="small" effect="plain">
                      {{ rule.type }}: {{ rule.description }}
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
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getActiveAdvices, getAdviceMonitor, closeAdvice } from '../api/advice'

const loading = ref(false)
const advices = ref([])
const searchKeyword = ref('')
const filterType = ref('')
const monitorDialogVisible = ref(false)
const monitorLoading = ref(false)
const monitorLogs = ref([])
const currentAdvice = ref(null)

const filteredAdvices = computed(() => {
  let result = advices.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    result = result.filter(a =>
      a.stockCode.toLowerCase().includes(kw) ||
      a.stockName.toLowerCase().includes(kw)
    )
  }
  if (filterType.value) {
    result = result.filter(a => a.adviceType === filterType.value)
  }
  return result
})

onMounted(() => {
  loadAdvices()
})

async function loadAdvices() {
  loading.value = true
  try {
    const res = await getActiveAdvices()
    advices.value = res.advices || []
  } catch (e) {
    console.error('加载活跃建议失败', e)
    ElMessage.error('加载活跃建议失败')
  } finally {
    loading.value = false
  }
}

function filterAdvices() {
  // filteredAdvices 是计算属性，会自动更新
}

async function viewMonitor(advice) {
  currentAdvice.value = advice
  monitorDialogVisible.value = true
  monitorLoading.value = true
  monitorLogs.value = []
  try {
    const res = await getAdviceMonitor(advice.id)
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
    loadAdvices()
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

function formatTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatRiskLevel(level) {
  const map = {
    'CRITICAL': '🔴 严重',
    'HIGH': '🟠 高',
    'MEDIUM': '🟡 中',
    'LOW': '🟢 低'
  }
  return map[level] || level
}

function formatVolume(v) {
  if (!v) return '-'
  if (v >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (v >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toString()
}

function getConfidenceColor(score) {
  if (score >= 75) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}
</script>

<style scoped>
.active-advices-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-title h2 {
  margin: 0;
  font-size: 24px;
  color: #303133;
}

.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.advice-card {
  height: 100%;
  transition: all 0.3s;
}

.advice-card:hover {
  transform: translateY(-4px);
}

.advice-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.stock-info {
  flex: 1;
}

.stock-link {
  text-decoration: none;
  color: inherit;
}

.stock-link:hover h3 {
  color: #409eff;
}

.stock-info h3 {
  margin: 0 0 4px 0;
  font-size: 18px;
  color: #303133;
  transition: color 0.3s;
}

.stock-code {
  font-size: 13px;
  color: #909399;
  font-family: monospace;
}

.advice-details {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-row .label {
  font-size: 13px;
  color: #909399;
}

.detail-row .value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.value.price {
  font-family: monospace;
  font-size: 16px;
}

.value.target {
  color: #67c23a;
  font-family: monospace;
}

.value.stoploss {
  color: #f56c6c;
  font-family: monospace;
}

.value.profit {
  color: #67c23a;
}

.value.loss {
  color: #f56c6c;
}

.value.time {
  font-size: 12px;
  color: #909399;
}

.advice-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.monitor-summary {
  margin-bottom: 20px;
}

.monitor-log {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-header {
  display: flex;
  gap: 8px;
  align-items: center;
}

.log-data {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.data-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.data-label {
  font-size: 13px;
  color: #909399;
}

.data-value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  font-family: monospace;
}

.triggered-rules {
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.rules-title {
  font-size: 13px;
  color: #606266;
  font-weight: 600;
  margin-bottom: 8px;
}

.rule-item {
  margin-bottom: 6px;
}

.risk-triggered {
  border: 1px solid #e6a23c;
  background: #fdf6ec;
}
</style>
