<template>
  <div class="dashboard">
    <!-- 指数行情 -->
    <div class="index-bar">
      <div v-for="idx in indices" :key="idx.code" class="index-card" @click="showIndexChart(idx)">
        <div class="index-name">{{ idx.name }}</div>
        <div class="index-price" :class="priceClass(idx.change_percent)">
          {{ idx.current_price?.toFixed(2) }}
        </div>
        <div class="index-change" :class="priceClass(idx.change_percent)">
          {{ fmtPercent(idx.change_percent) }}
        </div>
      </div>
    </div>

    <!-- 热点板块排名（行业 + 概念） -->
    <el-card shadow="never" class="hotboard-card">
      <template #header>
        <div class="card-header">
          <span>热点板块</span>
        </div>
      </template>
      <el-tabs v-model="hotBoardTab" class="hotboard-tabs">
        <el-tab-pane label="行业板块" name="industry">
          <div class="hotboard-grid" v-loading="hotBoardLoading">
            <div
              v-for="(b, i) in hotBoards.slice(0, 20)"
              :key="b.name"
              class="hotboard-item"
              :class="{ 'top3': i < 3 }"
              @click="goToBoard('industry', b.name)"
            >
              <span class="hb-rank">{{ i + 1 }}</span>
              <span class="hb-name">{{ b.name }}</span>
              <span class="hb-change" :class="priceClass(b.change_percent)">
                {{ fmtPercent(b.change_percent) }}
              </span>
              <span class="hb-count">{{ b.up_count }}/{{ b.down_count }}</span>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="概念板块" name="concept">
          <div class="hotboard-grid" v-loading="conceptLoading">
            <div
              v-for="(b, i) in hotConcepts.slice(0, 20)"
              :key="b.name"
              class="hotboard-item"
              :class="{ 'top3': i < 3 }"
              @click="goToBoard('concept', b.name)"
            >
              <span class="hb-rank">{{ i + 1 }}</span>
              <span class="hb-name">{{ b.name }}</span>
              <span class="hb-change" :class="priceClass(b.change_percent)">
                {{ fmtPercent(b.change_percent) }}
              </span>
              <span class="hb-count">{{ b.up_count != null ? b.up_count + '/' + (b.down_count ?? 0) : '' }}</span>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 市场概况 -->
    <el-card class="market-overview" shadow="never">
      <div class="overview-items">
        <div class="overview-item">
          <span class="ov-label">上涨</span>
          <span class="ov-value up">{{ overview.up_count || 0 }}</span>
        </div>
        <div class="overview-item">
          <span class="ov-label">下跌</span>
          <span class="ov-value down">{{ overview.down_count || 0 }}</span>
        </div>
        <div class="overview-item">
          <span class="ov-label">平盘</span>
          <span class="ov-value">{{ overview.flat_count || 0 }}</span>
        </div>
        <el-divider direction="vertical" />
        <div class="overview-item">
          <span class="ov-label">涨停</span>
          <span class="ov-value limit-up">{{ overview.limit_up || 0 }}</span>
        </div>
        <div class="overview-item">
          <span class="ov-label">跌停</span>
          <span class="ov-value limit-down">{{ overview.limit_down || 0 }}</span>
        </div>
        <el-divider direction="vertical" />
        <div class="overview-item">
          <span class="ov-label">总成交额</span>
          <span class="ov-value">{{ fmtAmount(overview.total_amount) }}</span>
        </div>
      </div>
    </el-card>

    <!-- 搜索 + 操作栏 -->
    <div class="toolbar">
      <div class="search-area">
        <el-button type="primary" @click="handleRefresh" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- 板块行情 -->
    <el-card shadow="never" class="board-card">
      <template #header>
        <div class="card-header board-tabs">
          <span
            v-for="b in boards"
            :key="b.key"
            class="board-tab"
            :class="{ active: activeBoard === b.key }"
            @click="switchBoard(b.key)"
          >
            {{ b.label }}
            <span class="board-count">{{ boardTotals[b.key] }}</span>
          </span>
          <el-tooltip content="点击列头排序" placement="right">
            <el-icon style="margin-left: auto; color: var(--el-text-color-secondary); cursor: help;"><InfoFilled /></el-icon>
          </el-tooltip>
        </div>
      </template>
      <el-table
        :data="boardStocks"
        stripe
        @row-click="goToDetail"
        size="small"
        v-loading="boardLoading"
        @sort-change="handleBoardSort"
      >
        <el-table-column prop="code" label="代码" width="110" />
        <el-table-column prop="name" label="名称" width="130" />
        <el-table-column label="现价" width="110" sortable="custom" prop="current_price">
          <template #default="{ row }">
            <span :style="{ color: priceColor(row.change_percent) }">
              {{ row.current_price != null ? row.current_price.toFixed(2) : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="涨跌幅" width="110" sortable="custom" prop="change_percent">
          <template #default="{ row }">
            <el-tag :type="priceTagType(row.change_percent)" effect="dark" size="small">
              {{ fmtPercent(row.change_percent) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最高" width="100">
          <template #default="{ row }">{{ row.high_price?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="最低" width="100">
          <template #default="{ row }">{{ row.low_price?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="成交量" width="120" sortable="custom" prop="volume">
          <template #default="{ row }">{{ fmtVol(row.volume) }}</template>
        </el-table-column>
        <el-table-column label="成交额" width="120" sortable="custom" prop="amount">
          <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="换手率" width="90" sortable="custom" prop="turnover_rate">
          <template #default="{ row }">{{ row.turnover_rate != null ? row.turnover_rate + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="市盈率" width="90" sortable="custom" prop="pe_ratio">
          <template #default="{ row }">{{ row.pe_ratio?.toFixed(2) ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!watchlistSet.has(row.code)" type="primary" link size="small" @click.stop="addStock(row.code)">加自选</el-button>
            <el-tag v-else type="info" size="small" effect="plain" style="cursor: default">已自选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="board-footer" v-if="boardTotals[activeBoard] > 0">
        <el-pagination
          v-model:current-page="boardPage"
          :page-size="boardPageSize"
          :total="boardTotals[activeBoard]"
          layout="prev, pager, next, total"
          @current-change="loadBoard"
          small
        />
      </div>
    </el-card>

    <!-- 指数分时图弹窗 -->
    <el-dialog
      v-model="chartDialog.visible"
      :title="chartDialog.title"
      width="800px"
      :close-on-click-modal="false"
      top="5vh"
    >
      <div ref="chartRef" style="width: 100%; height: 420px"></div>
      <template #footer>
        <el-button @click="chartDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="goToStock(chartDialog.code)">查看详情</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getIndexQuotes, getMarketOverview, getBoardStocks,
  getHotBoards, getHotConcepts, getIndexIntraday, addWatchlist, getWatchlist
} from '../api/stock'
import * as echarts from 'echarts'

const router = useRouter()

const loading = ref(false)
const indices = ref([])
const overview = ref({})

// 热点板块
const hotBoards = ref([])
const hotBoardLoading = ref(false)
const hotConcepts = ref([])
const conceptLoading = ref(false)
const hotBoardTab = ref('industry')

// 自选股（用于判断是否已添加）
const watchlistSet = ref(new Set())

async function loadWatchlistSet() {
  try {
    const data = await getWatchlist()
    const list = Array.isArray(data) ? data : (data.data || [])
    watchlistSet.value = new Set(list.map(s => s.code))
  } catch (e) {
    watchlistSet.value = new Set()
  }
}

// 板块（全部/主板/创业板/科创板/北交所）
const boards = [
  { key: 'all', label: '全部' },
  { key: 'main', label: '主板' },
  { key: 'chiNext', label: '创业板' },
  { key: 'star', label: '科创板' },
  { key: 'bj', label: '北交所' },
]
const activeBoard = ref('all')
const boardStocks = ref([])
const boardTotals = ref({ all: 0, main: 0, chiNext: 0, star: 0, bj: 0 })
const boardPage = ref(1)
const boardPageSize = ref(10)
const boardLoading = ref(false)

// 排序状态
const sortedBy = ref(null)
const sortOrder = ref(null)

// 分时图弹窗
const chartDialog = ref({
  visible: false,
  title: '',
  code: '',
})
const chartRef = ref(null)
let chartInstance = null

onMounted(async () => {
  await Promise.all([
    loadIndices(),
    loadMarketOverview(),
    loadHotBoards(),
    loadBoard(),
    loadWatchlistSet(),
  ])
  // 加载全部板块数量（不阻塞）
  boards.filter(b => b.key !== activeBoard.value).forEach(b => {
    getBoardStocks(b.key, 1, 1).then(res => {
      if (res && Array.isArray(res.data)) boardTotals.value[b.key] = res.total || 0
      else if (res?.data && Array.isArray(res.data.data)) boardTotals.value[b.key] = res.data.total || 0
    }).catch(() => {})
  })
  // 监听 WebSocket 实时推送（全部数据由WebSocket驱动，无需轮询）
  window.addEventListener('index-update', onIndexUpdate)
  window.addEventListener('board-update', onBoardUpdate)
  window.addEventListener('hot-board-update', onHotBoardUpdate)
  window.addEventListener('hot-concept-update', onHotConceptUpdate)
  window.addEventListener('market-update', onMarketUpdate)
})

onBeforeUnmount(() => {
  chartInstance?.dispose()
  window.removeEventListener('index-update', onIndexUpdate)
  window.removeEventListener('board-update', onBoardUpdate)
  window.removeEventListener('hot-board-update', onHotBoardUpdate)
  window.removeEventListener('hot-concept-update', onHotConceptUpdate)
  window.removeEventListener('market-update', onMarketUpdate)
})

function onIndexUpdate(e) {
  if (e.detail && Array.isArray(e.detail)) {
    indices.value = e.detail
  }
}

function onBoardUpdate(e) {
  const d = e.detail
  if (!d || !d.board) return
  const board = d.board
  const dataArr = d.data
  if (Array.isArray(dataArr)) {
    boardTotals.value[board] = d.total || 0
    // 只在第一页时更新表格，翻页后不受WebSocket干扰
    if (board === activeBoard.value && boardPage.value === 1) {
      boardStocks.value = dataArr
    }
  }
}

function onHotBoardUpdate(e) {
  if (Array.isArray(e.detail)) hotBoards.value = e.detail
}

function onHotConceptUpdate(e) {
  if (Array.isArray(e.detail)) hotConcepts.value = e.detail
}

function onMarketUpdate(e) {
  if (e.detail) overview.value = e.detail
}

async function loadIndices() {
  try { indices.value = await getIndexQuotes() } catch (e) {}
}

async function loadMarketOverview() {
  try { overview.value = await getMarketOverview() } catch (e) {}
}

async function loadHotBoards() {
  hotBoardLoading.value = true
  conceptLoading.value = true
  try {
    hotBoards.value = await getHotBoards()
  } catch (e) {
    hotBoards.value = []
  } finally {
    hotBoardLoading.value = false
  }
  try {
    hotConcepts.value = await getHotConcepts()
  } catch (e) {
    hotConcepts.value = []
  } finally {
    conceptLoading.value = false
  }
}

async function loadBoard(page, silent) {
  if (!silent) boardLoading.value = true
  try {
    const p = page || boardPage.value
    const res = await getBoardStocks(activeBoard.value, p, boardPageSize.value, sortedBy.value, sortOrder.value)
    let dataArr = []
    let totalCount = 0
    if (res && Array.isArray(res.data)) {
      dataArr = res.data
      totalCount = res.total || 0
    } else if (res && res.data && Array.isArray(res.data.data)) {
      dataArr = res.data.data
      totalCount = res.data.total || 0
    } else if (res && Array.isArray(res)) {
      dataArr = res
      totalCount = res.length
    }
    boardStocks.value = dataArr
    boardTotals.value[activeBoard.value] = totalCount
    if (page) boardPage.value = page
  } catch (e) {
    console.error('[Board] Error:', e)
    boardStocks.value = []
    boardTotals.value[activeBoard.value] = 0
  } finally {
    if (!silent) boardLoading.value = false
  }
}

async function switchBoard(key) {
  if (activeBoard.value === key) return
  activeBoard.value = key
  sortedBy.value = null
  sortOrder.value = null
  boardPage.value = 1
  await loadBoard(1)
  if (!boardTotals.value[key]) {
    getBoardStocks(key, 1, 1)
      .then(res => {
        if (res && Array.isArray(res.data)) boardTotals.value[key] = res.total || 0
        else if (res?.data && Array.isArray(res.data.data)) boardTotals.value[key] = res.data.total || 0
      })
      .catch(() => {})
  }
}

async function handleRefresh() {
  loading.value = true
  try {
    await Promise.all([
      loadIndices(),
      loadMarketOverview(),
      loadHotBoards(),
      loadBoard(),
      loadWatchlistSet(),
    ])
  } finally {
    loading.value = false
  }
}

function goToBoard(type, name) {
  router.push(`/board/${type}/${encodeURIComponent(name)}`)
}

async function addStock(code) {
  try {
    await addWatchlist(code)
    watchlistSet.value = new Set([...watchlistSet.value, code])
    ElMessage.success('已添加自选')
  } catch (e) {
    ElMessage.error('添加自选失败')
  }
}

function goToDetail(row) {
  router.push(`/stock/${row.code}`)
}

function goToStock(code) {
  router.push(`/stock/${code}`)
}

async function showIndexChart(idx) {
  chartDialog.value = {
    visible: true,
    title: `${idx.name} 分时图`,
    code: idx.code,
  }
  await nextTick()
  renderChart(idx.code)
}

async function renderChart(code) {
  if (!chartRef.value) return
  chartInstance?.dispose()
  chartInstance = echarts.init(chartRef.value)

  try {
    const data = await getIndexIntraday(code)
    if (!data || data.length === 0) {
      chartInstance.setOption({
        title: { text: '暂无分时数据（非交易时段）', left: 'center', top: 'center', textStyle: { fontSize: 14, color: '#909399' } },
      })
      return
    }

    const times = data.map((d) => d.time.slice(11, 16))
    const prices = data.map((d) => d.price)
    const vols = data.map((d) => d.volume || 0)
    const basePrice = prices[0]

    chartInstance.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' },
        formatter: (params) => {
          const p = params[0]
          const v = params[1]
          return `<div>${p.axisValue}</div>
            <div>价格: <b>${p.data.toFixed(2)}</b></div>
            <div>涨幅: <b style="color:${p.data >= basePrice ? '#ef5350' : '#26a69a'}">${((p.data - basePrice) / basePrice * 100).toFixed(2)}%</b></div>
            <div>成交量: ${fmtVol(v?.data || 0)}</div>`
        },
      },
      grid: [
        { left: '6%', right: '5%', top: '8%', height: '62%' },
        { left: '6%', right: '5%', top: '78%', height: '16%' },
      ],
      xAxis: [
        { type: 'category', data: times, boundaryGap: false, axisLabel: { fontSize: 11, interval: Math.max(1, Math.floor(times.length / 8)) } },
        { type: 'category', data: times, gridIndex: 1, boundaryGap: false, axisLabel: { show: false }, splitLine: { show: false } },
      ],
      yAxis: [
        { type: 'value', scale: true, splitNumber: 4, axisLabel: { formatter: (v) => v.toFixed(0) } },
        { type: 'value', gridIndex: 1, splitNumber: 3, axisLabel: { formatter: (v) => fmtVol(v) } },
      ],
      series: [
        {
          type: 'line', data: prices, smooth: true, symbol: 'none',
          lineStyle: { width: 2, color: prices[0] <= prices[prices.length - 1] ? '#ef5350' : '#26a69a' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: prices[0] <= prices[prices.length - 1] ? 'rgba(239,83,80,0.25)' : 'rgba(38,166,154,0.25)' },
              { offset: 1, color: prices[0] <= prices[prices.length - 1] ? 'rgba(239,83,80,0.02)' : 'rgba(38,166,154,0.02)' },
            ]),
          },
          markLine: { silent: true, data: [{ yAxis: basePrice, label: { formatter: `基准 ${basePrice.toFixed(2)}`, fontSize: 11 } }], lineStyle: { color: '#999', type: 'dashed' } },
        },
        { type: 'bar', xAxisIndex: 1, yAxisIndex: 1, data: vols, barMaxWidth: 3, itemStyle: { color: (p) => (p.value > 0 ? '#ef5350' : '#26a69a') } },
      ],
    })
  } catch (e) {
    chartInstance.setOption({
      title: { text: '加载失败', left: 'center', top: 'center', textStyle: { fontSize: 14, color: '#909399' } },
    })
  }
}

function priceColor(p) {
  if (!p) return 'var(--el-text-color-primary)'
  return p > 0 ? '#ef5350' : p < 0 ? '#26a69a' : 'var(--el-text-color-primary)'
}

function priceClass(p) {
  if (!p) return ''
  return p > 0 ? 'up' : p < 0 ? 'down' : ''
}

function priceTagType(p) {
  if (!p) return 'info'
  return p > 0 ? 'danger' : 'success'
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

async function handleBoardSort({ prop, order }) {
  sortedBy.value = prop
  sortOrder.value = order
  if (!prop || !order) return
  boardPage.value = 1
  await loadBoard(1)
}
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
}

/* 指数栏 */
.index-bar {
  display: flex;
  gap: 1px;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 12px;
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
}
.index-card {
  flex: 1;
  padding: 14px 16px;
  cursor: pointer;
  transition: background 0.2s;
  text-align: center;
  border-right: 1px solid var(--el-border-color-lighter);
}
.index-card:last-child { border-right: none; }
.index-card:hover { background: var(--el-fill-color-light); }
.index-name {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.index-price {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 2px;
}
.index-change {
  font-size: 12px;
  font-weight: 600;
}
.up { color: #ef5350; }
.down { color: #26a69a; }

/* 热点板块 */
.hotboard-card {
  margin-bottom: 12px;
}
.hotboard-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
}
.hotboard-tabs {
  margin-top: -8px;
}
.hotboard-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-right: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  transition: background 0.15s;
  cursor: pointer;
}
.hotboard-item:hover { background: var(--el-fill-color-light); }
html.dark .hotboard-item.top3 { background: rgba(239,83,80,0.12); }
.hotboard-item.top3 { background: #fff8f8; }
.hb-rank {
  width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
}
.hotboard-item.top3 .hb-rank {
  background: #ef5350;
  color: #fff;
}
.hb-name {
  flex: 1;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hb-change {
  font-weight: 600;
  min-width: 60px;
  text-align: right;
}
.hb-count {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  min-width: 50px;
  text-align: right;
}

/* 市场概况 */
.market-overview {
  margin-bottom: 12px;
}
.overview-items {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
.overview-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ov-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.ov-value {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.ov-value.up { color: #ef5350; }
.ov-value.down { color: #26a69a; }
.ov-value.limit-up { color: #ef5350; }
.ov-value.limit-down { color: #26a69a; }

/* 工具栏 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.search-area {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 卡片 */
.board-card {
  margin-bottom: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 板块标签 */
.board-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: none;
  flex-wrap: wrap;
}
.board-tab {
  padding: 6px 16px;
  cursor: pointer;
  font-size: 14px;
  color: var(--el-text-color-regular);
  border-radius: 4px 4px 0 0;
  transition: all 0.2s;
  user-select: none;
}
.board-tab:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.board-tab.active {
  color: #fff;
  background: var(--el-color-primary);
  font-weight: 600;
}
.board-count {
  font-size: 11px;
  margin-left: 4px;
  opacity: 0.8;
}
.board-footer {
  text-align: center;
  padding: 12px 0 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
