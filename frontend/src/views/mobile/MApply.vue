<template>
  <div class="m-page">
    <!-- 步骤一：选择资产 -->
    <template v-if="step === 1">
      <div class="m-seg" :data-active="type === 'BORROW' ? '1' : '0'">
        <button class="m-seg-btn" :class="{ active: type === 'RECEIVE' }" type="button" @click="type = 'RECEIVE'">
          领用
        </button>
        <button class="m-seg-btn" :class="{ active: type === 'BORROW' }" type="button" @click="type = 'BORROW'">
          借用
        </button>
      </div>

      <!-- 搜索框 -->
      <div class="m-search">
        <svg width="15" height="15" viewBox="0 0 20 20" fill="none">
          <circle cx="9" cy="9" r="6" stroke="currentColor" stroke-width="1.5" />
          <line x1="13.5" y1="13.5" x2="17" y2="17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
        <input
          v-model="keyword"
          class="m-search-input"
          type="search"
          placeholder="搜索编码 / 名称 / 序列号"
          @input="onKeywordInput"
        />
      </div>

      <!-- 资产列表 -->
      <div v-if="loading && !assets.length" class="m-skeleton-list">
        <div v-for="n in 4" :key="n" class="m-skeleton-card" />
      </div>
      <template v-else>
        <div v-if="!assets.length" class="m-empty">没有匹配的闲置资产</div>
        <div
          v-for="(a, i) in assets"
          :key="a.id"
          class="m-card apply-asset"
          :class="{ selected: selected.has(a.id) }"
          :style="{ '--i': Math.min(i, 8) }"
          @click="toggleAsset(a)"
        >
          <div class="apply-asset-main">
            <div class="apply-asset-name">{{ a.name }}</div>
            <div class="apply-asset-sub">
              {{ a.barcode }}{{ a.sn ? ` · SN ${a.sn}` : '' }}{{ a.locationName ? ` · ${a.locationName}` : '' }}
            </div>
          </div>
          <span class="apply-check" :class="{ checked: selected.has(a.id) }">
            <svg v-if="selected.has(a.id)" width="12" height="12" viewBox="0 0 20 20" fill="none">
              <path d="M4 10.5L8 14.5L16 5.5" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </span>
        </div>
        <button v-if="hasMore" class="load-more" type="button" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
      </template>

      <!-- 底部操作 -->
      <div class="apply-footer">
        <button class="m-btn primary" type="button" :disabled="!selected.size" @click="step = 2">
          下一步{{ selected.size ? `（已选 ${selected.size} 台）` : '' }}
        </button>
      </div>
    </template>

    <!-- 步骤二：填写信息 -->
    <template v-else>
      <button class="apply-back" type="button" @click="step = 1">
        <svg width="16" height="16" viewBox="0 0 20 20" fill="none">
          <path d="M12 4L6 10L12 16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        返回选择资产
      </button>

      <div class="m-field">
        <label class="m-field-label required">单据类型</label>
        <div class="m-seg m-seg-compact" :data-active="type === 'BORROW' ? '1' : '0'">
          <button class="m-seg-btn" :class="{ active: type === 'RECEIVE' }" type="button" @click="type = 'RECEIVE'">
            领用
          </button>
          <button class="m-seg-btn" :class="{ active: type === 'BORROW' }" type="button" @click="type = 'BORROW'">
            借用
          </button>
        </div>
      </div>

      <div class="m-field" role="button" @click="openLocationSheet">
        <label class="m-field-label required">{{ typeLabel }}区域</label>
        <div class="m-field-value" :class="{ placeholder: !locationName }">
          {{ locationName || '审批通过后资产位置更新至此' }}
          <svg width="14" height="14" viewBox="0 0 20 20" fill="none" class="m-field-arrow">
            <path d="M7.5 5L12.5 10L7.5 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
      </div>

      <div class="m-field">
        <label class="m-field-label required">{{ typeLabel }}部门</label>
        <input v-model="department" class="m-input" type="text" maxlength="100" placeholder="请输入部门" />
      </div>

      <div class="m-field">
        <label class="m-field-label required">{{ typeLabel }}事由</label>
        <textarea
          v-model="reason"
          class="m-textarea"
          maxlength="500"
          placeholder="请输入事由"
        ></textarea>
      </div>

      <!-- 已选清单 -->
      <div class="picked-head">已选资产（{{ selected.size }}）</div>
      <div v-for="a in selectedList" :key="a.id" class="m-card picked-card">
        <div class="picked-main">
          <div class="apply-asset-name">{{ a.name }}</div>
          <div class="apply-asset-sub">{{ a.barcode }}{{ a.sn ? ` · SN ${a.sn}` : '' }}</div>
        </div>
        <button class="picked-remove" type="button" aria-label="移除" @click="selected.delete(a.id)">
          <svg width="14" height="14" viewBox="0 0 20 20" fill="none">
            <line x1="5" y1="5" x2="15" y2="15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <line x1="15" y1="5" x2="5" y2="15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="apply-footer">
        <button class="m-btn ghost" type="button" @click="step = 1">上一步</button>
        <button
          class="m-btn primary"
          type="button"
          :disabled="submitting || !canSubmit"
          @click="onSubmit"
        >
          {{ submitting ? '提交中…' : `提交申请（${selected.size} 台）` }}
        </button>
      </div>
    </template>

    <!-- 区域选择弹层（底部半屏） -->
    <div v-if="locSheet" class="loc-sheet m-sheet in" @click.self="closeLocationSheet">
      <div class="loc-panel">
        <div class="loc-head">
          <span class="m-sheet-head-title">选择{{ typeLabel }}区域</span>
          <button class="m-sheet-close" type="button" @click="closeLocationSheet">
            <svg width="16" height="16" viewBox="0 0 20 20" fill="none">
              <line x1="5" y1="5" x2="15" y2="15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              <line x1="15" y1="5" x2="5" y2="15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </button>
        </div>
        <div class="loc-body">
          <button
            v-for="node in locationNodes"
            :key="node.id"
            class="loc-item"
            :class="{ active: locationId === node.id }"
            :style="{ paddingLeft: `${14 + node.depth * 20}px` }"
            type="button"
            @click="pickLocation(node)"
          >
            <span class="loc-name">{{ node.name }}</span>
            <svg v-if="locationId === node.id" width="15" height="15" viewBox="0 0 20 20" fill="none">
              <path d="M4 10.5L8 14.5L16 5.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <div v-if="!locationNodes.length" class="m-empty">暂无区域数据</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { assetApi } from '@/api/modules/asset'
import { receiptApi } from '@/api/modules/receipt'
import { useUserStore } from '@/stores/user'
import { useBasedataStore } from '@/stores/basedata'
import { buildTree } from '@/utils/tree'
import type { Asset } from '@/api/interface/asset'
import type { ReceiptType } from '@/api/interface/receipt'
import type { Location } from '@/api/interface/basedata'

const router = useRouter()
const userStore = useUserStore()
const basedataStore = useBasedataStore()

const PAGE_SIZE = 20

/* ---------------- 步骤与类型 ---------------- */
const step = ref<1 | 2>(1)
const type = ref<ReceiptType>('RECEIVE')
const typeLabel = computed(() => (type.value === 'RECEIVE' ? '领用' : '借用'))

/* ---------------- 资产搜索与多选 ---------------- */
const keyword = ref('')
const searchKeyword = ref('')
const assets = ref<Asset[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const hasMore = computed(() => assets.value.length < total.value)

/** 已选资产（跨搜索/翻页保持） */
const selected = ref(new Map<number, Asset>())
const selectedList = computed(() => [...selected.value.entries()].map(([, a]) => a).reverse())

let searchTimer: ReturnType<typeof setTimeout> | undefined

const onKeywordInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = keyword.value.trim()
    page.value = 1
    void loadAssets()
  }, 300)
}

const loadAssets = async (append = false) => {
  loading.value = true
  try {
    const resp = await assetApi.getAssets({
      page: page.value,
      size: PAGE_SIZE,
      status: 'IDLE',
      keyword: searchKeyword.value || undefined,
    })
    if (append) {
      const seen = new Set(assets.value.map((a) => a.id))
      assets.value = [...assets.value, ...resp.records.filter((a) => !seen.has(a.id))]
    } else {
      assets.value = resp.records
    }
    total.value = resp.total
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  page.value += 1
  void loadAssets(true)
}

const toggleAsset = (a: Asset) => {
  if (selected.value.has(a.id)) selected.value.delete(a.id)
  else selected.value.set(a.id, a)
  // 触发响应式（Map 直接变更不触发）
  selected.value = new Map(selected.value)
}

/* ---------------- 表单字段 ---------------- */
const locationId = ref<number>()
const locationName = ref('')
const department = ref('')
const reason = ref('')
const submitting = ref(false)

const canSubmit = computed(
  () => selected.value.size > 0 && !!locationId.value && !!department.value.trim() && !!reason.value.trim(),
)

/* ---------------- 区域选择 ---------------- */
const locSheet = ref(false)

interface LocationNode {
  id: number
  name: string
  depth: number
}

/** 位置树 → 缩进扁平列表（弹层用） */
const locationNodes = computed<LocationNode[]>(() => {
  const tree = buildTree<Location>(basedataStore.locations)
  const out: LocationNode[] = []
  const walk = (nodes: Array<Location & { children?: unknown[] }>, depth: number) => {
    nodes.forEach((n) => {
      out.push({ id: n.id, name: n.name, depth })
      if (n.children?.length) walk(n.children as Array<Location & { children?: unknown[] }>, depth + 1)
    })
  }
  walk(tree as Array<Location & { children?: unknown[] }>, 0)
  return out
})

const openLocationSheet = () => {
  locSheet.value = true
}

const closeLocationSheet = () => {
  locSheet.value = false
}

const pickLocation = (node: LocationNode) => {
  locationId.value = node.id
  locationName.value = node.name
  closeLocationSheet()
}

/* ---------------- 提交 ---------------- */
const onSubmit = async () => {
  if (submitting.value || !canSubmit.value) return
  submitting.value = true
  try {
    await receiptApi.apply({
      type: type.value,
      assetIds: [...selected.value.keys()],
      locationId: locationId.value!,
      department: department.value.trim(),
      reason: reason.value.trim(),
    })
    ElMessage.success(`${typeLabel.value}申请已提交，等待审批`)
    router.replace('/m/approvals?tab=mine')
  } catch {
    /* 拦截器已提示（如 409 重复占用） */
  } finally {
    submitting.value = false
  }
}

/* ---------------- 初始化 ---------------- */
onMounted(async () => {
  void loadAssets()
  const me = await userStore.loadMe()
  if (me?.dept) department.value = me.dept
  if (!basedataStore.locations.length) void basedataStore.fetchLocations()
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped>
/* ---------- 搜索框 ---------- */
.m-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 12px;
  margin-bottom: 12px;
  background: var(--color-bg-2);
  border-radius: var(--radius-round);
  color: var(--color-text-4);
}

.m-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: none;
  font-size: var(--text-base);
  color: var(--color-text-1);
}

.m-search-input:focus {
  outline: none;
}

.m-search-input::-webkit-search-cancel-button {
  display: none;
}

.m-skeleton-list {
  padding-top: 2px;
}

/* ---------- 资产卡（可选中态） ---------- */
.apply-asset {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1.5px solid transparent;
  transition: border-color 0.2s, transform 0.12s ease;
}

.apply-asset.selected {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
}

.apply-asset-main {
  flex: 1;
  min-width: 0;
}

.apply-asset-name {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.apply-asset-sub {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--color-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.apply-check {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1.5px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-inverse);
  transition: background-color 0.18s, border-color 0.18s, transform 0.18s;
}

.apply-check.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
  transform: scale(1.05);
}

.load-more {
  display: block;
  width: 100%;
  height: 40px;
  border: none;
  background: none;
  color: var(--color-primary);
  font-size: var(--text-base);
}

.load-more:disabled {
  color: var(--color-text-4);
}

/* ---------- 步骤二 ---------- */
.apply-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  padding: 2px 0 10px;
  color: var(--color-primary);
  font-size: var(--text-base);
}

.m-seg-compact {
  margin-bottom: 0;
}

.m-field-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: var(--text-base);
  color: var(--color-text-1);
  min-height: 22px;
}

.m-field-value.placeholder {
  color: var(--color-text-4);
}

.m-field-arrow {
  flex-shrink: 0;
  color: var(--color-text-4);
}

.picked-head {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-1);
  margin: 14px 2px 10px;
}

.picked-card {
  display: flex;
  align-items: center;
  gap: 10px;
}

.picked-main {
  flex: 1;
  min-width: 0;
}

.picked-remove {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: var(--color-bg-3);
  color: var(--color-text-3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.picked-remove:active {
  color: var(--color-error-text);
  background: var(--color-error-bg);
}

/* ---------- 底部固定操作条 ---------- */
.apply-footer {
  display: flex;
  gap: 12px;
  position: sticky;
  bottom: 0;
  padding: 12px 0 calc(12px + env(safe-area-inset-bottom));
  background: linear-gradient(to top, var(--color-bg-1) 70%, transparent);
}

/* ---------- 区域弹层（底部半屏） ---------- */
.loc-sheet {
  background: rgba(0, 0, 0, 0.4);
  justify-content: flex-end;
}

.loc-panel {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: 65vh;
  background: var(--color-bg-2);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  animation: loc-in 0.3s cubic-bezier(0.32, 0.72, 0, 1);
}

@keyframes loc-in {
  from {
    transform: translateY(100%);
  }
  to {
    transform: none;
  }
}

.loc-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 46px;
  border-bottom: 1px solid var(--color-border-light);
}

.loc-body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 8px 0 16px;
}

.loc-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 44px;
  padding-right: 16px;
  border: none;
  background: none;
  color: var(--color-text-2);
  font-size: var(--text-base);
}

.loc-item.active {
  color: var(--color-primary);
  font-weight: 600;
}

.loc-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .loc-panel {
    animation: none;
  }
}
</style>
