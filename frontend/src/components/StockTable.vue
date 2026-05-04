<template>
  <el-table :data="data" style="width: 100%" stripe @row-click="handleRowClick">
    <el-table-column prop="code" label="代码" width="100" />
    <el-table-column prop="name" label="名称" width="120" />
    <el-table-column label="现价" width="100">
      <template #default="{ row }">
        <span :style="{ color: row.changePercent >= 0 ? '#ef5350' : '#26a69a' }">
          {{ row.currentPrice?.toFixed(2) }}
        </span>
      </template>
    </el-table-column>
    <el-table-column label="涨跌幅" width="100">
      <template #default="{ row }">
        <el-tag :type="row.changePercent >= 0 ? 'danger' : 'success'" effect="dark" size="small">
          {{ row.changePercent >= 0 ? '+' : '' }}{{ row.changePercent?.toFixed(2) }}%
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="最高" width="100">
      <template #default="{ row }">{{ row.highPrice?.toFixed(2) }}</template>
    </el-table-column>
    <el-table-column label="最低" width="100">
      <template #default="{ row }">{{ row.lowPrice?.toFixed(2) }}</template>
    </el-table-column>
    <el-table-column label="成交量" width="120">
      <template #default="{ row }">{{ formatVolume(row.volume) }}</template>
    </el-table-column>
    <el-table-column label="成交额" width="120">
      <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  data: { type: Array, default: () => [] },
})

const router = useRouter()

function handleRowClick(row) {
  router.push(`/stock/${row.code}`)
}

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
