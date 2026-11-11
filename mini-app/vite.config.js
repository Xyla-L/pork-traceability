import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5173,
    // H5 本地联调必须走代理：小程序里 uni.request 打的是相对路径 /api/v1，
    // 没有代理就只能看到 404，页面上的存证/验真都没法真跑一遍。
    proxy: {
      '/api': {
        target: process.env.API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
