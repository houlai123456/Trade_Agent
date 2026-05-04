<template>
  <div class="stock-detail" v-loading="loading">
    <!-- 基本信息 -->
    <el-card class="info-card" v-if="quote">
      <div class="stock-header">
        <div class="stock-title">
          <h2>{{ quote.name }} ({{ quote.code }})</h2>
          <el-button
            v-if="!isIndex"
            :type="isWatchlisted ? 'danger' : 'primary'"
            :icon="isWatchlisted ? StarFilled : Star"
            @click="toggleWatchlist"
            plain
            size="small"
          >
            {{ isWatchlisted ? '删除自选' : '加自选' }}
          </el-button>
        </div>
        <div class="price-section">
          <span class="current-price" :style="{ color: priceColor }">
            {{ quote.currentPrice?.toFixed(2) }}
          </span>
          <span class="change-info" :style="{ color: priceColor }">
            {{ quote.changePercent >= 0 ? '+' : '' }}{{ quote.changePercent?.toFixed(2) }}%
            {{ quote.changeAmount >= 0 ? '+' : '' }}{{ quote.changeAmount?.toFixed(2) }}
          </span>
        </div>
        <div class="detail-grid">
          <div class="detail-item"><label>开盘</label><span>{{ quote.openPrice?.toFixed(2) }}</span></div>
          <div class="detail-item"><label>昨收</label><span>{{ quote.yesterdayClose?.toFixed(2) }}</span></div>
          <div class="detail-item"><label>最高</label><span>{{ quote.highPrice?.toFixed(2) }}</span></div>
          <div class="detail-item"><label>最低</label><span>{{ quote.lowPrice?.toFixed(2) }}</span></div>
          <div class="detail-item"><label>成交量</label><span>{{ formatVolume(quote.volume) }}</span></div>
          <div class="detail-item"><label>成交额</label><span>{{ formatAmount(quote.amount) }}</span></div>
        </div>
      </div>
    </el-card>

    <!-- 图表 + 盘口 -->
    <div class="chart-row" style="margin-top: 16px">
      <el-card class="chart-card">
        <template #header>
          <div class="chart-header">
            <el-radio-group v-model="period" size="small" @change="onPeriodChange">
              <el-radio-button value="DAY">日K</el-radio-button>
              <el-radio-button value="WEEK">周K</el-radio-button>
              <el-radio-button value="MONTH">月K</el-radio-button>
              <el-radio-button value="INTRADAY">分时</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <KLineChart v-if="['DAY','WEEK','MONTH'].includes(period)" :data="klineData" />
        <IntradayChart v-else :data="intradayData" :prev-close="quote?.yesterdayClose" />
      </el-card>

      <!-- 盘口 -->
      <el-card class="bidask-card" v-if="bidAsk && !isIndex" shadow="never">
        <template #header><span class="bidask-title">盘口</span></template>
        <div class="bidask-panel">
          <div class="limit-row limit-up-row">
            <span class="limit-label">涨停</span>
            <span class="limit-price up">{{ limitUp.toFixed(2) }}</span>
            <span class="limit-pct">+{{ limitPct }}%</span>
          </div>
          <div v-for="i in 5" :key="'s'+i"
            class="bidask-row sell-row"
            @click="clickPrice(bidAsk[`sell${6-i}`])"
            :title="'点击填入价格'">
            <span class="bidask-label">卖{{ 6-i }}</span>
            <span class="bidask-price sell">{{ bidAsk[`sell${6-i}`]?.toFixed(2) }}</span>
            <span class="bidask-vol">{{ formatBidVol(bidAsk[`sell${6-i}_vol`]) }}</span>
          </div>
          <div class="bidask-current">
            <span class="bidask-price-current" :style="{ color: priceColor }">{{ bidAsk.current_price?.toFixed(2) }}</span>
            <span class="bidask-change" :style="{ color: priceColor }">
              {{ bidAsk.change_percent >= 0 ? '+' : '' }}{{ bidAsk.change_percent?.toFixed(2) }}%
            </span>
          </div>
          <div v-for="i in 5" :key="'b'+i"
            class="bidask-row buy-row"
            @click="clickPrice(bidAsk[`buy${i}`])"
            :title="'点击填入价格'">
            <span class="bidask-label">买{{ i }}</span>
            <span class="bidask-price buy">{{ bidAsk[`buy${i}`]?.toFixed(2) }}</span>
            <span class="bidask-vol">{{ formatBidVol(bidAsk[`buy${i}_vol`]) }}</span>
          </div>
          <div class="limit-row limit-down-row">
            <span class="limit-label">跌停</span>
            <span class="limit-price down">{{ limitDown.toFixed(2) }}</span>
            <span class="limit-pct">-{{ limitPct }}%</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 交易面板 -->
    <el-card class="trade-card" style="margin-top: 16px" v-if="!isIndex">
      <template #header><span>挂单交易</span></template>
      <el-form :model="tradeForm" label-width="80px" size="default" inline>
        <el-form-item label="交易数量">
          <el-input-number v-model="tradeForm.quantity" :min="100" :step="100" :max="99999900" />
          <span style="margin-left: 8px; color: #909399; font-size: 13px">股（1手=100股）</span>
        </el-form-item>
        <el-form-item label="限价">
          <el-input-number v-model="tradeForm.price" :precision="2" :step="0.01" :min="0.01" :max="99999" style="width: 160px" />
          <span style="margin-left: 8px; color: #909399; font-size: 13px">
            元/股<template v-if="quote?.currentPrice">（当前价 {{ quote.currentPrice.toFixed(2) }}）</template>
          </span>
          <el-tag v-if="priceOutOfRange" :type="priceOutOfRange === 'up' ? 'danger' : 'success'" size="small" effect="plain" style="margin-left: 8px">
            {{ priceOutOfRange === 'up' ? '超过涨停价' : '低于跌停价' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="预估金额" v-if="tradeForm.price">
          <span style="font-weight: 700; font-size: 16px; color: #303133">
            {{ fmtMoney(tradeForm.price * tradeForm.quantity) }}
          </span>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="buying" @click="handleBuy" :disabled="!quote?.currentPrice || !!priceOutOfRange">
            买入
          </el-button>
          <el-button type="success" :loading="selling" @click="handleSell" :disabled="!quote?.currentPrice || !!priceOutOfRange" style="margin-left: 12px">
            卖出
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 资金流向 -->
    <el-card class="card" style="margin-top: 16px" v-if="fundFlow.length">
      <template #header><span>资金流向（近5个交易日）</span></template>
      <div style="margin-bottom: 8px; font-size: 13px; color: #909399">
        红色=净流入(买入)，绿色=净流出(卖出)。主力=超大单+大单(机构资金)，小单(散户)
      </div>
      <el-table :data="fundRows" stripe size="small" style="width: 100%">
        <el-table-column prop="type" label="资金类型" width="80" />
        <el-table-column v-for="f in fundFlow.slice(0, 5)" :key="f.date" :label="f.date" align="right" min-width="140">
          <template #default="{ row }">
            <span v-if="row.key === '主力'" :style="{ color: (f['主力净流入-净额']||0) >= 0 ? '#ef5350' : '#26a69a', fontWeight: 600 }">
              {{ fmtMoney(f['主力净流入-净额']) }}
              ({{ f['主力净流入-净占比'] != null ? (f['主力净流入-净占比'] >= 0 ? '+' : '') + f['主力净流入-净占比'].toFixed(2) + '%' : '-' }})
            </span>
            <span v-else-if="row.key === '超大单'" :style="{ color: (f['超大单净流入-净额']||0) >= 0 ? '#ef5350' : '#26a69a' }">
              {{ fmtMoney(f['超大单净流入-净额']) }}
              ({{ f['超大单净流入-净占比'] != null ? f['超大单净流入-净占比'].toFixed(2) + '%' : '-' }})
            </span>
            <span v-else-if="row.key === '大单'" :style="{ color: (f['大单净流入-净额']||0) >= 0 ? '#ef5350' : '#26a69a' }">
              {{ fmtMoney(f['大单净流入-净额']) }}
              ({{ f['大单净流入-净占比'] != null ? f['大单净流入-净占比'].toFixed(2) + '%' : '-' }})
            </span>
            <span v-else-if="row.key === '中单'" :style="{ color: (f['中单净流入-净额']||0) >= 0 ? '#ef5350' : '#26a69a' }">
              {{ fmtMoney(f['中单净流入-净额']) }}
              ({{ f['中单净流入-净占比'] != null ? f['中单净流入-净占比'].toFixed(2) + '%' : '-' }})
            </span>
            <span v-else-if="row.key === '小单'" :style="{ color: (f['小单净流入-净额']||0) >= 0 ? '#ef5350' : '#26a69a' }">
              {{ fmtMoney(f['小单净流入-净额']) }}
              ({{ f['中单净流入-净占比'] != null ? f['小单净流入-净占比'].toFixed(2) + '%' : '-' }})
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ===== 多Agent一键分析 ===== -->
    <AICollaboration :stock-code="code" />

    <!-- ===== ReAct Agent 对话 ===== -->
    <ReactChat :stock-code="code" :stock-name="quote?.name" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { getQuote, getKline, getWatchlist, addWatchlist, removeWatchlist, getIndexQuote, getIndexKline, getIntraday, getIndexIntraday, getFundFlow, getBidAsk } from '../api/stock'
import { placeOrder } from '../api/trade'
import { createWebSocket } from '../utils/websocket'
import KLineChart from '../components/KLineChart.vue'
import IntradayChart from '../components/IntradayChart.vue'
import AICollaboration from '../components/AICollaboration.vue'
import ReactChat from '../components/ReactChat.vue'

const route = useRoute()
const code = computed(() => route.params.code)

const isIndex = computed(() => {
  const c = code.value
  return c.startsWith('sh000') || c.startsWith('sz399')
})

const quote = ref(null)
const klineData = ref([])
const intradayData = ref([])
const fundFlow = ref([])
const bidAsk = ref(null)
const loading = ref(false)
const period = ref('DAY')
const isWatchlisted = ref(false)

const tradeForm = ref({ quantity: 100, price: null })
const buying = ref(false)
const selling = ref(false)

const fundRows = [
  { type: '主力', key: '主力' },
  { type: '超大单', key: '超大单' },
  { type: '大单', key: '大单' },
  { type: '中单', key: '中单' },
  { type: '小单', key: '小单' },
]

let wsClient = null

onMounted(() => {
  loadQuote()
  loadKline()
  loadIntraday()
  if (!isIndex.value) { checkWatchlist(); loadFundFlow(); loadBidAsk() }
  connectWebSocket()
})

onBeforeUnmount(() => {
  if (wsClient) wsClient.disconnect()
})

function mapIndexQuote(d) {
  if (!d) return null
  return {
    name: d.name,
    code: d.code,
    currentPrice: d.current_price,
    changePercent: d.change_percent,
    changeAmount: d.change_amount,
    openPrice: d.open_price,
    yesterdayClose: d.yesterday_close,
    highPrice: d.high_price,
    lowPrice: d.low_price,
    volume: d.volume,
    amount: d.amount,
  }
}

async function loadQuote() {
  loading.value = true
  try {
    if (isIndex.value) {
      const d = await getIndexQuote(code.value)
      quote.value = mapIndexQuote(d)
    } else {
      quote.value = await getQuote(code.value)
    }
    if (quote.value?.currentPrice) {
      tradeForm.value.price = quote.value.currentPrice
    }
  } catch (e) {
    console.error('加载行情失败', e)
  } finally {
    loading.value = false
  }
}

async function loadKline() {
  try {
    if (isIndex.value) {
      const periodMap = { DAY: 'daily', WEEK: 'weekly', MONTH: 'monthly' }
      klineData.value = await getIndexKline(code.value, periodMap[period.value] || 'daily')
    } else {
      klineData.value = await getKline(code.value, period.value)
    }
  } catch (e) {
    console.error('加载K线失败', e)
  }
}

async function loadIntraday() {
  try {
    if (isIndex.value) {
      const res = await getIndexIntraday(code.value)
      intradayData.value = res.data || res
    } else {
      const res = await getIntraday(code.value)
      intradayData.value = res.data || res
    }
  } catch (e) {
    console.error('加载分时失败', e)
  }
}

async function loadBidAsk() {
  try {
    const res = await getBidAsk(code.value)
    if (res && res.current_price != null) {
      bidAsk.value = res
    }
  } catch (e) { /* 盘口加载失败不影响主功能 */ }
}

async function loadFundFlow() {
  try { fundFlow.value = await getFundFlow(code.value) }
  catch (e) { /* ignore */ }
}

function connectWebSocket() {
  wsClient = createWebSocket((data) => {
    if (data.type === 'QUOTE_UPDATE' && Array.isArray(data.data)) {
      // 更新当前股票行情
      const match = data.data.find(q => q.code === code.value)
      if (match && quote.value) {
        quote.value.currentPrice = match.currentPrice ?? quote.value.currentPrice
        quote.value.changePercent = match.changePercent ?? quote.value.changePercent
        quote.value.changeAmount = match.changeAmount ?? quote.value.changeAmount
        quote.value.highPrice = match.highPrice ?? quote.value.highPrice
        quote.value.lowPrice = match.lowPrice ?? quote.value.lowPrice
        quote.value.volume = match.volume ?? quote.value.volume
        quote.value.amount = match.amount ?? quote.value.amount
        if (!tradeForm.value.price || tradeForm.value.price === 0) {
          tradeForm.value.price = quote.value.currentPrice
        }
      }
    }
  })
  wsClient.connect()
}

function onPeriodChange(val) {
  if (val === 'INTRADAY') {
    if (intradayData.value.length === 0) loadIntraday()
  } else {
    loadKline()
  }
}

async function checkWatchlist() {
  try {
    const list = await getWatchlist()
    isWatchlisted.value = list.some((item) => item.code === code.value)
  } catch (e) {
    console.error(e)
  }
}

async function toggleWatchlist() {
  try {
    if (isWatchlisted.value) {
      await removeWatchlist(code.value)
      isWatchlisted.value = false
      ElMessage.success('已删除自选')
    } else {
      await addWatchlist(code.value)
      isWatchlisted.value = true
      ElMessage.success('已添加自选')
    }
  } catch (e) {
    console.error(e)
  }
}

function clickPrice(price) {
  if (price != null) {
    tradeForm.value.price = price
  }
}

async function handleBuy() {
  if (!quote.value?.currentPrice) return
  const price = tradeForm.value.price || quote.value.currentPrice
  try {
    await ElMessageBox.confirm(
      `确认以限价 ${price.toFixed(2)} 买入 ${code.value} ${tradeForm.value.quantity} 股？`,
      '买入确认',
      { confirmButtonText: '确认挂单', cancelButtonText: '取消', type: 'info' }
    )
    buying.value = true
    await placeOrder(code.value, tradeForm.value.quantity, price, 'BUY')
    ElMessage.success(`挂单买入成功！${code.value} ${tradeForm.value.quantity}股，限价 ${price.toFixed(2)}`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || e || '挂单失败')
  } finally {
    buying.value = false
  }
}

async function handleSell() {
  if (!quote.value?.currentPrice) return
  const price = tradeForm.value.price || quote.value.currentPrice
  try {
    await ElMessageBox.confirm(
      `确认以限价 ${price.toFixed(2)} 卖出 ${code.value} ${tradeForm.value.quantity} 股？`,
      '卖出确认',
      { confirmButtonText: '确认挂单', cancelButtonText: '取消', type: 'warning' }
    )
    selling.value = true
    await placeOrder(code.value, tradeForm.value.quantity, price, 'SELL')
    ElMessage.success(`挂单卖出成功！${code.value} ${tradeForm.value.quantity}股，限价 ${price.toFixed(2)}`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || e || '挂单失败')
  } finally {
    selling.value = false
  }
}

function fmtMoney(v) {
  if (v == null) return '-'
  return '¥' + Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatBidVol(v) {
  if (v == null) return '-'
  if (v >= 10000) return (v / 10000).toFixed(1) + '万'
  return v.toString()
}

const priceColor = computed(() => {
  if (!quote.value?.changePercent) return '#303133'
  return quote.value.changePercent >= 0 ? '#ef5350' : '#26a69a'
})

const limitPct = computed(() => {
  const c = code.value
  if (c.startsWith('688')) return 20
  if (c.startsWith('30')) return 20
  if (c.startsWith('8')) return 30
  if (c.startsWith('4')) return 30
  return 10
})

const limitUp = computed(() => {
  const base = quote.value?.yesterdayClose
  if (!base) return 0
  return Math.round(base * (1 + limitPct.value / 100) * 100) / 100
})

const limitDown = computed(() => {
  const base = quote.value?.yesterdayClose
  if (!base) return 0
  return Math.round(base * (1 - limitPct.value / 100) * 100) / 100
})

const priceOutOfRange = computed(() => {
  const p = tradeForm.value.price
  if (!p || !limitUp.value || !limitDown.value) return null
  if (p > limitUp.value) return 'up'
  if (p < limitDown.value) return 'down'
  return null
})

function formatVolume(v) {
  if (!v) return '-'
  if (v >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (v >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toString()
}

function formatAmount(v) {
  if (!v) return '-'
  if (v >= 1e8) return (v / 1e8).toFixed(2) + '亿'
  if (v >= 1e4) return (v / 1e4).toFixed(0) + '万'
  return v.toFixed(0)
}
</script>

<style scoped>
.stock-detail {
  max-width: 1200px;
  margin: 0 auto;
}
.stock-header {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.stock-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.stock-title h2 {
  margin: 0;
  font-size: 22px;
}
.price-section {
  display: flex;
  align-items: baseline;
  gap: 16px;
}
.current-price {
  font-size: 36px;
  font-weight: bold;
}
.change-info {
  font-size: 18px;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.detail-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.detail-item label {
  color: #909399;
  font-size: 13px;
}
.detail-item span {
  font-weight: 600;
}

.chart-row {
  display: flex;
  gap: 12px;
}
.chart-card {
  flex: 1;
  min-width: 0;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bidask-card {
  width: 240px;
  flex-shrink: 0;
}
.bidask-title {
  font-weight: 600;
  font-size: 14px;
}
.bidask-panel {
  font-size: 13px;
}
.bidask-row {
  display: flex;
  align-items: center;
  padding: 4px 0;
  cursor: pointer;
  border-radius: 4px;
}
.bidask-row:hover {
  background: #f0f2f5;
}
.bidask-label {
  width: 32px;
  color: #909399;
  font-size: 12px;
}
.bidask-price {
  flex: 1;
  text-align: right;
  font-weight: 600;
  font-family: monospace;
}
.bidask-price.sell {
  color: #ef5350;
}
.bidask-price.buy {
  color: #26a69a;
}
.bidask-vol {
  width: 80px;
  text-align: right;
  color: #606266;
  font-family: monospace;
  font-size: 12px;
}
.bidask-current {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-top: 1px solid #e4e7ed;
  border-bottom: 1px solid #e4e7ed;
  margin: 4px 0;
}
.limit-row {
  display: flex;
  align-items: center;
  padding: 3px 0;
  border-radius: 3px;
  font-size: 12px;
}
.limit-up-row {
  margin-bottom: 2px;
}
.limit-down-row {
  margin-top: 2px;
}
.limit-label {
  width: 32px;
  font-weight: 700;
  font-size: 11px;
}
.limit-up-row .limit-label { color: #ef5350; }
.limit-down-row .limit-label { color: #26a69a; }
.limit-price {
  flex: 1;
  text-align: right;
  font-weight: 700;
  font-family: monospace;
  font-size: 14px;
}
.limit-price.up { color: #ef5350; }
.limit-price.down { color: #26a69a; }
.limit-pct {
  width: 44px;
  text-align: right;
  font-size: 11px;
  font-weight: 600;
}
.limit-up-row .limit-pct { color: #ef5350; }
.limit-down-row .limit-pct { color: #26a69a; }
.bidask-price-current {
  font-size: 18px;
  font-weight: 700;
  font-family: monospace;
}
.bidask-change {
  font-size: 13px;
  font-weight: 600;
}

.trade-card {
  margin-top: 16px;
}

</style>
