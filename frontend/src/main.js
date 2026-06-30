import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注册Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 暗色模式初始化：从 localStorage 读取
const isDark = localStorage.getItem('theme') === 'dark'
if (isDark) document.documentElement.classList.add('dark')

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
