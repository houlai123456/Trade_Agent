import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api/stock/intraday': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/stock/search': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/stock/board': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/stock/hot-concepts': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/stock/fund-flow': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/stock/bid-ask': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/stock/north-flow': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/stock/hot-boards': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/index': {
        target: 'http://localhost:5000',
        changeOrigin: true,
      },
      '/api/rag': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
})
