import { ref } from 'vue'

/**
 * 主题三态切换（P1）：auto 跟随系统 / light 浅色 / dark 深色。
 *
 * - html.dark 类是唯一驱动源：设计令牌（variables.css）与 Element Plus（dark/css-vars.css）均依赖它
 * - index.html 首屏内联脚本做同一件事，避免暗色用户看到白闪；本模块接管后续运行时切换
 * - 偏好持久化 localStorage(asset.theme)，并经 storage 事件多标签页同步
 */

export type ThemeMode = 'auto' | 'light' | 'dark'

const THEME_KEY = 'asset.theme'
export const THEME_LABELS: Record<ThemeMode, string> = {
  auto: '跟随系统',
  light: '浅色模式',
  dark: '深色模式',
}

const mq = window.matchMedia('(prefers-color-scheme: dark)')

/** 当前模式（响应式，顶栏切换器渲染用） */
export const themeMode = ref<ThemeMode>(readMode())

function readMode(): ThemeMode {
  const saved = localStorage.getItem(THEME_KEY)
  return saved === 'light' || saved === 'dark' ? saved : 'auto'
}

function isDarkActive(): boolean {
  if (themeMode.value === 'auto') return mq.matches
  return themeMode.value === 'dark'
}

/** 把当前模式应用到 <html>（dark 类增删；light 时不加类且 variables.css 默认即浅色） */
function applyToDocument(): void {
  document.documentElement.classList.toggle('dark', isDarkActive())
}

/** 系统偏好变化时，auto 模式下实时跟随 */
mq.addEventListener('change', () => {
  if (themeMode.value === 'auto') applyToDocument()
})

/** 多标签页同步：其他标签页改主题时本地跟随 */
window.addEventListener('storage', (e) => {
  if (e.key !== THEME_KEY || !e.newValue) return
  themeMode.value = readMode()
  applyToDocument()
})

/** 切换模式（auto → light → dark 循环），持久化并立即生效 */
export function cycleTheme(): void {
  const order: ThemeMode[] = ['auto', 'light', 'dark']
  themeMode.value = order[(order.indexOf(themeMode.value) + 1) % order.length]
  localStorage.setItem(THEME_KEY, themeMode.value)
  applyToDocument()
}

/** 应用启动时调用一次（与 index.html 内联脚本幂等） */
export function initTheme(): void {
  applyToDocument()
}
