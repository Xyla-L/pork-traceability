import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'
import { mockAuthPlugin } from './mock/auth'

export default defineConfig(({ mode }) => {
  // 通过 .env 里 VITE_USE_MOCK_AUTH=true 开启登录 mock（后端 auth-service 未启动时兜底）；
  // 默认关闭，走真实后端。
  const env = loadEnv(mode, process.cwd(), '')
  const useMockAuth = env.VITE_USE_MOCK_AUTH === 'true'

  return {
  plugins: [
    vue(),
    ...(useMockAuth ? [mockAuthPlugin()] : []),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/types/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/types/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/styles/variables.scss" as *;`,
      },
    },
  },
  }
})
