import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
  },
  {
    path: '/stock/:code',
    name: 'StockDetail',
    component: () => import('../views/StockDetail.vue'),
  },
  {
    path: '/ai-chat',
    name: 'AiChat',
    component: () => import('../views/AiChat.vue'),
  },
  {
    path: '/news',
    name: 'NewsList',
    component: () => import('../views/NewsList.vue'),
  },
  {
    path: '/alerts',
    name: 'AlertList',
    component: () => import('../views/AlertList.vue'),
  },
  {
    path: '/trade',
    name: 'Trade',
    component: () => import('../views/Trade.vue'),
  },
  {
    path: '/board/:type/:name',
    name: 'BoardDetail',
    component: () => import('../views/BoardDetail.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
