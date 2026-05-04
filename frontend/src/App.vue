<template>
  <el-container style="height: 100vh">
    <el-header class="app-header">
      <div class="logo">
        <span class="logo-icon">T</span>
        <span class="logo-text">Trade Agent</span>
        <span class="logo-sub">A股智能交易助手</span>
      </div>
      <el-menu
        :default-active="currentRoute"
        mode="horizontal"
        router
        class="nav-menu"
      >
        <el-menu-item index="/">行情看板</el-menu-item>
        <el-menu-item index="/ai-chat">AI对话</el-menu-item>
        <el-menu-item index="/news">新闻舆情</el-menu-item>
        <el-menu-item index="/alerts">异动预警</el-menu-item>
        <el-menu-item index="/watch">盯盘设置</el-menu-item>
      </el-menu>
      <div class="header-right">
        <el-autocomplete
          v-model="searchKeyword"
          :fetch-suggestions="handleSearch"
          :trigger-on-focus="false"
          placeholder="搜索股票代码或名称"
          clearable
          style="width: 240px"
          @select="goToDetail"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #default="{ item }">
            <div class="search-item">
              <span class="search-code">{{ item.code }}</span>
              <span class="search-name">{{ item.name }}</span>
              <el-button type="primary" link size="small" @click.stop="goToDetail(item)" style="margin-left: auto;">
                详情
              </el-button>
            </div>
          </template>
        </el-autocomplete>
        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="user-avatar">
            <el-avatar :size="32" icon="UserFilled" />
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/trade">
                <el-icon><Wallet /></el-icon> 账户
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, UserFilled, Wallet } from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'
import { searchStock } from './api/stock'
import { createWebSocket } from './utils/websocket'
import { useAlertStore } from './stores/alert'
import { useStockStore } from './stores/stock'

const route = useRoute()
const router = useRouter()
const currentRoute = computed(() => route.path)
const searchKeyword = ref('')

let wsConnection = null

onMounted(() => {
  wsConnection = createWebSocket(handleWsMessage, handleWsError)
  wsConnection.connect()
})

onUnmounted(() => {
  wsConnection?.disconnect()
})

function handleWsMessage(data) {
  if (data.type === 'QUOTE_UPDATE') {
    // 更新自选股行情到store
    const stockStore = useStockStore()
    if (Array.isArray(data.data)) {
      data.data.forEach(q => stockStore.updateQuote(q))
    }
  } else if (data.type === 'ALERT') {
    const alertStore = useAlertStore()
    alertStore.addAlert(data.data)
    ElNotification({
      title: '异动预警',
      message: data.data.description || `${data.data.name} (${data.data.code}) 出现异动`,
      type: 'warning',
      duration: 5000,
    })
  } else if (data.type === 'WATCH_ALERT') {
    ElNotification({
      title: '盯盘提醒',
      message: data.message || `${data.name} (${data.code}) 触发盯盘条件`,
      type: 'info',
      duration: 5000,
    })
  }
}

function handleWsError(err) {
  console.error('WebSocket error:', err)
}

function handleUserCommand(path) {
  router.push(path)
}

async function handleSearch(query, cb) {
  if (!query.trim()) {
    cb([])
    return
  }
  try {
    const res = await searchStock(query)
    // Python服务返回 {success, data}，Java后端直接返数组
    const list = Array.isArray(res) ? res : (res.data || [])
    cb(list.map(r => ({ ...r, value: `${r.code} ${r.name}` })))
  } catch (e) {
    cb([])
  }
}

function goToDetail(item) {
  searchKeyword.value = ''
  router.push(`/stock/${item.code}`)
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }

.app-header {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
  height: 60px !important;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 40px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #409eff, #6366f1);
  color: #fff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 20px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}

.logo-sub {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}

.nav-menu {
  flex: 1;
  border-bottom: none !important;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 0 4px;
  border-radius: 4px;
  transition: background 0.2s;
}
.user-avatar:hover {
  background: #f5f7fa;
}

.app-main {
  background: #f5f7fa;
  padding: 16px;
  height: calc(100vh - 60px);
  overflow-y: auto;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.search-code {
  font-family: monospace;
  font-size: 13px;
  color: #409eff;
  font-weight: 600;
}

.search-name {
  font-size: 13px;
  color: #303133;
}

.el-autocomplete-suggestion li {
  padding: 6px 12px;
}
</style>
