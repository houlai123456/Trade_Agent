<template>
  <div class="lhb-list">
    <el-card>
      <template #header>
        <div class="lhb-header">
          <h3>龙虎榜</h3>
          <div class="lhb-controls">
            <el-date-picker
              v-model="selectedDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              :shortcuts="dateShortcuts"
              style="width: 180px"
            />
            <el-button type="primary" @click="loadData" :loading="loading">
              查询
            </el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading">
        <div class="summary-bar" v-if="data.length">
          <span>共 <b>{{ data.length }}</b> 只股票上榜</span>
          <span class="summary-date">{{ selectedDate || '最近交易日' }}</span>
        </div>

        <el-table
          :data="data"
          stripe
          highlight-current-row
          style="width: 100%"
          :default-sort="{ prop: 'lhb_net_amount', order: 'descending' }"
          size="small"
        >
          <el-table-column prop="code" label="代码" width="100" fixed>
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="goStock(row.code)">
                {{ row.code }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="100" fixed />
          <el-table-column prop="close_price" label="收盘价" width="90" sortable>
            <template #default="{ row }">
              {{ row.close_price != null ? row.close_price.toFixed(2) : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="change_percent" label="涨跌幅" width="90" sortable>
            <template #default="{ row }">
              <span :class="changeClass(row.change_percent)">
                {{ formatPercent(row.change_percent) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="lhb_net_amount" label="龙虎榜净买额" width="130" sortable>
            <template #default="{ row }">
              <span :class="amountClass(row.lhb_net_amount)">
                {{ formatAmount(row.lhb_net_amount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="lhb_buy_amount" label="买入额" width="120" sortable>
            <template #default="{ row }">
              <span class="amount-red">{{ formatAmount(row.lhb_buy_amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lhb_sell_amount" label="卖出额" width="120" sortable>
            <template #default="{ row }">
              <span class="amount-green">{{ formatAmount(row.lhb_sell_amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lhb_total_amount" label="龙虎榜成交额" width="130" sortable>
            <template #default="{ row }">
              {{ formatAmount(row.lhb_total_amount) }}
            </template>
          </el-table-column>
          <el-table-column prop="net_amount_ratio" label="净买占比" width="100" sortable>
            <template #default="{ row }">
              {{ row.net_amount_ratio != null ? row.net_amount_ratio.toFixed(2) + '%' : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="turnover_rate" label="换手率" width="90" sortable>
            <template #default="{ row }">
              {{ row.turnover_rate != null ? row.turnover_rate.toFixed(2) + '%' : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="上榜原因" min-width="160">
            <template #default="{ row }">
              <el-tooltip :content="row.reason" placement="top" :show-after="300">
                <span class="reason-text">{{ row.reason }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" fixed="right">
            <template #default="{ row }">
              <el-popover placement="left" :width="360" trigger="click">
                <template #reference>
                  <el-button type="info" link size="small">解读</el-button>
                </template>
                <div class="interpretation-pop">
                  <div class="interp-title">{{ row.name }} ({{ row.code }})</div>
                  <div class="interp-reason">
                    <b>上榜原因：</b>{{ row.reason || '无' }}
                  </div>
                  <div class="interp-text" v-if="row.interpretation">
                    <b>解读：</b>{{ row.interpretation }}
                  </div>
                  <div class="interp-text" v-else>暂无解读</div>
                </div>
              </el-popover>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && data.length === 0" description="暂无龙虎榜数据" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLhbDetail } from '../api/lhb'

const router = useRouter()
const data = ref([])
const loading = ref(false)
const selectedDate = ref('')

const dateShortcuts = [
  { text: '今天', value: new Date() },
  { text: '昨天', value: () => { const d = new Date(); d.setDate(d.getDate() - 1); return d } },
  { text: '前天', value: () => { const d = new Date(); d.setDate(d.getDate() - 2); return d } },
]

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    data.value = await getLhbDetail(selectedDate.value || undefined)
  } catch (e) {
    console.error('加载龙虎榜数据失败', e)
    data.value = []
  } finally {
    loading.value = false
  }
}

function goStock(code) {
  router.push(`/stock/${code}`)
}

function formatAmount(val) {
  if (val == null) return '-'
  const abs = Math.abs(val)
  if (abs >= 1e8) return (val / 1e8).toFixed(2) + '亿'
  if (abs >= 1e4) return (val / 1e4).toFixed(2) + '万'
  return val.toFixed(2)
}

function formatPercent(val) {
  if (val == null) return '-'
  return (val > 0 ? '+' : '') + val.toFixed(2) + '%'
}

function changeClass(val) {
  if (val == null) return ''
  return val >= 0 ? 'color-red' : 'color-green'
}

function amountClass(val) {
  if (val == null) return ''
  return val >= 0 ? 'color-red' : 'color-green'
}
</script>

<style scoped>
.lhb-list {
  max-width: 1400px;
  margin: 0 auto;
}

.lhb-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.lhb-header h3 {
  margin: 0;
}

.lhb-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}

.summary-bar {
  margin-bottom: 12px;
  font-size: 14px;
  color: #606266;
}

.summary-date {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}

.color-red {
  color: #f56c6c;
  font-weight: 500;
}

.color-green {
  color: #67c23a;
  font-weight: 500;
}

.amount-red {
  color: #f56c6c;
}

.amount-green {
  color: #67c23a;
}

.reason-text {
  display: inline-block;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.interpretation-pop {
  font-size: 14px;
  line-height: 1.8;
}

.interp-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.interp-reason {
  color: #e6a23c;
  margin-bottom: 8px;
}

.interp-text {
  color: #606266;
  white-space: pre-wrap;
}
</style>
