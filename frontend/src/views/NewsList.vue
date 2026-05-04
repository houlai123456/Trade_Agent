<template>
  <div class="news-list">
    <el-card>
      <template #header>
        <div class="news-header">
          <h3>新闻舆情</h3>
          <div class="news-controls">
            <el-input
              v-model="stockCode"
              placeholder="输入股票代码筛选"
              clearable
              style="width: 200px"
              @keyup.enter="loadNews"
            />
            <el-button type="primary" @click="loadNews" :loading="loading">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading" class="news-container">
        <div v-for="item in newsList" :key="item.id" class="news-item">
          <div class="news-header-row">
            <el-tag
              :type="sentimentTagType(item.sentiment)"
              size="small"
              effect="dark"
              class="sentiment-tag"
            >
              {{ sentimentLabel(item.sentiment) }}
            </el-tag>
            <span class="news-source">{{ item.source }}</span>
            <span class="news-time">{{ formatTime(item.publishTime) }}</span>
          </div>
          <a class="news-title" :href="item.url" target="_blank" v-if="item.url">
            {{ item.title }}
          </a>
          <div class="news-title" v-else>{{ item.title }}</div>
          <div class="news-summary">{{ item.summary }}</div>
          <div class="news-stock">
            <el-tag size="small" v-if="item.stockCode">{{ item.stockName || item.stockCode }}</el-tag>
            <el-tag
              v-for="s in parseAffected(item.affectedStocks)"
              :key="s"
              size="small"
              type="warning"
              effect="plain"
            >{{ s }}</el-tag>
          </div>
        </div>
        <el-empty v-if="newsList.length === 0" description="暂无新闻" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getLatestNews, getNewsByStock } from '../api/news'

const newsList = ref([])
const stockCode = ref('')
const loading = ref(false)

onMounted(() => {
  loadNews()
})

async function loadNews() {
  loading.value = true
  try {
    if (stockCode.value.trim()) {
      newsList.value = await getNewsByStock(stockCode.value.trim())
    } else {
      newsList.value = await getLatestNews()
    }
  } catch (e) {
    console.error('加载新闻失败', e)
  } finally {
    loading.value = false
  }
}

function sentimentTagType(sentiment) {
  if (sentiment === 'POSITIVE') return 'danger'
  if (sentiment === 'NEGATIVE') return 'success'
  return 'info'
}

function sentimentLabel(sentiment) {
  const map = { POSITIVE: '利好', NEGATIVE: '利空', NEUTRAL: '中性' }
  return map[sentiment] || '中性'
}

function parseAffected(val) {
  if (!val) return []
  try { return JSON.parse(val) } catch { return [] }
}

function formatTime(time) {
  if (!time) return ''
  return time.substring(0, 10) + ' ' + time.substring(11, 16)
}
</script>

<style scoped>
.news-list {
  max-width: 1000px;
  margin: 0 auto;
}
.news-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.news-header h3 {
  margin: 0;
}
.news-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}
.news-container {
  min-height: 200px;
}
.news-item {
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
}
.news-item:last-child {
  border-bottom: none;
}
.news-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.sentiment-tag {
  flex-shrink: 0;
}
.news-source {
  font-size: 12px;
  color: #909399;
}
.news-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
}
.news-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  text-decoration: none;
  display: block;
  margin-bottom: 6px;
  cursor: pointer;
}
.news-title:hover {
  color: #409eff;
}
.news-summary {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 8px;
}
.news-stock {
  display: flex;
  gap: 6px;
}
</style>
