import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getWatchlist, getQuotes } from '../api/stock'

export const useStockStore = defineStore('stock', () => {
  const watchlist = ref([])
  const quotes = ref({}) // code -> StockQuote
  const loading = ref(false)

  const watchlistQuotes = computed(() => {
    return watchlist.value
      .map((item) => quotes.value[item.code])
      .filter(Boolean)
  })

  async function loadWatchlist() {
    try {
      const data = await getWatchlist()
      watchlist.value = data
      data.forEach((q) => {
        quotes.value[q.code] = q
      })
    } catch (e) {
      console.error('加载自选股失败', e)
    }
  }

  async function refreshQuotes() {
    if (watchlist.value.length === 0) return
    loading.value = true
    try {
      const codes = watchlist.value.map((item) => item.code)
      const data = await getQuotes(codes)
      data.forEach((q) => {
        quotes.value[q.code] = q
      })
    } catch (e) {
      console.error('刷新行情失败', e)
    } finally {
      loading.value = false
    }
  }

  function updateQuote(quote) {
    if (quote && quote.code) {
      quotes.value[quote.code] = quote
    }
  }

  function setWatchlist(list) {
    watchlist.value = list
  }

  return {
    watchlist,
    quotes,
    loading,
    watchlistQuotes,
    loadWatchlist,
    refreshQuotes,
    updateQuote,
    setWatchlist,
  }
})
