<template>
  <div class="watch-page">
    <el-tabs v-model="activeTab">
      <!-- 盯盘规则 -->
      <el-tab-pane label="盯盘规则" name="rules">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>盯盘规则</span>
              <el-button type="primary" size="small" @click="showAddRuleDialog = true">
                新建规则
              </el-button>
            </div>
          </template>
          <el-table :data="watchRules" stripe size="small" v-loading="ruleLoading">
            <el-table-column prop="code" label="代码" width="110" />
            <el-table-column prop="name" label="名称" width="130" />
            <el-table-column label="条件" width="180">
              <template #default="{ row }">
                <el-tag :type="row.conditionType === 'ABOVE' ? 'danger' : 'success'" size="small">
                  {{ row.conditionType === 'ABOVE' ? '高于' : '低于' }}
                </el-tag>
                <span style="margin-left: 6px; font-weight: 600">{{ row.targetPrice?.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" size="small"
                  @change="(val) => handleToggleRule(row, val)" />
              </template>
            </el-table-column>
            <el-table-column label="上次触发" width="170">
              <template #default="{ row }">
                <span v-if="row.lastTriggeredTime" style="color: #909399; font-size: 13px">
                  {{ row.lastTriggeredTime?.replace('T', ' ') }}
                </span>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">
                <span style="color: #909399; font-size: 13px">{{ row.createTime?.replace('T', ' ') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditRule(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteRule(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!ruleLoading && watchRules.length === 0" description="暂无盯盘规则" />
        </el-card>
      </el-tab-pane>

      <!-- 条件单 -->
      <el-tab-pane label="条件单" name="orders">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>条件单</span>
              <el-button type="primary" size="small" @click="showAddOrderDialog = true">
                新建条件单
              </el-button>
            </div>
          </template>
          <el-table :data="conditionOrders" stripe size="small" v-loading="orderLoading">
            <el-table-column prop="code" label="代码" width="100" />
            <el-table-column prop="name" label="名称" width="120" />
            <el-table-column label="方向" width="70">
              <template #default="{ row }">
                <el-tag :type="row.direction === 'BUY' ? 'danger' : 'success'" size="small" effect="dark">
                  {{ row.direction === 'BUY' ? '买入' : '卖出' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="触发条件" width="180">
              <template #default="{ row }">
                <el-tag v-if="row.conditionType === 'ABOVE'" type="danger" size="small">涨破</el-tag>
                <el-tag v-else-if="row.conditionType === 'BELOW'" type="success" size="small">跌破</el-tag>
                <el-tag v-else-if="row.conditionType === 'GOLDEN_CROSS'" type="danger" size="small">金叉</el-tag>
                <el-tag v-else-if="row.conditionType === 'DEATH_CROSS'" type="success" size="small">死叉</el-tag>
                <el-tag v-else-if="row.conditionType === 'VOLUME_BREAKOUT'" type="warning" size="small">放量</el-tag>
                <span v-if="row.triggerPrice" style="margin-left: 4px; font-weight: 600">{{ row.triggerPrice?.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="80" align="right">
              <template #default="{ row }">{{ row.quantity }}</template>
            </el-table-column>
            <el-table-column label="委托价" width="100" align="right">
              <template #default="{ row }">
                <span v-if="row.orderPrice">{{ row.orderPrice?.toFixed(2) }}</span>
                <span v-else style="color: #c0c4cc">市价</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'PENDING'" type="warning" size="small">等待中</el-tag>
                <el-tag v-else-if="row.status === 'TRIGGERED'" type="success" size="small">已触发</el-tag>
                <el-tag v-else-if="row.status === 'CANCELLED'" type="info" size="small">已取消</el-tag>
                <el-tag v-else type="info" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="触发时间" width="170">
              <template #default="{ row }">
                <span v-if="row.triggerTime" style="color: #909399; font-size: 13px">
                  {{ row.triggerTime?.replace('T', ' ') }}
                </span>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" type="danger" link size="small"
                  :loading="cancelLoadingId === row.id" @click="handleCancelOrder(row.id)">
                  撤销
                </el-button>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!orderLoading && conditionOrders.length === 0" description="暂无条件单" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建/编辑盯盘规则 Dialog -->
    <el-dialog v-model="showAddRuleDialog" :title="editingRule ? '编辑盯盘规则' : '新建盯盘规则'" width="480px" @closed="resetRuleDialog">
      <el-form :model="ruleForm" label-width="80px">
        <el-form-item label="股票">
          <el-autocomplete
            v-model="ruleForm.code"
            :fetch-suggestions="searchStockSuggest"
            :trigger-on-focus="false"
            placeholder="搜索股票代码或名称"
            style="width: 100%"
            clearable
            @select="(item) => { ruleForm.code = item.code; ruleForm.name = item.name }"
          >
            <template #default="{ item }">
              <span class="search-code">{{ item.code }}</span>
              <span class="search-name">{{ item.name }}</span>
            </template>
          </el-autocomplete>
        </el-form-item>
        <el-form-item label="条件类型">
          <el-radio-group v-model="ruleForm.conditionType">
            <el-radio value="ABOVE">高于</el-radio>
            <el-radio value="BELOW">低于</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标价格">
          <el-input-number v-model="ruleForm.targetPrice" :precision="2" :step="0.01" :min="0.01" :max="99999" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddRuleDialog = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="handleSaveRule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建条件单 Dialog -->
    <el-dialog v-model="showAddOrderDialog" title="新建条件单" width="480px" @closed="resetOrderDialog">
      <el-form :model="orderForm" label-width="80px">
        <el-form-item label="股票">
          <el-autocomplete
            v-model="orderForm.code"
            :fetch-suggestions="searchStockSuggest"
            :trigger-on-focus="false"
            placeholder="搜索股票代码或名称"
            style="width: 100%"
            clearable
            @select="(item) => { orderForm.code = item.code; orderForm.name = item.name }"
          >
            <template #default="{ item }">
              <span class="search-code">{{ item.code }}</span>
              <span class="search-name">{{ item.name }}</span>
            </template>
          </el-autocomplete>
        </el-form-item>
        <el-form-item label="方向">
          <el-radio-group v-model="orderForm.direction">
            <el-radio value="BUY">买入</el-radio>
            <el-radio value="SELL">卖出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="触发条件">
          <el-select v-model="orderForm.conditionType" style="width: 100%">
            <el-option label="涨破目标价" value="ABOVE" />
            <el-option label="跌破目标价" value="BELOW" />
            <el-option label="金叉 (MA5上穿MA10)" value="GOLDEN_CROSS" />
            <el-option label="死叉 (MA5下穿MA10)" value="DEATH_CROSS" />
            <el-option label="放量突破 (量>5日均量1.5倍)" value="VOLUME_BREAKOUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发价格" v-if="orderForm.conditionType === 'ABOVE' || orderForm.conditionType === 'BELOW'">
          <el-input-number v-model="orderForm.triggerPrice" :precision="2" :step="0.01" :min="0.01" :max="99999" style="width: 200px" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="orderForm.quantity" :min="100" :step="100" :max="99999900" />
          <span style="margin-left: 8px; color: #909399; font-size: 13px">股（1手=100股）</span>
        </el-form-item>
        <el-form-item label="委托价">
          <el-input-number v-model="orderForm.orderPrice" :precision="2" :step="0.01" :min="0" :max="99999" style="width: 200px" :placeholder="null" />
          <span style="margin-left: 8px; color: #909399; font-size: 13px">留空=市价触发</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddOrderDialog = false">取消</el-button>
        <el-button type="primary" :loading="orderSaving" @click="handleSaveOrder">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWatchRules, addWatchRule, updateWatchRule, deleteWatchRule, getConditionOrders, addConditionOrder, cancelConditionOrder } from '../api/watch'
import { searchStock } from '../api/stock'

const activeTab = ref('rules')

// ========== 盯盘规则 ==========
const watchRules = ref([])
const ruleLoading = ref(false)
const showAddRuleDialog = ref(false)
const ruleSaving = ref(false)
const editingRule = ref(null)
const ruleForm = ref({
  code: '',
  name: '',
  conditionType: 'ABOVE',
  targetPrice: null,
})

async function loadRules() {
  ruleLoading.value = true
  try {
    const res = await getWatchRules()
    watchRules.value = res.data || res || []
  } catch (e) {
    watchRules.value = []
  } finally {
    ruleLoading.value = false
  }
}

function handleEditRule(row) {
  editingRule.value = row
  ruleForm.value = {
    code: row.code,
    name: row.name,
    conditionType: row.conditionType,
    targetPrice: row.targetPrice,
  }
  showAddRuleDialog.value = true
}

async function handleSaveRule() {
  if (!ruleForm.value.code.trim() || !ruleForm.value.targetPrice) {
    ElMessage.warning('请填写完整信息')
    return
  }
  ruleSaving.value = true
  try {
    if (editingRule.value) {
      await updateWatchRule(editingRule.value.id, {
        conditionType: ruleForm.value.conditionType,
        targetPrice: ruleForm.value.targetPrice,
      })
      ElMessage.success('规则已更新')
    } else {
      await addWatchRule(ruleForm.value.code, ruleForm.value.name, ruleForm.value.conditionType, ruleForm.value.targetPrice)
      ElMessage.success('规则已创建')
    }
    showAddRuleDialog.value = false
    ruleForm.value = { code: '', name: '', conditionType: 'ABOVE', targetPrice: null }
    editingRule.value = null
    await loadRules()
  } catch (e) {
    // 错误已由 axios 拦截器统一处理
  } finally {
    ruleSaving.value = false
  }
}

async function handleDeleteRule(id) {
  try {
    await ElMessageBox.confirm('确认删除此盯盘规则？', '删除确认', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteWatchRule(id)
    ElMessage.success('已删除')
    await loadRules()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleToggleRule(row, enabled) {
  try {
    await updateWatchRule(row.id, { enabled })
  } catch (e) {
    row.enabled = enabled === 1 ? 0 : 1
  }
}

// ========== 条件单 ==========
const conditionOrders = ref([])
const orderLoading = ref(false)
const showAddOrderDialog = ref(false)
const orderSaving = ref(false)
const cancelLoadingId = ref(null)
const orderForm = ref({
  code: '',
  name: '',
  direction: 'BUY',
  conditionType: 'ABOVE',
  triggerPrice: null,
  quantity: 100,
  orderPrice: null,
})

async function loadOrders() {
  orderLoading.value = true
  try {
    const res = await getConditionOrders()
    conditionOrders.value = res.data || res || []
  } catch (e) {
    conditionOrders.value = []
  } finally {
    orderLoading.value = false
  }
}

async function handleSaveOrder() {
  const needsPrice = orderForm.value.conditionType === 'ABOVE' || orderForm.value.conditionType === 'BELOW'
  if (!orderForm.value.code.trim() || !orderForm.value.quantity) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (needsPrice && !orderForm.value.triggerPrice) {
    ElMessage.warning('请填写触发价格')
    return
  }
  if (orderForm.value.quantity % 100 !== 0) {
    ElMessage.warning('数量必须是100的整数倍')
    return
  }
  orderSaving.value = true
  try {
    await addConditionOrder(
      orderForm.value.code,
      orderForm.value.name,
      orderForm.value.direction,
      orderForm.value.conditionType,
      orderForm.value.triggerPrice || 0,
      orderForm.value.quantity,
      orderForm.value.orderPrice || null,
    )
    ElMessage.success('条件单已创建')
    showAddOrderDialog.value = false
    orderForm.value = { code: '', name: '', direction: 'BUY', conditionType: 'ABOVE', triggerPrice: null, quantity: 100, orderPrice: null }
    await loadOrders()
  } catch (e) {
    // 错误已由 axios 拦截器统一处理
  } finally {
    orderSaving.value = false
  }
}

async function handleCancelOrder(id) {
  try {
    await ElMessageBox.confirm('确认撤销此条件单？', '撤销确认', {
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消',
      type: 'warning',
    })
    cancelLoadingId.value = id
    await cancelConditionOrder(id)
    ElMessage.success('条件单已撤销')
    await loadOrders()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('撤销失败')
  } finally {
    cancelLoadingId.value = null
  }
}

// ========== 搜索 ==========
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

// 关闭dialog时重置编辑状态
function resetRuleDialog() {
  editingRule.value = null
  ruleForm.value = { code: '', name: '', conditionType: 'ABOVE', targetPrice: null }
}
function resetOrderDialog() {
  orderForm.value = { code: '', name: '', direction: 'BUY', conditionType: 'ABOVE', triggerPrice: null, quantity: 100, orderPrice: null }
}

onMounted(async () => {
  await Promise.all([loadRules(), loadOrders()])
})
</script>

<style scoped>
.watch-page {
  max-width: 1200px;
  margin: 0 auto;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
