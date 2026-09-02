import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  // 后端地址默认本地 asset-backend（6006），可用 VITE_PROXY_TARGET 覆盖
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:6006'
  // 鉴权服务默认本地 comm_public_basic（6002），钉钉免登接口走此代理
  const authProxyTarget = env.VITE_AUTH_PROXY_TARGET || 'http://localhost:6002'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173, // 与 asset-backend CORS 白名单一致（app.cors.allowed-origins）
      proxy: {
        // 代理不改写路径：/api/v1/me -> http://localhost:6006/api/v1/me
        '/api': {
          target: proxyTarget,
          changeOrigin: true
        },
        // 钉钉免登等鉴权接口：/auth-api/... -> http://localhost:6002/...（去前缀）
        '/auth-api': {
          target: authProxyTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/auth-api/, '')
        }
      }
    }
  }
})
