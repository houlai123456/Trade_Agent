<template>
  <div class="board-detail" v-loading="loading">
    <el-card class="board-info-card">
      <template #header>
        <div class="board-header">
          <el-button text @click="goBack" style="margin-right: 8px">&lt; 返回</el-button>
          <h2>{{ boardName }}</h2>
          <el-tag v-if="boardType === 'industry'" type="primary" size="small" style="margin-left: 8px">行业板块</el-tag>
          <el-tag v-else type="warning" size="small" style="margin-left: 8px">概念板块</el-tag>
        </div>
      </template>
      <div v-if="boardInfo" class="board-summary">
        <div class="summary-item">
          <span class="summary-label">涨跌幅</span>
          <span class="summary-value" :class="priceClass(boardInfo.change_percent)">
            {{ fmtPercent(boardInfo.change_percent) }}
          </span>
        </div>
        <div class="summary-item" v-if="boardInfo.up_count != null">
          <span class="summary-label">上涨/下跌</span>
          <span class="summary-value">{{ boardInfo.up_count }}/{{ boardInfo.down_count }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">成份股</span>
          <span class="summary-value">{{ stocks.length }} 只</span>
        </div>
      </div>
    </el-card>

    <!-- 板块K线图 -->
    <el-card class="chart-card" style="margin-top: 12px">
      <template #header>
        <div class="card-header">
          <span>K线图（成份股权均合成）</span>
          <el-radio-group v-model="klinePeriod" size="small" @change="loadKline">
            <el-radio-button value="daily">日K</el-radio-button>
            <el-radio-button value="weekly">周K</el-radio-button>
            <el-radio-button value="monthly">月K</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div v-loading="klineLoading">
        <KLineChart v-if="klineData.length" :data="klineData" />
        <el-empty v-else-if="!klineLoading" description="暂无K线数据（非交易时段可能无数据）" />
      </div>
    </el-card>

    <el-card class="stock-list-card" style="margin-top: 12px">
      <template #header>
        <div class="card-header">
          <span>成份股列表</span>
          <el-button text size="small" @click="loadStocks" :loading="loading">刷新</el-button>
        </div>
      </template>
      <el-table :data="stocks" stripe size="small" @row-click="goToStock">
        <el-table-column prop="code" label="代码" width="120" />
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column label="现价" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: priceColor(row.change_percent) }">{{ row.current_price?.toFixed(2) ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="涨跌幅" width="120" align="right">
          <template #default="{ row }">
            <el-tag :type="row.change_percent > 0 ? 'danger' : 'success'" size="small" effect="dark">
              {{ fmtPercent(row.change_percent) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="涨跌额" width="120" align="right">
          <template #default="{ row }">
            <span :style="{ color: priceColor(row.change_amount) }">{{ row.change_amount?.toFixed(2) ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成交量" width="120" align="right">
          <template #default="{ row }">{{ fmtVol(row.volume) }}</template>
        </el-table-column>
        <el-table-column label="成交额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && stocks.length === 0" description="暂无数据" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBoardIndustryStocks, getBoardConceptStocks, getHotBoards, getHotConcepts, getBoardKline } from '../api/stock'
import KLineChart from '../components/KLineChart.vue'

const route = useRoute()
const router = useRouter()

const boardType = computed(() => route.params.type) // 'industry' | 'concept'
const boardName = ref('')
const stocks = ref([])
const boardInfo = ref(null)
const loading = ref(false)

// K线
const klineData = ref([])
const klineLoading = ref(false)
const klinePeriod = ref('daily')

let refreshTimer = null

onMounted(async () => {
  boardName.value = decodeURIComponent(route.params.name)
  await Promise.all([loadBoardInfo(), loadStocks()])
  await loadKline()
  refreshTimer = setInterval(() => loadStocks(true), 30000)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})

async function loadBoardInfo() {
  try {
    const list = boardType.value === 'industry' ? await getHotBoards() : await getHotConcepts()
    const found = list.find(b => b.name === boardName.value)
    if (found) boardInfo.value = found
  } catch (e) {}
}

async function loadKline() {
  if (stocks.value.length === 0) return
  klineLoading.value = true
  try {
    const codes = stocks.value.slice(0, 10).map(s => s.code).join(',')
    const data = await getBoardKline(codes, klinePeriod.value, 120)
    klineData.value = Array.isArray(data) ? data : []
  } catch (e) {
    klineData.value = []
  } finally {
    klineLoading.value = false
  }
}

async function loadStocks(silent) {
  if (!silent) loading.value = true
  try {
    const fn = boardType.value === 'industry' ? getBoardIndustryStocks : getBoardConceptStocks
    const raw = await fn(boardName.value)
    stocks.value = Array.isArray(raw) ? raw : (raw.data || [])
    if (!boardInfo.value && stocks.value.length > 0) {
      boardInfo.value = { change_percent: null, up_count: null, down_count: null }
    }
  } catch (e) {
    stocks.value = []
  } finally {
    if (!silent) loading.value = false
  }
}

function goToStock(row) {
  router.push(`/stock/${row.code}`)
}

function goBack() {
  router.push('/')
}

function priceClass(p) {
  if (!p) return ''
  return p > 0 ? 'up' : p < 0 ? 'down' : ''
}

function priceColor(p) {
  if (!p) return '#303133'
  return p > 0 ? '#ef5350' : p < 0 ? '#26a69a' : '#303133'
}

function fmtPercent(p) {
  if (p == null) return '-'
  return (p > 0 ? '+' : '') + p.toFixed(2) + '%'
}

function fmtVol(v) {
  if (!v) return '-'
  if (v >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (v >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toString()
}

function fmtAmount(v) {
  if (!v) return '-'
  if (v >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (v >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toFixed(0)
}
</script>

<style scoped>
.board-detail {
  max-width: 1200px;
  margin: 0 auto;
}
.board-header {
  display: flex;
  align-items: center;
}
.board-header h2 {
  margin: 0;
  font-size: 20px;
}
.board-summary {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-label {
  font-size: 13px;
  color: #909399;
}
.summary-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.summary-value.up { color: #ef5350; }
.summary-value.down { color: #26a69a; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
