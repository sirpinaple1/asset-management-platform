<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { assetApi } from '@/api/modules/asset'
import type { Asset } from '@/api/interface/asset'

/**
 * 命令面板（⌘K / Ctrl+K）：搜索资产 + 跳转页面，一步到位
 * - 静态命令：页面导航（按关键词模糊匹配）
 * - 资产搜索：防抖 300ms 调服务端 keyword 查询，选中深链 /assets?id=N 打开详情
 * - 键盘：↑↓ 选择、Enter 执行、Esc 关闭
 */
const router = useRouter()

const visible = ref(false)
const keyword = ref('')
const inputRef = ref<HTMLInputElement>()
const activeIndex = ref(0)

const NAV_COMMANDS = [
  { type: 'nav' as const, label: '工作台', path: '/dashboard' },
  { type: 'nav' as const, label: '审批中心', path: '/approvals' },
  { type: 'nav' as const, label: '资产列表', path: '/assets' },
  { type: 'nav' as const, label: '领用 & 退库', path: '/receipts/receive' },
  { type: 'nav' as const, label: '借用 & 归还', path: '/receipts/borrow' },
  { type: 'nav' as const, label: '资产调拨', path: '/transfers' },
  { type: 'nav' as const, label: '实物信息变更', path: '/changes' },
  { type: 'nav' as const, label: '盘点管理', path: '/stocktakes' },
  { type: 'nav' as const, label: '分类管理', path: '/basedata/categories' },
  { type: 'nav' as const, label: '位置管理', path: '/basedata/locations' },
]

/* ---------------- 静态命令过滤 ---------------- */
const kw = computed(() => keyword.value.trim().toLowerCase())
const navResults = computed(() =>
  kw.value ? NAV_COMMANDS.filter((c) => c.label.toLowerCase().includes(kw.value)) : NAV_COMMANDS.slice(0, 6),
)

/* ---------------- 资产搜索（防抖 300ms） ---------------- */
const assetResults = ref<Asset[]>([])
const searching = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | undefined

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  const q = val.trim()
  if (!q) {
    assetResults.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    searching.value = true
    try {
      const page = await assetApi.getAssets({ keyword: q, size: 6 })
      assetResults.value = page.records
    } catch {
      assetResults.value = /* 失败静默：面板保留导航结果 */ []
    } finally {
      searching.value = false
    }
  }, 300)
})

interface PaletteItem {
  type: 'nav' | 'asset'
  id: string
  label: string
  sub?: string
  path: string
}

const items = computed<PaletteItem[]>(() => [
  ...assetResults.value.map((a) => ({
    type: 'asset' as const,
    id: `asset-${a.id}`,
    label: a.name,
    sub: `${a.barcode} · ${a.categoryName ?? ''} · ${a.locationName ?? ''}`,
    path: `/assets?id=${a.id}`,
  })),
  ...navResults.value.map((c) => ({
    type: 'nav' as const,
    id: `nav-${c.path}`,
    label: c.label,
    sub: '页面跳转',
    path: c.path,
  })),
])

watch(items, () => {
  activeIndex.value = 0
})

/* ---------------- 打开 / 关闭 / 执行 ---------------- */
const open = () => {
  visible.value = true
  keyword.value = ''
  assetResults.value = []
  activeIndex.value = 0
  nextTick(() => inputRef.value?.focus())
}
const close = () => {
  visible.value = false
}
const execute = (item: PaletteItem) => {
  close()
  /* 同路径重复跳转交给 router 处理；带 query 的深链每次都要生效 */
  router.push(item.path).catch(() => {})
}

const onKeydown = (e: KeyboardEvent) => {
  if (!visible.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = Math.min(activeIndex.value + 1, items.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, 0)
  } else if (e.key === 'Enter') {
    const item = items.value[activeIndex.value]
    if (item) {
      e.preventDefault()
      execute(item)
    }
  }
}

const onGlobalKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    visible.value ? close() : open()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  window.removeEventListener('keydown', onKeydown)
  if (searchTimer) clearTimeout(searchTimer)
})

defineExpose({ open })
</script>

<template>
  <Teleport to="body">
    <transition name="cmdk-fade">
      <div v-if="visible" class="cmdk-overlay" @click.self="close">
        <div class="cmdk-panel" role="dialog" aria-label="命令面板">
          <div class="cmdk-input-row">
            <svg width="16" height="16" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="9" cy="9" r="6" style="stroke: var(--color-text-4)" stroke-width="1.5" />
              <line x1="13.5" y1="13.5" x2="17" y2="17" style="stroke: var(--color-text-4)" stroke-width="1.5" stroke-linecap="round" />
            </svg>
            <input
              ref="inputRef"
              v-model="keyword"
              class="cmdk-input"
              placeholder="搜索资产（编码/名称/序列号）或跳转页面…"
              autocomplete="off"
              spellcheck="false"
            />
            <span class="cmdk-esc">ESC</span>
          </div>

          <div class="cmdk-list">
            <template v-if="assetResults.length">
              <div class="cmdk-group">资产</div>
              <button
                v-for="(item, i) in items.filter((it) => it.type === 'asset')"
                :key="item.id"
                type="button"
                class="cmdk-item is-asset"
                :class="{ active: i === activeIndex }"
                @mousemove="activeIndex = i"
                @click="execute(item)"
              >
                <span class="cmdk-item-label">{{ item.label }}</span>
                <span class="cmdk-item-sub">{{ item.sub }}</span>
              </button>
            </template>
            <div v-if="navResults.length" class="cmdk-group">页面</div>
            <button
              v-for="item in items.filter((it) => it.type === 'nav')"
              :key="item.id"
              type="button"
              class="cmdk-item"
              :class="{ active: activeIndex === items.findIndex((it) => it.id === item.id) }"
              @mousemove="activeIndex = items.findIndex((it) => it.id === item.id)"
              @click="execute(item)"
            >
              <span class="cmdk-item-label">{{ item.label }}</span>
              <span class="cmdk-item-sub">{{ item.sub }}</span>
            </button>
            <div v-if="!items.length && !searching" class="cmdk-empty">无匹配结果</div>
          </div>

          <div class="cmdk-footer">
            <span>↑↓ 选择</span>
            <span>Enter 打开</span>
            <span>Esc 关闭</span>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.cmdk-overlay {
  position: fixed;
  inset: 0;
  z-index: 2100;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 12vh;
}

.cmdk-panel {
  width: min(560px, calc(100vw - 32px));
  background: var(--color-bg-2);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cmdk-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.cmdk-input {
  flex: 1;
  border: none;
  outline: none;
  background: none;
  font-size: var(--text-md);
  color: var(--color-text-1);
}

.cmdk-input::placeholder {
  color: var(--color-text-4);
}

.cmdk-esc {
  font-size: var(--text-xs);
  color: var(--color-text-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 2px 6px;
}

.cmdk-list {
  max-height: 380px;
  overflow-y: auto;
  padding: 8px;
}

.cmdk-group {
  font-size: var(--text-xs);
  color: var(--color-text-4);
  padding: 8px 8px 4px;
}

.cmdk-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 8px 12px;
  border: none;
  border-radius: var(--radius-md);
  background: none;
  cursor: pointer;
  text-align: left;
}

.cmdk-item.active {
  background: var(--color-primary-bg);
}

.cmdk-item.is-asset .cmdk-item-label {
  font-weight: 600;
}

.cmdk-item-label {
  font-size: var(--text-base);
  color: var(--color-text-1);
}

.cmdk-item-sub {
  font-size: var(--text-xs);
  color: var(--color-text-3);
}

.cmdk-empty {
  padding: 24px;
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-text-4);
}

.cmdk-footer {
  display: flex;
  gap: 16px;
  padding: 8px 16px;
  border-top: 1px solid var(--color-border-light);
  font-size: var(--text-xs);
  color: var(--color-text-4);
}

.cmdk-fade-enter-active,
.cmdk-fade-leave-active {
  transition: opacity 0.12s ease;
}

.cmdk-fade-enter-from,
.cmdk-fade-leave-to {
  opacity: 0;
}
</style>
