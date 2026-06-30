import request from './request'

// 获取龙虎榜详情
export function getLhbDetail(date) {
  return request.get('/lhb/detail', { params: { date }, timeout: 30000 })
}
