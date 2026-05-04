<template>
  <div class="trade-page">
    <!-- 账户资产 -->
    <el-card shadow="never" class="account-card">
      <template #header>
        <div class="card-header">
          <span>虚拟账户</span>
          <el-button text size="small" @click="loadAccount" :loading="loading">刷新</el-button>
        </div>
      </template>
      <div class="account-items">
        <div class="account-item">
          <span class="acc-label">总资产</span>
          <span class="acc-value total">{{ fmtMoney(account.totalAssets) }}</span>
        </div>
        <div class="account-item">
          <span class="acc-label">可用资金</span>
          <span class="acc-value">{{ fmtMoney(account.availableBalance) }}</span>
        </div>
        <div class="account-item">
          <span class="acc-label">持仓市值</span>
          <span class="acc-value" :class="account.marketValue > 0 ? 'has-pos' : ''">{{ fmtMoney(account.marketValue) }}</span>
        </div>
        <div class="account-item">
          <span class="acc-label">冻结资金</span>
          <span class="acc-value frozen">{{ fmtMoney(account.frozenBalance) }}</span>
        </div>
      </div>
    </el-card>

    <!-- 交易面板 -->
    <el-card shadow="never" class="trade-panel-card">
      <template #header><span>下单交易</span></template>
      <el-form :model="tradeForm" label-width="80px" size="default">
        <el-form-item label="股票代码">
          <el-autocomplete
            v-model="tradeForm.code"
            :fetch-suggestions="searchStockSuggest"
            :trigger-on-focus="false"
            placeholder="输入代码搜索"
            style="width: 260px"
            clearable
            @select="(item) => { tradeForm.code = item.code; loadQuoteForForm(item.code) }"
          >
            <template #default="{ item }">
              <span class="search-code">{{ item.code }}</span>
              <span class="search-name">{{ item.name }}</span>
            </template>
          </el-autocomplete>
          <el-tag v-if="quoteInfo" :type="quoteInfo.change_percent > 0 ? 'danger' : 'success'" style="margin-left: 10px">
            现价 {{ quoteInfo.current_price?.toFixed(2) }}
            <span v-if="quoteInfo.change_percent">({{ fmtPercent(quoteInfo.change_percent) }})</span>
          </el-tag>
        </el-form-item>
        <el-form-item label="交易数量">
          <el-input-number v-model="tradeForm.quantity" :min="100" :step="100" :max="99999900" />
          <span style="margin-left: 8px; color: #909399; font-size: 13px">股（1手=100股）</span>
        </el-form-item>
        <el-form-item label="成交单价">
          <el-input-number v-model="tradeForm.price" :precision="2" :step="0.01" :min="0.01" :max="99999" style="width: 160px" />
          <span style="margin-left: 8px; color: #909399; font-size: 13px">
            元/股<template v-if="quoteInfo">（当前价 {{ quoteInfo.current_price?.toFixed(2) }}）</template>
          </span>
        </el-form-item>
        <el-form-item label="预估金额">
          <span v-if="quoteInfo" style="font-weight: 700; font-size: 16px; color: #303133">
            {{ fmtMoney(tradeForm.price * tradeForm.quantity) }}
          </span>
          <span v-else style="color: #c0c4cc">输入股票代码后自动计算</span>
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="buying" @click="handleBuy" :disabled="!canTrade">
            买入
          </el-button>
          <el-button type="success" :loading="selling" @click="handleSell" :disabled="!canTrade" style="margin-left: 12px">
            卖出
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 自选股 -->
    <el-card shadow="never" class="watchlist-card">
      <template #header>
        <div class="card-header">
          <span>自选股</span>
          <el-button text size="small" @click="loadWatchlist" :loading="wlLoading">刷新</el-button>
        </div>
      </template>
      <el-table :data="watchlistQuotes" stripe size="small" @row-click="selectWatchStock" v-if="watchlistQuotes.length > 0">
        <el-table-column prop="code" label="代码" width="110" />
        <el-table-column prop="name" label="名称" width="130" />
        <el-table-column label="现价" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: priceColor(row.change_percent) }">{{ row.current_price?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="涨跌幅" width="100" align="right">
          <template #default="{ row }">
            <el-tag :type="row.change_percent > 0 ? 'danger' : 'success'" size="small" effect="dark">
              {{ row.change_percent > 0 ? '+' : '' }}{{ row.change_percent?.toFixed(2) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="selectWatchStock(row)">交易</el-button>
            <el-button type="danger" link size="small" @click.stop="delWatch(row.code)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="还未添加自选股" />
    </el-card>

    <!-- 我的持仓 -->
    <el-card shadow="never" class="pos-card">
      <template #header>
        <div class="card-header">
          <span>我的持仓</span>
          <span class="header-subtitle">{{ positions.length }}只</span>
        </div>
      </template>
      <el-table :data="positions" stripe size="small" v-loading="posLoading" @row-click="goToDetail">
        <el-table-column prop="code" label="代码" width="110" />
        <el-table-column prop="name" label="名称" width="130" />
        <el-table-column label="持有数量" width="100" align="right">
          <template #default="{ row }">{{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="可用数量" width="100" align="right">
          <template #default="{ row }">{{ row.availableQuantity ?? row.quantity }}</template>
        </el-table-column>
        <el-table-column label="成本价" width="110" align="right">
          <template #default="{ row }">{{ row.costPrice?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="现价" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: priceColor(row.plRatio) }">{{ row.currentPrice?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="市值" width="120" align="right">
          <template #default="{ row }">{{ fmtMoney(row.marketValue) }}</template>
        </el-table-column>
        <el-table-column label="浮动盈亏" width="150" align="right">
          <template #default="{ row }">
            <span :style="{ color: priceColor(row.plRatio) }">
              {{ row.profitLoss > 0 ? '+' : '' }}{{ fmtMoney(row.profitLoss) }}
            </span>
            <span :style="{ color: priceColor(row.plRatio), fontSize: '12px', marginLeft: '4px' }">
              ({{ row.plRatio > 0 ? '+' : '' }}{{ row.plRatio?.toFixed(2) }}%)
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!posLoading && positions.length === 0" description="暂无持仓" />
    </el-card>

    <!-- 我的挂单 -->
    <el-card shadow="never" class="pending-card">
      <template #header>
        <div class="card-header">
          <span>我的挂单</span>
          <span class="header-subtitle">{{ pendingOrders.length }}笔</span>
          <el-button text size="small" @click="loadPendingOrders" :loading="pendingLoading">刷新</el-button>
        </div>
      </template>
      <el-table :data="pendingOrders" stripe size="small" v-loading="pendingLoading">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.tradeTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="方向" width="80">
          <template #default="{ row }">
            <el-tag :type="row.tradeType === 'BUY' ? 'danger' : 'success'" size="small" effect="dark">
              {{ row.tradeType === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="代码" width="110" />
        <el-table-column prop="name" label="名称" width="130" />
        <el-table-column label="限价" width="110" align="right">
          <template #default="{ row }">{{ row.price?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" :loading="cancelLoadingId === row.id" @click="handleCancelOrder(row.id)">
              撤单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!pendingLoading && pendingOrders.length === 0" description="暂无挂单" />
    </el-card>

    <!-- 交易流水 -->
    <el-card shadow="never" class="order-card">
      <template #header>
        <div class="card-header">
          <span>交易流水</span>
          <span class="header-subtitle">{{ orders.length }}笔</span>
        </div>
      </template>
      <el-table :data="orders" stripe size="small" v-loading="orderLoading">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.tradeTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.tradeType === 'BUY' ? 'danger' : 'success'" size="small" effect="dark">
              {{ row.tradeType === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="代码" width="110" />
        <el-table-column prop="name" label="名称" width="130" />
        <el-table-column label="成交价" width="110" align="right">
          <template #default="{ row }">{{ row.price?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'DONE'" type="success" size="small">成交</el-tag>
            <el-tag v-else-if="row.status === 'PENDING'" type="warning" size="small">挂单中</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="info" size="small">已撤单</el-tag>
            <span v-else>{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="盈亏" width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.profitLoss != null" :style="{ color: priceColor(row.profitLoss) }">
              {{ row.profitLoss > 0 ? '+' : '' }}{{ fmtMoney(row.profitLoss) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!orderLoading && orders.length === 0" description="暂无交易记录" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccount, buyStock, sellStock, getPositions, getOrders, getPendingOrders, cancelOrder } from '../api/trade'
import { searchStock, getWatchlist, removeWatchlist, addWatchlist, getQuotes } from '../api/stock'
import { useStockStore } from '../stores/stock'

const router = useRouter()
const stockStore = useStockStore()
const loading = ref(false)
const account = ref({ totalAssets: 0, availableBalance: 0, frozenBalance: 0, marketValue: 0 })
const positions = ref([])
const orders = ref([])
const posLoading = ref(false)
const orderLoading = ref(false)
const buying = ref(false)
const selling = ref(false)
const quoteInfo = ref(null)

// 挂单
const pendingOrders = ref([])
const pendingLoading = ref(false)
const cancelLoadingId = ref(null)

// 自选股
const watchlistQuotes = ref([])
const wlLoading = ref(false)

const tradeForm = ref({
  code: '',
  quantity: 100,
  price: null,
})

const canTrade = computed(() => tradeForm.value.code.trim() && tradeForm.value.quantity > 0 && quoteInfo.value)

onMounted(async () => {
  await Promise.all([loadAccount(), loadPositions(), loadOrders(), loadWatchlist(), loadPendingOrders()])
})

async function loadAccount() {
  try {
    const res = await getAccount()
    account.value = res.data || res
  } catch (e) {}
}

async function loadPositions() {
  posLoading.value = true
  try {
    const res = await getPositions()
    positions.value = res.data || res || []
  } catch (e) {
    positions.value = []
  } finally {
    posLoading.value = false
  }
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const res = await getOrders()
    orders.value = res.data || res || []
  } catch (e) {
    orders.value = []
  } finally {
    orderLoading.value = false
  }
}

async function searchStockSuggest(query, cb) {
  if (!query.trim()) { cb([]); return }
  try {
    const res = await searchStock(query)
    const list = Array.isArray(res) ? res : (res.data || [])
    cb(list.map(r => ({ ...r, value: `${r.code} ${r.name}` })))
  } catch (e) {
    cb([])
  }
}

async function loadQuoteForForm(code) {
  try {
    const { getQuote } = await import('../api/stock')
    const q = await getQuote(code)
    quoteInfo.value = q.data || q
    if (quoteInfo.value?.current_price) {
      tradeForm.value.price = quoteInfo.value.current_price
    }
  } catch (e) {
    quoteInfo.value = null
  }
}

// WebSocket实时行情更新交易面板的现价
watch(() => stockStore.quotes[tradeForm.value.code], (newQuote) => {
  if (newQuote && quoteInfo.value) {
    quoteInfo.value = { ...quoteInfo.value, ...newQuote }
  }
})

async function handleBuy() {
  if (!canTrade.value) return
  try {
    await ElMessageBox.confirm(
      `确认以现价 ${quoteInfo.value.current_price?.toFixed(2)} 买入 ${tradeForm.value.code} ${tradeForm.value.quantity} 股？`,
      '买入确认',
      { confirmButtonText: '确认买入', cancelButtonText: '取消', type: 'info' }
    )
    buying.value = true
    const res = await buyStock(tradeForm.value.code, tradeForm.value.quantity, tradeForm.value.price)
    const order = res.data || res
    ElMessage.success(`买入成功！${order.code} ${order.quantity}股，金额 ${fmtMoney(order.amount)}`)
    quoteInfo.value = null
    tradeForm.value.quantity = 100
    tradeForm.value.price = null
    await Promise.all([loadAccount(), loadPositions(), loadOrders()])
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || e || '买入失败')
  } finally {
    buying.value = false
  }
}

async function handleSell() {
  if (!canTrade.value) return
  try {
    await ElMessageBox.confirm(
      `确认以现价 ${quoteInfo.value.current_price?.toFixed(2)} 卖出 ${tradeForm.value.code} ${tradeForm.value.quantity} 股？`,
      '卖出确认',
      { confirmButtonText: '确认卖出', cancelButtonText: '取消', type: 'warning' }
    )
    selling.value = true
    const res = await sellStock(tradeForm.value.code, tradeForm.value.quantity, tradeForm.value.price)
    const order = res.data || res
    const pl = order.profitLoss != null ? `，盈亏 ${order.profitLoss > 0 ? '+' : ''}${fmtMoney(order.profitLoss)}` : ''
    ElMessage.success(`卖出成功！${order.code} ${order.quantity}股${pl}`)
    quoteInfo.value = null
    tradeForm.value.quantity = 100
    tradeForm.value.price = null
    await Promise.all([loadAccount(), loadPositions(), loadOrders()])
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || e || '卖出失败')
  } finally {
    selling.value = false
  }
}

async function loadPendingOrders() {
  pendingLoading.value = true
  try {
    const res = await getPendingOrders()
    pendingOrders.value = res.data || res || []
  } catch (e) {
    pendingOrders.value = []
  } finally {
    pendingLoading.value = false
  }
}

async function handleCancelOrder(id) {
  try {
    await ElMessageBox.confirm('确认撤销此挂单？', '撤单确认', {
      confirmButtonText: '确认撤单',
      cancelButtonText: '取消',
      type: 'warning'
    })
    cancelLoadingId.value = id
    await cancelOrder(id)
    ElMessage.success('撤单成功')
    await Promise.all([loadPendingOrders(), loadAccount(), loadPositions()])
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || e || '撤单失败')
  } finally {
    cancelLoadingId.value = null
  }
}

function goToDetail(row) {
  router.push(`/stock/${row.code}`)
}

// 自选股
async function loadWatchlist() {
  wlLoading.value = true
  try {
    const data = await getWatchlist()
    watchlistQuotes.value = Array.isArray(data) ? data : (data.data || [])
  } catch (e) {
    watchlistQuotes.value = []
  } finally {
    wlLoading.value = false
  }
}

function selectWatchStock(row) {
  tradeForm.value.code = row.code
  quoteInfo.value = row
  tradeForm.value.price = row.current_price
}

async function delWatch(code) {
  try {
    await removeWatchlist(code)
    await loadWatchlist()
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

function fmtMoney(v) {
  if (v == null) return '-'
  return '¥' + Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtPercent(p) {
  if (p == null) return '-'
  return (p > 0 ? '+' : '') + p.toFixed(2) + '%'
}

function priceColor(p) {
  if (!p) return '#303133'
  return p > 0 ? '#ef5350' : '#26a69a'
}
</script>

<style scoped>
.trade-page {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-subtitle {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}

/* 账户资产 */
.account-items {
  display: flex;
  gap: 32px;
}
.account-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.acc-label {
  font-size: 13px;
  color: #909399;
}
.acc-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}
.acc-value.total {
  color: #409eff;
  font-size: 28px;
}
.acc-value.has-pos {
  color: #e6a23c;
}
.acc-value.frozen {
  color: #909399;
}

/* 交易面板 */
.trade-panel-card {
  margin-bottom: 0;
}

.search-code {
  font-family: monospace;
  font-size: 13px;
  color: #409eff;
  font-weight: 600;
  margin-right: 8px;
}
.search-name {
  font-size: 13px;
  color: #303133;
}
</style>
