<template>
  <div class="m-page">
    <!-- 顶部 segmented：持有中 / 已归还 -->
    <div class="m-seg">
      <button
        v-for="seg in SEGS"
        :key="seg.key"
        class="m-seg-btn"
        :class="{ active: tab === seg.key }"
        type="button"
        @click="tab = seg.key"
      >
        {{ seg.label }}
      </button>
    </div>

    <div v-if="loading" class="m-empty">加载中…</div>
    <template v-else>
      <div v-if="curList.length === 0" class="m-empty">
        {{ tab === 'active' ? '当前没有持有中的资产' : '没有归还记录' }}
      </div>
      <div v-for="a in curList" :key="a.id" class="m-card">
        <div class="m-card-head">
          <span class="m-card-tag" :data-type="a.type">{{ typeLabel(a.type) }}</span>
          <span class="m-card-name">{{ a.assetName || `资产#${a.assetId}` }}</span>
        </div>
        <div class="m-card-sub">{{ a.assetBarcode }}{{ a.assetSn ? ` · SN ${a.assetSn}` : '' }}</div>
        <div class="m-card-foot">
          <span>{{ tab === 'active' ? `领取于 ${fmtDate(a.allocatedAt)}` : `归还于 ${fmtDate(a.returnedAt || '')}` }}</span>
          <button
            v-if="tab === 'active'"
            class="m-return-btn"
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { receiptApi } from '@/api/modules/receipt'
import { useUserStore } from '@/stores/user'
import type { Allocation } from '@/api/interface/receipt'

const userStore = useUserStore()

const SEGS = [
  { key: 'active', label: '持有中' },
  { key: 'returned', label: '已归还' },
] as const
const tab = ref<'active' | 'returned'>('active')

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
.m-page {
  padding: 12px 12px 20px;
}

.m-seg {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.m-seg-btn {
  flex: 1;
  height: 36px;
  border: 1px solid var(--el-border-color);
  border-radius: 18px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: 14px;
  transition: background-color 0.2s, color 0.2s;
}

.m-seg-btn.active {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
  font-weight: 600;
}

.m-card {
  background: var(--el-bg-color);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.m-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.m-card-tag {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #fff;
  background: var(--el-color-info);
}

.m-card-tag[data-type='RECEIVE'] { background: var(--el-color-primary); }
.m-card-tag[data-type='BORROW'] { background: #9a67ea; }
.m-card-tag[data-type='TRANSFER'] { background: #e6a23c; }

.m-card-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-card-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.m-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.m-return-btn {
  height: 30px;
  padding: 0 16px;
  border-radius: 15px;
  border: 1px solid var(--el-color-primary);
  background: transparent;
  color: var(--el-color-primary);
  font-size: 13px;
}

.m-return-btn:disabled {
  opacity: 0.5;
}

.m-empty {
  text-align: center;
  padding: 48px 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
</style>
