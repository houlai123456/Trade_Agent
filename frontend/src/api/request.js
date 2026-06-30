import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useLoadingStore } from '../stores/loading'

// 全局 loading 追踪
const track = (instance) => {
  instance.interceptors.request.use((config) => {
    try { useLoadingStore().start() } catch (e) {}
    return config
  })
  instance.interceptors.response.use(
    (response) => { try { useLoadingStore().done() } catch (e) {}; return response },
    (error) => { try { useLoadingStore().done() } catch (e) {}; return Promise.reject(error) }
  )
}

const request = axios.create({ baseURL: '/api', timeout: 15000 })
track(request)

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const msg = error.response?.data?.message || error.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export { track }
export default request
