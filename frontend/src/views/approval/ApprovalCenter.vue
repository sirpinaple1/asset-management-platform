<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Component } from 'vue'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import { useNotificationStore } from '@/stores/notification'
import { receiptApi } from '@/api/modules/receipt'
import { transferApi } from '@/api/modules/transfer'
import { changeApi } from '@/api/modules/change'
import { notificationApi } from '@/api/modules/notification'
import type { NotificationItem } from '@/api/interface/notification'
import type { ApprovalBizType, ApprovalItem, ApprovalTabKey } from '@/api/interface/approval'
import {
  APPROVAL_BIZ_META,
  APPROVAL_TAB_META,
  approvalStatusTag,
  isHandledBy,
  isMine,
  isTodoFor,
  todoBucketOf,
} from '@/api/interface/approval'
import IconDocReceive from '@/components/icons/IconDocReceive.vue'
import IconDocBorrow from '@/components/icons/IconDocBorrow.vue'
import IconDocTransfer from '@/components/icons/IconDocTransfer.vue'
import IconDocChange from '@/components/icons/IconDocChange.vue'
import IconDocReturn from '@/components/icons/IconDocReturn.vue'

/**
 * 审批中心：M04 领用/借用 + M05 调拨 + M06 变更三类单据的统一处理入口（共享池语义——
 * 后端无指定审批人模型，PENDING 单据任何非发起人可处理；变更单允许发起人自审）。
 * 三 tab（待我处理/我发起的/我处理的）+ 类型筛选 + 关键词搜索 + 前端分页 + 深链 query 同步；
 * 行内快速通过/拒绝（仅"待我处理"tab），详情经 ?id= 深链跳各列表页抽屉。
 */
defineOptions({ name: 'approvals-center' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const store = useApprovalStore()
const notifStore = useNotificationStore()

const items = computed(() => store.items)
const loading = computed(() => store.loading)
const meUserId = computed(() => userStore.me?.userId)

/* ---------------- 状态 tabs（前端过滤 + 计数） ---------------- */
const TAB_KEYS: ApprovalTabKey[] = ['todo', 'mine', 'handled', 'cc']
const activeTab = ref<ApprovalTabKey>('todo')

const tabs = computed(() => [
  {
    key: 'todo' as ApprovalTabKey,
    label: APPROVAL_TAB_META.todo.label,
    count: items.value.filter((it) => isTodoFor(it, meUserId.value)).length,
  },
  {
    key: 'mine' as ApprovalTabKey,
    label: APPROVAL_TAB_META.mine.label,
    count: items.value.filter((it) => isMine(it, meUserId.value)).length,
  },
  {
    key: 'handled' as ApprovalTabKey,
    label: APPROVAL_TAB_META.handled.label,
    count: items.value.filter((it) => isHandledBy(it, meUserId.value)).length,
  },
  /* 抄送我的：服务端分页计数，首次加载前不显示数字 */
  {
    key: 'cc' as ApprovalTabKey,
    label: APPROVAL_TAB_META.cc.label,
    count: ccLoaded.value ? ccTotal.value : undefined,
  },
])

/* ---------------- 类型筛选 chips ---------------- */
type TypeKey = 'ALL' | ApprovalBizType
const TYPE_KEYS: TypeKey[] = ['ALL', 'RECEIVE', 'BORROW', 'TRANSFER', 'CHANGE', 'RETURN']
const activeType = ref<TypeKey>('ALL')

const typeChips = computed(() => [
  { key: 'ALL' as TypeKey, label: '全部类型' },
  ...(['RECEIVE', 'BORROW', 'TRANSFER', 'CHANGE', 'RETURN'] as ApprovalBizType[]).map((key) => ({
    key: key as TypeKey,
    label: APPROVAL_BIZ_META[key].label,
  })),
])

/* ---------------- 搜索（防抖 300ms 前端过滤） ---------------- */
const keyword = ref('')
const searchKeyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKeyword.value = val.trim()
    currentPage.value = 1
  }, 300)
})
onBeforeUnmount(() => searchTimer && clearTimeout(searchTimer))

/* ---------------- 抄送我的（DOC_CC 通知，服务端分页） ---------------- */
const CC_PAGE_SIZE = 10
const ccRecords = ref<NotificationItem[]>([])
const ccLoading = ref(false)
const ccTotal = ref(0)
const ccPage = ref(1)
/** 首次加载前 tab 不显示计数 */
const ccLoaded = ref(false)

const loadCc = async (page = 1) => {
  ccLoading.value = true
  try {
    const resp = await notificationApi.list({ page, size: CC_PAGE_SIZE, type: 'DOC_CC' })
    ccPage.value = page
    ccTotal.value = resp.total
    ccRecords.value = resp.records
    ccLoaded.value = true
  } catch {
    /* 拦截器已提示 */
  } finally {
    ccLoading.value = false
  }
}

/* 切到抄送 tab 时加载/刷新（immediate：兼容 ?tab=cc 深链直接进入） */
watch(
  activeTab,
  (tab) => {
    if (tab === 'cc') void loadCc(1)
  },
  { immediate: true },
)
onActivated(() => {
  if (activeTab.value === 'cc') void loadCc(1)
})

/** 抄送条目业务类型文案：四类单据用 meta，RETURN=退还审批，其他原样展示 */
const ccBizLabel = (row: NotificationItem) => {
  if (row.bizType && row.bizType in APPROVAL_BIZ_META) {
    return APPROVAL_BIZ_META[row.bizType as ApprovalBizType].label
  }
  return row.bizType === 'RETURN' ? '退还审批' : row.bizType || '—'
}

/** 抄送条目跳转：RETURN 且 bizId=0 为退还审批（无系统单据），不跳转 */
const ccJumpPath = (row: NotificationItem) => {
  if (!row.bizType || !row.bizId) return null
  const meta = APPROVAL_BIZ_META[row.bizType as ApprovalBizType]
  if (!meta) return null
  return { path: meta.listPath, query: { id: String(row.bizId) } }
}

const ccActingId = ref<number>()

const handleCcRowClick = async (row: NotificationItem) => {
  if (ccActingId.value) return
  const target = ccJumpPath(row)
  if (!target) {
    /* 无系统单据（如退还审批）：仅标记已读，不跳转 */
    if (row.readFlag === 0) {
      try {
        await notificationApi.markRead(row.id)
        row.readFlag = 1
        notifStore.decreaseUnread()
      } catch {
        /* 拦截器已提示 */
      }
    }
    return
  }
  ccActingId.value = row.id
  try {
    if (row.readFlag === 0) {
      await notificationApi.markRead(row.id)
      row.readFlag = 1
      notifStore.decreaseUnread()
    }
    router.push(target)
  } catch {
    /* 拦截器已提示 */
  } finally {
    ccActingId.value = undefined
  }
}

/* ---------------- 前端过滤 + 分页（待我处理分 directed / pool 两池） ---------------- */
const PAGE_SIZE = 10
const currentPage = ref(1)

const todoToMe = computed<ApprovalItem[]>(() =>
  items.value.filter(
    (it) => isTodoFor(it, meUserId.value) && todoBucketOf(it, meUserId.value) === 'directed',
  ),
)
const todoSharedPool = computed<ApprovalItem[]>(() =>
  items.value.filter(
    (it) => isTodoFor(it, meUserId.value) && todoBucketOf(it, meUserId.value) === 'pool',
  ),
)

const filtered = computed(() => {
  const me = meUserId.value
  let list = items.value.filter((it) =>
    activeTab.value === 'todo'
      ? isTodoFor(it, me)
      : activeTab.value === 'mine'
        ? isMine(it, me)
        : isHandledBy(it, me),
  )
  if (activeType.value !== 'ALL') list = list.filter((it) => it.bizType === activeType.value)
  const kw = searchKeyword.value.toLowerCase()
  if (kw) {
    list = list.filter((it) =>
      [it.serialNo, it.applicantName, it.summary].some((field) =>
        field?.toLowerCase().includes(kw),
      ),
    )
  }
  return list
})

const total = computed(() => filtered.value.length)

const pageData = computed(() =>
  filtered.value.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE),
)

/* tab/过滤结果变化时纠正越界页码 */
watch([activeTab, activeType, total], () => {
  const maxPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})

/* ---------------- 深链与刷新保持：tab/类型/搜索词/页码同步 URL query ---------------- */
watch(
  () => route.query,
  (q) => {
    if (route.name !== 'approvals-center') return
    const tab = typeof q.tab === 'string' ? q.tab : ''
    if (TAB_KEYS.includes(tab as ApprovalTabKey)) {
      const nextTab = tab as ApprovalTabKey
      if (nextTab !== activeTab.value) activeTab.value = nextTab
    }
    const type = typeof q.type === 'string' ? q.type : ''
    if (TYPE_KEYS.includes(type as TypeKey)) {
      const nextType = type as TypeKey
      if (nextType !== activeType.value) activeType.value = nextType
    }
    const kw = typeof q.q === 'string' ? q.q : ''
    if (kw !== keyword.value) keyword.value = kw
    const p = Number(q.page)
    if (Number.isInteger(p) && p >= 1 && p !== currentPage.value) currentPage.value = p
  },
  { immediate: true },
)

watch([activeTab, activeType, searchKeyword, currentPage], () => {
  if (route.name !== 'approvals-center') return
  const query: Record<string, string> = {}
  if (activeTab.value !== 'todo') query.tab = activeTab.value
  if (activeType.value !== 'ALL') query.type = activeType.value
  if (searchKeyword.value) query.q = searchKeyword.value
  if (currentPage.value > 1) query.page = String(currentPage.value)
  const current = JSON.stringify(route.query)
  const next = JSON.stringify(query)
  if (current !== next) router.replace({ query })
})

/* ---------------- 数据加载 ---------------- */
/* keep-alive：首次挂载先取当前用户（tab 过滤依赖 userId）再拉聚合列表；
   之后每次切回本页刷新（单据状态可能已被他人变更） */
let firstActivation = true
onMounted(async () => {
  try {
    await userStore.loadMe()
  } catch {
    /* 401/403/503 已由 axios 拦截器统一提示/跳转 */
  }
  void store.refresh()
})
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  void store.refresh()
})

/* ---------------- 行内快捷处理（仅"待我处理"tab） ---------------- */
const actingId = ref<number>()

/** 快捷通过按钮文案：领用/借用=通过、调拨=确认、变更=执行 */
const approveText = (bizType: ApprovalBizType) =>
  bizType === 'TRANSFER' ? '确认' : bizType === 'CHANGE' ? '执行' : '通过'

const handleApprove = async (row: ApprovalItem) => {
  if (actingId.value) return /* 防重复提交 */
  actingId.value = row.bizId
  try {
    if (row.bizType === 'RECEIVE' || row.bizType === 'BORROW') {
      await receiptApi.approve(row.bizId)
    } else if (row.bizType === 'TRANSFER') {
      await transferApi.confirm(row.bizId)
    } else {
      await changeApi.confirm(row.bizId)
    }
    ElMessage.success('处理成功')
    await store.refresh() /* 计数联动（侧边栏角标/工作台待办卡） */
  } catch {
    /* 业务/网络错误已由 axios 拦截器统一提示 */
  } finally {
    actingId.value = undefined
  }
}

/** 拒绝：必填原因（变更单无拒绝，撤销走详情抽屉） */
const handleReject = async (row: ApprovalItem) => {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `拒绝单据「${row.serialNo}」后将驳回发起人申请，请填写拒绝原因。`,
      '拒绝确认',
      {
        confirmButtonText: '拒绝',
        cancelButtonText: '取消',
        inputPlaceholder: '拒绝原因（必填）',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写拒绝原因'),
      },
    )
    reason = value.trim()
  } catch {
    return /* 用户取消 */
  }
  if (actingId.value) return
  actingId.value = row.bizId
  try {
    if (row.bizType === 'TRANSFER') await transferApi.reject(row.bizId, reason)
    else await receiptApi.reject(row.bizId, reason)
    ElMessage.success('已拒绝')
    await store.refresh()
  } catch {
    /* 拦截器已提示 */
  } finally {
    actingId.value = undefined
  }
}

/* 详情：深链跳对应列表页，?id= 自动打开详情抽屉（钉钉退还单无系统单据，不跳转） */
const goDetail = (row: ApprovalItem) => {
  const meta = APPROVAL_BIZ_META[row.bizType]
  if (!meta.listPath) return
  router.push({ path: meta.listPath, query: { id: String(row.bizId) } })
}

/* ---------------- 输入与触发：Ctrl+F 聚焦搜索 ---------------- */
const searchInputEl = ref<HTMLElement>()

const onWindowKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
    e.preventDefault()
    searchInputEl.value?.querySelector('input')?.focus()
  }
}
onMounted(() => window.addEventListener('keydown', onWindowKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onWindowKeydown))

/* ---------------- 展示工具 ---------------- */
const BIZ_ICONS: Record<ApprovalBizType, Component> = {
  RECEIVE: IconDocReceive,
  BORROW: IconDocBorrow,
  TRANSFER: IconDocTransfer,
  CHANGE: IconDocChange,
  RETURN: IconDocReturn,
}

const bizLabel = (row: ApprovalItem) => APPROVAL_BIZ_META[row.bizType].label

const applicantText = (row: ApprovalItem) => row.applicantName || String(row.applicantUserId)

const statusTagOf = (row: ApprovalItem) => approvalStatusTag(row)

/** 定向标签：待我处理 tab 下按 assignee 展示，@我 或 指定给xxx 或共享池 */
const todoBucketTag = (row: ApprovalItem) => {
  const bucket = todoBucketOf(row, meUserId.value)
  if (bucket === 'directed') return { label: '指定处理人是我', type: 'warning' as const }
  return { label: '共享池', type: 'info' as const }
}

const emptyText = computed(() =>
  activeTab.value === 'todo'
    ? '暂无待处理单据'
    : activeTab.value === 'mine'
      ? '暂无我发起的单据'
      : activeTab.value === 'cc'
        ? '暂无抄送我的记录'
        : '暂无我处理的单据',
)
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">审批中心</h2>
        <span class="page-subtitle">
          待办按 assignee 语义拆分：
          <strong>定向给我</strong>（{{ todoToMe.length }}）
          · <strong>共享池</strong>（{{ todoSharedPool.length }}，任何有权限用户均可处理）
        </span>
      </div>

      <!-- 状态 tabs -->
      <div class="tabs">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <span>{{ tab.label }}</span>
          <span v-if="tab.count !== undefined" class="tab-count">({{ tab.count }})</span>
        </div>
      </div>

      <!-- 工具栏：类型筛选 chips + 搜索（抄送 tab 为通知列表，不适用） -->
      <div v-if="activeTab !== 'cc'" class="toolbar">
        <div class="type-chips">
          <button
            v-for="chip in typeChips"
            :key="chip.key"
            type="button"
            class="type-chip"
            :class="{ active: activeType === chip.key }"
            @click="activeType = chip.key"
          >
            {{ chip.label }}
          </button>
        </div>
        <el-input
          ref="searchInputEl"
          v-model="keyword"
          class="search-box"
          placeholder="搜索单号 / 申请人 / 摘要（Ctrl+F）"
          clearable
        />
      </div>

      <!-- 聚合表格（todo/mine/handled） -->
      <el-table
        v-if="activeTab !== 'cc'"
        v-loading="loading"
        :data="pageData"
        row-key="bizId"
        border
        highlight-current-row
        :empty-text="emptyText"
      >
        <el-table-column label="类型" width="92">
          <template #default="{ row }">
            <span class="biz-cell">
              <component :is="BIZ_ICONS[row.bizType as ApprovalBizType]" :size="16" />
              <span>{{ bizLabel(row) }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="serialNo" label="单号" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusTagOf(row).tagType" effect="light">
              {{ statusTagOf(row).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'todo'" label="待办分区" width="140">
          <template #default="{ row }">
            <el-tag :type="todoBucketTag(row).type" effect="plain">
              {{ todoBucketTag(row).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请人" min-width="110">
          <template #default="{ row }">{{ applicantText(row) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="申请时间" min-width="160" />
        <el-table-column label="操作" :width="activeTab === 'todo' ? 168 : 90" fixed="right">
          <template #default="{ row }">
            <template v-if="activeTab === 'todo'">
              <el-button
                link
                type="success"
                :loading="actingId === row.bizId"
                @click="handleApprove(row)"
              >
                {{ approveText(row.bizType) }}
              </el-button>
              <el-button
                v-if="row.bizType !== 'CHANGE'"
                link
                type="danger"
                :disabled="actingId === row.bizId"
                @click="handleReject(row)"
              >
                拒绝
              </el-button>
            </template>
            <el-button v-if="row.bizType !== 'RETURN'" link type="primary" @click="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 抄送我的表格（DOC_CC 通知，服务端分页；点击行标已读并深链单据详情） -->
      <div v-else class="cc-table-wrap">
        <el-table
          v-loading="ccLoading"
          :data="ccRecords"
          row-key="id"
          border
          highlight-current-row
          empty-text="暂无抄送我的记录"
          @row-click="handleCcRowClick"
        >
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag type="info" effect="light" size="small">{{ row.typeLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="{ 'cc-title-unread': row.readFlag === 0 }">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="单据类型" width="100">
            <template #default="{ row }">{{ ccBizLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="已读状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.readFlag === 0 ? 'danger' : 'info'" effect="plain" size="small">
                {{ row.readFlag === 0 ? '未读' : '已读' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="抄送时间" min-width="160" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="!ccJumpPath(row)"
                @click.stop="handleCcRowClick(row)"
              >
                查看
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div v-if="activeTab !== 'cc'" class="pagination">
        <span class="pagination-info">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="PAGE_SIZE"
          :total="total"
          layout="prev, pager, next"
          background
        />
      </div>
      <div v-else class="pagination">
        <span class="pagination-info">共 {{ ccTotal }} 条</span>
        <el-pagination
          v-model:current-page="ccPage"
          :page-size="CC_PAGE_SIZE"
          :total="ccTotal"
          layout="prev, pager, next"
          background
          @current-change="loadCc"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  background: var(--color-bg-2);
  border-radius: var(--radius-xl);
  padding: 24px;
  box-shadow: var(--shadow-md);
}

.page-header {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-1);
  margin: 0;
}

.page-subtitle {
  font-size: var(--text-sm);
  color: var(--color-text-3);
  flex: 1;
}

/* tabs（对齐其他列表页：胶囊样式） */
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.tab {
  height: 34px;
  padding: 0 12px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-base);
  background: var(--color-bg-3);
  color: var(--color-text-2);
  cursor: pointer;
  user-select: none;
}

.tab.active {
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.tab-count {
  font-size: var(--text-sm);
}

/* 工具栏：类型 chips + 搜索 */
.toolbar {
  min-height: 56px;
  padding: 12px 16px;
  background: var(--color-bg-3);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.type-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.type-chip {
  height: 30px;
  padding: 0 12px;
  border-radius: 15px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-2);
  font-size: var(--text-sm);
  color: var(--color-text-2);
  cursor: pointer;
  transition: all 0.15s ease;
}

.type-chip:hover {
  color: var(--color-primary);
  border-color: var(--color-primary-border);
}

.type-chip.active {
  background: var(--color-primary-bg);
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: 500;
}

.search-box {
  width: 280px;
  flex-shrink: 0;
}

/* 类型列：图标 + 文字 */
.biz-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-2);
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.pagination-info {
  font-size: var(--text-sm);
  color: var(--color-text-2);
}

/* 抄送我的表格：行可点击 + 未读标题加粗 */
.cc-table-wrap :deep(.el-table__row) {
  cursor: pointer;
}
.cc-title-unread {
  font-weight: 600;
  color: var(--color-text-1);
}
</style>
