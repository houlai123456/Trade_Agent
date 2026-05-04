<template>
  <div class="alert-list">
    <el-card>
      <template #header>
        <div class="alert-header">
          <h3>异动预警</h3>
          <div class="alert-actions">
            <el-button
              :icon="Check"
              @click="markAllRead"
              :disabled="unreadCount === 0"
            >
              全部已读
            </el-button>
            <el-button type="primary" @click="manualCheck" :loading="checking">
              立即检测
            </el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="alert-container">
        <div v-for="item in alertList" :key="item.id" :class="['alert-item', { 'alert-unread': item.readFlag === 0 }]">
          <div class="alert-left">
            <div class="alert-icon" :class="item.alertType === 'PRICE' ? 'price-alert' : 'volume-alert'">
              <el-icon v-if="item.alertType === 'PRICE'"><WarningFilled /></el-icon>
              <el-icon v-else><TrendCharts /></el-icon>
            </div>
          </div>
          <div class="alert-content">
            <div class="alert-title">
              <span class="alert-type-tag">
                {{ item.alertType === 'PRICE' ? '涨跌幅异动' : '成交量异动' }}
              </span>
              <span class="alert-name">{{ item.name }} ({{ item.code }})</span>
              <span class="alert-time">{{ formatTime(item.createTime) }}</span>
            </div>
            <div class="alert-desc">{{ item.description }}</div>
            <div class="alert-data" v-if="item.changePercent">
              涨跌幅：<span :style="{ color: item.changePercent >= 0 ? '#ef5350' : '#26a69a' }">
                {{ item.changePercent >= 0 ? '+' : '' }}{{ item.changePercent?.toFixed(2) }}%
              </span>
              价格：{{ item.currentPrice?.toFixed(2) }}
            </div>
          </div>
          <div class="alert-right">
            <el-button text type="primary" size="small" @click="goToStock(item.code)">
              查看
            </el-button>
          </div>
        </div>
        <el-empty v-if="alertList.length === 0" description="暂无预警" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { WarningFilled, TrendCharts, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getRecentAlerts, markAlertRead, checkAlerts, getUnreadAlertCount } from '../api/alert'
import { useAlertStore } from '../stores/alert'

const router = useRouter()
const alertStore = useAlertStore()

const alertList = ref([])
const loading = ref(false)
const checking = ref(false)
const unreadCount = ref(0)

onMounted(() => {
  loadAlerts()
})

// WebSocket推送的预警实时追加
watch(() => alertStore.latestAlerts.length, () => {
  const storeAlerts = alertStore.latestAlerts
  if (storeAlerts.length > 0) {
    // 把store中的新预警合并到当前列表（去重）
    const existingIds = new Set(alertList.value.map(a => a.id))
    for (const a of storeAlerts) {
      if (!existingIds.has(a.id)) {
        alertList.value.unshift(a)
        existingIds.add(a.id)
      }
    }
  }
})

async function loadAlerts() {
  loading.value = true
  try {
    alertList.value = await getRecentAlerts(50)
  } catch (e) {
    console.error('加载预警失败', e)
  } finally {
    loading.value = false
  }
}

async function markAllRead() {
  for (const item of alertList.value) {
    if (item.readFlag === 0) {
      await markAlertRead(item.id)
      item.readFlag = 1
    }
  }
  alertStore.markAllRead()
  unreadCount.value = 0
  ElMessage.success('已全部标记为已读')
}

async function manualCheck() {
  checking.value = true
  try {
    const alerts = await checkAlerts()
    if (alerts.length > 0) {
      ElMessage.info(`发现 ${alerts.length} 条新预警`)
      await loadAlerts()
    } else {
      ElMessage.success('未发现异动')
    }
  } catch (e) {
    console.error(e)
  } finally {
    checking.value = false
  }
}

function goToStock(code) {
  router.push(`/stock/${code}`)
}

function formatTime(time) {
  if (!time) return ''
  return time.substring(0, 10) + ' ' + time.substring(11, 16)
}
</script>

<style scoped>
.alert-list {
  max-width: 1000px;
  margin: 0 auto;
}
.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.alert-header h3 {
  margin: 0;
}
.alert-actions {
  display: flex;
  gap: 8px;
}
.alert-container {
  min-height: 200px;
}
.alert-item {
  display: flex;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
  align-items: flex-start;
}
.alert-item:last-child {
  border-bottom: none;
}
.alert-unread {
  background: #f0f9ff;
  margin: 0 -16px;
  padding: 16px;
  border-radius: 6px;
}
.alert-left {
  flex-shrink: 0;
}
.alert-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
.price-alert {
  background: #fef0f0;
  color: #ef5350;
}
.volume-alert {
  background: #fdf6ec;
  color: #e6a23c;
}
.alert-content {
  flex: 1;
}
.alert-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.alert-type-tag {
  font-size: 12px;
  background: #ecf5ff;
  color: #409eff;
  padding: 2px 8px;
  border-radius: 4px;
}
.alert-name {
  font-weight: 600;
  color: #303133;
}
.alert-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
}
.alert-desc {
  font-size: 14px;
  color: #606266;
  margin-bottom: 4px;
}
.alert-data {
  font-size: 13px;
  color: #909399;
}
.alert-right {
  flex-shrink: 0;
}
</style>
