<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationApi } from '@/api/modules/notification'
import type { NotificationItem } from '@/api/interface/notification'
import { NOTIFICATION_TYPE_TAG } from '@/api/interface/notification'
import { useNotificationStore } from '@/stores/notification'

/**
 * 通知铃铛（本组件在顶栏与左下角各挂载一个实例）：
 * - 未读数来自共享 store（两实例红点实时同步），60s 轮询由 store 引用计数管理
 * - 点击铃铛打开抽屉：「全部 / 抄送我的」两个视图 + 只看未读开关
 * - 点击单条通知：标记已读 + 深链跳对应单据详情（RETURN 且无 bizId 的退还审批不跳转）
 * - 右上角一键全部标记已读（会把抄送通知一并标读，后端暂无按 type 批量已读）
 */
defineOptions({ name: 'notification-bell' })

const router = useRouter()
const notifStore = useNotificationStore()

/* ---------------- 未读计数（共享 store：与另一处铃铛同步） ---------------- */
const unreadCount = computed(() => notifStore.unreadCount)

/* 挂载即绑定共享轮询，卸载解绑（最后一个实例卸载时轮询停止） */
let unbindPolling: (() => void) | undefined
onMounted(() => {
  unbindPolling = notifStore.bindPolling()
})
onBeforeUnmount(() => {
  unbindPolling?.()
})

/* ---------------- 抽屉与列表 ---------------- */
const drawerVisible = ref(false)
const loading = ref(false)
const pageSize = 10
const currentPage = ref(1)
const total = ref(0)
const records = ref<NotificationItem[]>([])
/** 视图 tab：all=全部通知，cc=抄送我的（type=DOC_CC） */
const activeTab = ref<'all' | 'cc'>('all')
const unreadOnly = ref(true)
const markingAll = ref(false)

/** 红点显示阈值：>=1 即展示 */
const badgeCount = computed(() => {
  const n = unreadCount.value
  if (n <= 0) return 0
  return n > 99 ? 99 : n
})

/** 铃铛打开时立即重拉一次最新计数 + 列表 */
watch(drawerVisible, async (v) => {
  if (v) {
    void notifStore.refreshCount()
    currentPage.value = 1
    await loadList(true)
  }
})

const loadList = async (reset = false) => {
  loading.value = true
  try {
    const resp = await notificationApi.list({
      page: currentPage.value,
      size: pageSize,
      unread: unreadOnly.value,
      ...(activeTab.value === 'cc' ? { type: 'DOC_CC' } : {}),
    })
    total.value = resp.total
    records.value = reset ? resp.records : [...records.value, ...resp.records]
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

/** 翻页加载下一页（滚动到列表底部时触发；简化版：按钮"加载更多"） */
const hasMore = computed(() => records.value.length < total.value)

const loadMore = () => {
  if (loading.value || !hasMore.value) return
  currentPage.value += 1
  void loadList()
}

/* 切换视图 tab（全部 / 抄送我的）时重置页码并重载 */
const switchTab = (tab: 'all' | 'cc') => {
  if (tab === activeTab.value) return
  activeTab.value = tab
  currentPage.value = 1
  void loadList(true)
}

/* 切换未读/全部时重置页码并重载 */
const toggleUnread = (val: boolean) => {
  if (val === unreadOnly.value) return
  unreadOnly.value = val
  currentPage.value = 1
  void loadList(true)
}

/* ---------------- 类型标签颜色（文案 typeLabel 由后端返回） ---------------- */
const typeTag = (t: string) => NOTIFICATION_TYPE_TAG[t] ?? 'info'

/* ---------------- 点击单条：标记已读 + 深链跳转 ---------------- */
const actingId = ref<number>()

/** 单据类型 → 列表页路径映射（含审批四类 + 盘点） */
const BIZ_LIST_PATHS: Record<string, string> = {
  RECEIVE: '/receipts/receive',
  BORROW: '/receipts/borrow',
  TRANSFER: '/transfers',
  CHANGE: '/changes',
  STOCKTAKE: '/stocktakes',
}

const jumpPath = (row: NotificationItem) => {
  /* RETURN 且 bizId=0 为退还审批（无系统单据），点击不跳转 */
  if (!row.bizType || !row.bizId) return null
  const listPath = BIZ_LIST_PATHS[row.bizType]
  if (!listPath) return null
  return { path: listPath, query: { id: String(row.bizId) } }
}

const handleClick = async (row: NotificationItem) => {
  if (actingId.value) return
  actingId.value = row.id
  try {
    if (row.readFlag === 0) {
      await notificationApi.markRead(row.id)
      row.readFlag = 1
      notifStore.decreaseUnread()
    }
    const target = jumpPath(row)
    if (target) {
      drawerVisible.value = false
      router.push(target)
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    actingId.value = undefined
  }
}

/* ---------------- 一键全部已读（含抄送类） ---------------- */
const markAllRead = async () => {
  if (markingAll.value || unreadCount.value <= 0) return
  markingAll.value = true
  try {
    await notificationApi.markAllRead()
    ElMessage.success('已全部标记为已读')
    notifStore.clearUnread()
    records.value.forEach((r) => (r.readFlag = 1))
  } catch {
    /* 拦截器已提示 */
  } finally {
    markingAll.value = false
  }
}

/* ---------------- 空态文案 ---------------- */
const emptyText = computed(() => {
  if (activeTab.value === 'cc') return unreadOnly.value ? '暂无未读抄送' : '暂无抄送通知'
  return unreadOnly.value ? '暂无未读通知' : '暂无通知'
})

/* ---------------- 时间格式化 ---------------- */
const formatTime = (s: string) => {
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <div class="bell-wrap">
    <!-- 铃铛按钮 + 角标 -->
    <el-button class="bell-btn" circle text @click="drawerVisible = true" aria-label="通知中心">
      <el-badge :value="badgeCount" :hidden="badgeCount <= 0" :max="99" class="bell-badge">
        <el-icon :size="18" color="var(--color-text-2)">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
            <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
          </svg>
        </el-icon>
      </el-badge>
    </el-button>

    <!-- 抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="通知中心"
      direction="rtl"
      :size="380"
      destroy-on-close
    >
      <template #header="{ titleId, closeBtn }">
        <div class="drawer-header">
          <div :id="titleId" class="drawer-title">通知中心</div>
          <el-button
            v-if="unreadCount > 0"
            size="small"
            type="primary"
            link
            :loading="markingAll"
            @click="markAllRead"
          >
            全部标记已读
          </el-button>
          <span v-else class="all-read-tip">全部已读</span>
          {{ closeBtn }}
        </div>
      </template>

      <div class="drawer-body">
        <!-- 顶部：视图 tab（全部 / 抄送我的）+ 只看未读开关 -->
        <div class="filter-bar">
          <div class="view-switch">
            <button
              type="button"
              class="view-btn"
              :class="{ active: activeTab === 'all' }"
              @click="switchTab('all')"
            >
              全部
            </button>
            <button
              type="button"
              class="view-btn"
              :class="{ active: activeTab === 'cc' }"
              @click="switchTab('cc')"
            >
              抄送我的
            </button>
          </div>
          <button
            type="button"
            class="unread-toggle"
            :class="{ active: unreadOnly }"
            @click="toggleUnread(!unreadOnly)"
          >
            只看未读<span class="scope-count">{{ unreadCount }}</span>
          </button>
        </div>

        <!-- 列表 -->
        <ul v-loading="loading" class="notif-list">
          <li
            v-for="row in records"
            :key="row.id"
            class="notif-item"
            :class="{ unread: row.readFlag === 0, dim: row.readFlag === 1 }"
            :disabled="actingId === row.id"
            @click="handleClick(row)"
          >
            <div class="notif-top">
              <el-tag :type="typeTag(row.type)" effect="light" size="small">
                {{ row.typeLabel }}
              </el-tag>
              <span class="notif-time">{{ formatTime(row.createdAt) }}</span>
            </div>
            <div class="notif-title" :class="{ 'is-bold': row.readFlag === 0 }">{{ row.title }}</div>
          </li>
          <li v-if="!loading && records.length === 0" class="notif-empty">
            {{ emptyText }}
          </li>
        </ul>

        <!-- 加载更多按钮 -->
        <div v-if="records.length > 0" class="load-more-wrap">
          <el-button
            v-if="hasMore"
            type="primary"
            plain
            :loading="loading"
            :disabled="loading"
            block
            @click="loadMore"
          >
            加载更多
          </el-button>
          <div v-else class="no-more">— 没有更多了 —</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.bell-wrap {
  display: inline-flex;
  align-items: center;
}

.bell-btn {
  width: 36px;
  height: 36px;
  padding: 0;
  border-radius: 50%;
  transition: background 0.15s ease;
}
.bell-btn:hover {
  background: var(--color-bg-3);
}

.bell-badge :deep(.el-badge__content) {
  border: none;
}

/* ---------- 抽屉 header 自定义 ---------- */
.drawer-header {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
}
.drawer-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-1);
  flex: 0 0 auto;
  margin-right: 8px;
}
.all-read-tip {
  font-size: var(--text-xs);
  color: var(--color-success-text);
  margin-right: 4px;
}

/* ---------- 抽屉 body ---------- */
.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ---------- 筛选栏：tab + 未读开关 ---------- */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.view-switch {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: var(--color-bg-1);
  border-radius: var(--radius-lg);
}

.view-btn {
  border: none;
  background: transparent;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  color: var(--color-text-2);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.view-btn:hover {
  color: var(--color-primary);
}
.view-btn.active {
  background: var(--color-bg-2);
  color: var(--color-primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

.unread-toggle {
  border: none;
  background: transparent;
  padding: 8px 8px;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  color: var(--color-text-3);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.unread-toggle:hover {
  color: var(--color-primary);
}
.unread-toggle.active {
  color: var(--color-primary);
  font-weight: 600;
}
.scope-count {
  font-size: var(--text-xs);
  color: var(--color-text-3);
}
.unread-toggle.active .scope-count {
  color: var(--color-primary);
}

/* ---------- 通知列表 ---------- */
.notif-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notif-item {
  padding: 12px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.15s ease;
}
.notif-item:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-bg);
}
.notif-item.unread {
  border-color: var(--color-warning-border);
  background: var(--color-warning-bg);
  position: relative;
}
.notif-item.unread::before {
  content: '';
  position: absolute;
  top: 10px;
  left: 6px;
  width: 6px;
  height: 6px;
  background: var(--color-error);
  border-radius: 50%;
}
.notif-item.unread {
  padding-left: 16px;
}
.notif-item.dim {
  opacity: 0.75;
}

.notif-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.notif-time {
  font-size: var(--text-xs);
  color: var(--color-text-3);
}

.notif-title {
  font-size: var(--text-base);
  color: var(--color-text-1);
  line-height: 20px;
}
.notif-title.is-bold {
  font-weight: 600;
}

.notif-empty {
  list-style: none;
  text-align: center;
  color: var(--color-text-3);
  font-size: var(--text-sm);
  padding: 40px 0;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
}

/* ---------- 加载更多 ---------- */
.load-more-wrap {
  margin-top: 4px;
}
.no-more {
  text-align: center;
  color: var(--color-text-3);
  font-size: var(--text-xs);
  padding: 12px 0;
}
</style>
