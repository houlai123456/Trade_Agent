import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useLoadingStore = defineStore('loading', () => {
  const activeRequests = ref(0)
  const progress = ref(0)
  let timer = null

  function start() {
    activeRequests.value++
    if (timer) return
    // 模拟进度条缓慢推进
    progress.value = 0
    timer = setInterval(() => {
      if (progress.value < 80) progress.value += (80 - progress.value) * 0.1
    }, 200)
  }

  function done() {
    activeRequests.value = Math.max(0, activeRequests.value - 1)
    if (activeRequests.value === 0) {
      progress.value = 100
      clearInterval(timer)
      timer = null
      setTimeout(() => { progress.value = 0 }, 300)
    }
  }

  return { activeRequests, progress, start, done }
})
