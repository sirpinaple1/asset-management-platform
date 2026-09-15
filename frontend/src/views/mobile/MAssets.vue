<template>
  <div class="m-page">
    <!-- 顶部分段器：持有中 / 已归还（滑动指示器） -->
    <div class="m-seg" :data-active="tab === 'returned' ? '1' : '0'">
      <span class="m-seg-indicator" aria-hidden="true" />
      <button
        v-for="seg in SEGS"
        :key="seg.key"
        class="m-seg-btn"
        :class="{ active: tab === seg.key }"
        type="button"
        @click="setTab(seg.key)"
      >
        {{ seg.label }}
      </button>
    </div>

    <div v-if="loading" class="skeleton-wrap">
      <div v-for="n in 4" :key="n" class="m-skeleton-card" />
    </div>
    <template v-else>
      <div v-if="curList.length === 0" class="m-empty">
        {{ tab === 'active' ? '当前没有持有中的资产' : '没有归还记录' }}
      </div>
      <div
        v-for="(a, i) in curList"
        :key="a.id"
        class="m-card"
        :style="{ '--i': Math.min(i, 8) }"
      >
        <div class="m-card-head">
          <span class="m-card-tag" :data-type="a.type">{{ typeLabel(a.type) }}</span>
          <span class="m-card-title">{{ a.assetName || `资产#${a.assetId}` }}</span>
        </div>
        <div class="m-card-sub">{{ a.assetBarcode }}{{ a.assetSn ? ` · SN ${a.assetSn}` : '' }}</div>
        <div class="m-card-foot">
          <span>{{ tab === 'active' ? `领取于 ${fmtDate(a.allocatedAt)}` : `归还于 ${fmtDate(a.returnedAt || '')}` }}</span>
          <button
            v-if="tab === 'active'"
            class="return-btn"
            type="button"
            :disabled="actingId === a.id"
            @click.stop="onReturn(a)"
          >
            {{ actingId === a.id ? '处理中…' : a.type === 'BORROW' ? '归还' : '退库' }}
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { receiptApi } from '@/api/modules/receipt'
import { useUserStore } from '@/stores/user'
import type { Allocation } from '@/api/interface/receipt'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const SEGS = [
  { key: 'active', label: '持有中' },
  { key: 'returned', label: '已归还' },
] as const
type TabKey = (typeof SEGS)[number]['key']

/** tab 与 URL query 双向同步（?tab=returned） */
const tab = ref<TabKey>(route.query.tab === 'returned' ? 'returned' : 'active')
watch(
  () => route.query.tab,
  (v) => {
    tab.value = v === 'returned' ? 'returned' : 'active'
  },
)
const setTab = (k: TabKey) => {
  if (tab.value === k) return
  router.replace({ query: k === 'active' ? {} : { tab: k } })
}

const loading = ref(true)
/** 归还动作防重复提交（记录 allocation id） */
const actingId = ref<number>()

const activeList = ref<Allocation[]>([])
const returnedList = ref<Allocation[]>([])
const curList = computed(() => (tab.value === 'active' ? activeList.value : returnedList.value))

const typeLabel = (t: Allocation['type']) =>
  t === 'RECEIVE' ? '领用' : t === 'BORROW' ? '借用' : '调拨'

const fmtDate = (s: string) => {
  const d = new Date(s)
  return Number.isNaN(d.getTime())
    ? s
    : d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const refresh = async () => {
  const me = await userStore.loadMe()
  if (!me) return
  loading.value = true
  try {
    const list = await receiptApi.getAllocations({ userId: me.userId })
    activeList.value = list.filter((a) => a.active)
    returnedList.value = list.filter((a) => !a.active)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

const onReturn = async (a: Allocation) => {
  if (actingId.value) return
  const action = a.type === 'BORROW' ? '归还' : '退库'
  try {
    await ElMessageBox.confirm(
      `确认${action}「${a.assetName || a.assetBarcode}」？${action}后资产回到闲置状态。`,
      `${action}确认`,
      { confirmButtonText: action, cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  actingId.value = a.id
  try {
    await receiptApi.returnAllocation(a.id)
    ElMessage.success(`已${action}`)
    await refresh()
  } catch {
    /* 拦截器已提示 */
  } finally {
    actingId.value = undefined
  }
}

onMounted(() => {
  void refresh()
})
</script>

<style scoped>
.skeleton-wrap {
  padding-top: 2px;
}

.return-btn {
  height: 30px;
  padding: 0 16px;
  border-radius: var(--radius-round);
  border: 1px solid var(--color-primary);
  background: transparent;
  color: var(--color-primary);
  font-size: var(--text-sm);
  transition: opacity 0.15s;
}

.return-btn:disabled {
  opacity: 0.45;
}
</style>
