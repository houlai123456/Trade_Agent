/**
 * WebSocket连接管理
 */
export function createWebSocket(onMessage, onError) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const url = `${protocol}//${host}/ws/stock`

  let ws = null
  let reconnectTimer = null
  let isConnected = false

  function connect() {
    try {
      ws = new WebSocket(url)
    } catch (e) {
      console.error('WebSocket连接失败', e)
      scheduleReconnect()
      return
    }

    ws.onopen = () => {
      console.log('WebSocket已连接')
      isConnected = true
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (onMessage) onMessage(data)
      } catch (e) {
        console.error('解析WebSocket消息失败', e)
      }
    }

    ws.onclose = () => {
      console.log('WebSocket已断开')
      isConnected = false
      scheduleReconnect()
    }

    ws.onerror = (err) => {
      console.error('WebSocket错误', err)
      isConnected = false
      if (onError) onError(err)
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, 5000)
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (ws) {
      ws.close()
      ws = null
    }
    isConnected = false
  }

  return {
    connect,
    disconnect,
    get isConnected() {
      return isConnected
    },
  }
}
