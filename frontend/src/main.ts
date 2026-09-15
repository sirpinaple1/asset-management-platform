import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/variables.css'
import './styles/reset.css'
import './styles/common.css'
import '@/styles/index.css'
import App from './App.vue'
import router from './router'
import { vPermission } from '@/directives/permission'
import { initSessionSync } from '@/utils/sessionSync'
import { initTheme } from '@/utils/theme'

initTheme()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

/* 按钮级权限指令：v-permission="'asset:create'"（未配置的权限点默认全用户可视） */
app.directive('permission', vPermission)

/* 全局异常兜底（P0）：组件渲染/生命周期抛错时跳转异常页，防白屏；带防循环标志 */
let errorRedirecting = false
app.config.errorHandler = (err, _instance, info) => {
  console.error('[global-error]', info, err)
  if (!errorRedirecting && router.currentRoute.value.path !== '/exception/500') {
    errorRedirecting = true
    router.replace('/exception/500').finally(() => {
      errorRedirecting = false
    })
  }
}

app.mount('#app')

/* 多标签页会话同步：任一标签页登出/换账号，其余标签页同步登出或刷新 */
initSessionSync()
