import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAlertStore = defineStore('alert', () => {
  const unreadCount = ref(0)
  const latestAlerts = ref([])

  function addAlert(alert) {
    latestAlerts.value.unshift(alert)
    if (latestAlerts.value.length > 100) {
      latestAlerts.value.pop()
    }
    unreadCount.value++
  }

  function markAllRead() {
    unreadCount.value = 0
  }

  function setUnreadCount(count) {
    unreadCount.value = count
  }

  return {
    unreadCount,
    latestAlerts,
    addAlert,
    markAllRead,
    setUnreadCount,
  }
})
