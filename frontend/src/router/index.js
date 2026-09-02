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
  {
    path: '/watch',
    name: 'WatchPage',
    component: () => import('../views/WatchPage.vue'),
  },
  {
    path: '/lhb',
    name: 'LhbList',
    component: () => import('../views/LhbList.vue'),
  },
  {
    path: '/active-advices',
    name: 'ActiveAdvices',
    component: () => import('../views/ActiveAdvices.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
