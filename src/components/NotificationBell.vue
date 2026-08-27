<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationApi } from '@/api/modules/notification'
import type {
  NotificationCategory,
  NotificationItem,
} from '@/api/interface/notification'
import { NOTIFICATION_CATEGORY_META } from '@/api/interface/notification'
import { APPROVAL_BIZ_META } from '@/api/interface/approval'

/**
 * 通知铃铛（右上角）：
 * - 60s 轮询 unread-count，红点 + 未读数字角标（超过 99 显示 99+）
 * - 点击铃铛打开抽屉，分页加载全部未读（可切"全部"）
 * - 点击单条通知：标记已读 + 深链跳对应单据详情
 * - 右上角一键全部标记已读
 */
defineOptions({ name: 'notification-bell' })

const router = useRouter()

/* ---------------- 未读计数轮询 ---------------- */
const unreadCount = ref(0)
const urgentCount = ref(0)
let pollTimer: ReturnType<typeof setInterval> | undefined

const pollUnread = async () => {
  try {
    const resp = await notificationApi.unreadCount()
    unreadCount.value = resp.unreadCount || 0
    urgentCount.value = resp.urgentCount || 0
  } catch {
    /* 401/503 等已由 axios 拦截器统一处理 */
  }
}

/** 立即执行一次（首次挂载展示角标不白等 60s） + 60s 轮询 */
pollUnread()
pollTimer = setInterval(pollUnread, 60 * 1000)
onBeforeUnmount(() => {
  pollTimer && clearInterval(pollTimer)
})

/* ---------------- 抽屉与列表 ---------------- */
const drawerVisible = ref(false)
const loading = ref(false)
const pageSize = 10
const currentPage = ref(1)
const total = ref(0)
const records = ref<NotificationItem[]>([])
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
      unreadOnly: unreadOnly.value,
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

/* 切换未读/全部时重置页码并重载 */
const toggleScope = (val: boolean) => {
  if (val === unreadOnly.value) return
  unreadOnly.value = val
  currentPage.value = 1
  void loadList(true)
}

/* ---------------- 分类标签颜色 ---------------- */
const categoryTag = (c: NotificationCategory) => {
  const m = NOTIFICATION_CATEGORY_META[c]
  if (m.type === 'urgent') return 'warning' as const
  if (m.type === 'result') return 'success' as const
  return 'info' as const
}

const categoryLabel = (c: NotificationCategory) => NOTIFICATION_CATEGORY_META[c].label

/* ---------------- 点击单条：标记已读 + 深链跳转 ---------------- */
const actingId = ref<number>()

const jumpPath = (row: NotificationItem) => {
  if (row.refOrderBiz && APPROVAL_BIZ_META[row.refOrderBiz as keyof typeof APPROVAL_BIZ_META]) {
    const { listPath } =
      APPROVAL_BIZ_META[row.refOrderBiz as keyof typeof APPROVAL_BIZ_META]
    if (row.refOrderId) {
      return { path: listPath, query: { id: String(row.refOrderId) } }
    }
    return { path: listPath }
  }
  return null
}

const handleClick = async (row: NotificationItem) => {
  if (actingId.value) return
  actingId.value = row.id
  try {
    if (!row.read) {
      await notificationApi.markRead(row.id)
      row.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
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

/* ---------------- 一键全部已读 ---------------- */
const markAllRead = async () => {
  if (markingAll.value || unreadCount.value <= 0) return
  markingAll.value = true
  try {
    await notificationApi.markAllRead()
    ElMessage.success('已全部标记为已读')
    unreadCount.value = 0
    urgentCount.value = 0
    records.value.forEach((r) => (r.read = true))
  } catch {
    /* 拦截器已提示 */
  } finally {
    markingAll.value = false
  }
}

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
        <el-icon :size="18" color="#4e5969">
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
        <!-- 顶部切换：只看未读 / 全部 -->
        <div class="scope-switch">
          <button
            type="button"
            class="scope-btn"
            :class="{ active: unreadOnly }"
            @click="toggleScope(true)"
          >
            只看未读<span class="scope-count">{{ unreadCount }}</span>
          </button>
          <button
            type="button"
            class="scope-btn"
            :class="{ active: !unreadOnly }"
            @click="toggleScope(false)"
          >
            全部
          </button>
        </div>

        <!-- 列表 -->
        <ul v-loading="loading" class="notif-list">
          <li
            v-for="row in records"
            :key="row.id"
            class="notif-item"
            :class="{ unread: !row.read, dim: row.read }"
            :disabled="actingId === row.id"
            @click="handleClick(row)"
          >
            <div class="notif-top">
              <el-tag :type="categoryTag(row.category)" effect="light" size="small">
                {{ categoryLabel(row.category) }}
              </el-tag>
              <span class="notif-time">{{ formatTime(row.createdAt) }}</span>
            </div>
            <div class="notif-title" :class="{ 'is-bold': !row.read }">{{ row.title }}</div>
            <div class="notif-content">{{ row.content }}</div>
          </li>
          <li v-if="!loading && records.length === 0" class="notif-empty">
            {{ unreadOnly ? '暂无未读通知' : '暂无通知' }}
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
  background: #f2f3f5;
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
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  flex: 0 0 auto;
  margin-right: 8px;
}
.all-read-tip {
  font-size: 12px;
  color: #00b42a;
  margin-right: 4px;
}

/* ---------- 抽屉 body ---------- */
.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.scope-switch {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: #f7f8fa;
  border-radius: 8px;
  align-self: flex-start;
}

.scope-btn {
  border: none;
  background: transparent;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.scope-btn:hover {
  color: #165dff;
}
.scope-btn.active {
  background: #ffffff;
  color: #165dff;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.scope-count {
  font-size: 12px;
  color: #86909c;
}
.scope-btn.active .scope-count {
  color: #165dff;
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
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.notif-item:hover {
  border-color: #b8d4ff;
  background: #f5faff;
}
.notif-item.unread {
  border-color: #e5c28a;
  background: #fff9f0;
  position: relative;
}
.notif-item.unread::before {
  content: '';
  position: absolute;
  top: 10px;
  left: 6px;
  width: 6px;
  height: 6px;
  background: #f53f3f;
  border-radius: 50%;
}
.notif-item.unread {
  padding-left: 18px;
}
.notif-item.dim {
  opacity: 0.75;
}

.notif-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.notif-time {
  font-size: 12px;
  color: #86909c;
}

.notif-title {
  font-size: 14px;
  color: #111827;
  line-height: 20px;
  margin-bottom: 4px;
}
.notif-title.is-bold {
  font-weight: 600;
}

.notif-content {
  font-size: 13px;
  color: #4e5969;
  line-height: 18px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notif-empty {
  list-style: none;
  text-align: center;
  color: #86909c;
  font-size: 13px;
  padding: 40px 0;
  border: 1px dashed #e5e7eb;
  border-radius: 8px;
}

/* ---------- 加载更多 ---------- */
.load-more-wrap {
  margin-top: 4px;
}
.no-more {
  text-align: center;
  color: #86909c;
  font-size: 12px;
  padding: 12px 0;
}
</style>
