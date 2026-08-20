<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import type { MeInfo } from '@/api/interface'

const userStore = useUserStore()
const loading = ref(true)
const failed = ref(false)
const me = ref<MeInfo | null>(null)

onMounted(async () => {
  try {
    me.value = await userStore.loadMe()
    if (!me.value) failed.value = true
  } catch {
    // 401/403/503 已由 axios 拦截器统一提示/跳转，这里只标记失败态
    failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="workbench">
    <template v-if="me">
      <div class="columns">
        <!-- 左列：最近使用 / 我的收藏 / 快捷入口 -->
        <div class="left-col">
          <div class="card recent-card">
            <div class="card-title">最近使用</div>
            <div class="recent-item">
              <div class="recent-icon">
                <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <rect x="10" y="6" width="20" height="26" rx="3" stroke="#165DFF" stroke-width="1.5" />
                  <rect x="14" y="12" width="12" height="1.5" rx="0.75" fill="#165DFF" />
                  <rect x="14" y="17" width="12" height="1.5" rx="0.75" fill="#165DFF" />
                  <rect x="14" y="22" width="8" height="1.5" rx="0.75" fill="#165DFF" />
                </svg>
              </div>
              <span class="recent-text">物品领用记录</span>
            </div>
            <div class="recent-item">
              <div class="recent-icon">
                <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <rect x="8" y="6" width="24" height="28" rx="3" stroke="#14C9C9" stroke-width="1.5" />
                  <rect x="13" y="13" width="8" height="1.5" rx="0.75" fill="#14C9C9" />
                  <rect x="13" y="19" width="8" height="1.5" rx="0.75" fill="#14C9C9" />
                  <rect x="13" y="25" width="8" height="1.5" rx="0.75" fill="#14C9C9" />
                  <path
                    d="M24 16L26 18L30 13" stroke="#14C9C9" stroke-width="1.5"
                    stroke-linecap="round" stroke-linejoin="round"
                  />
                </svg>
              </div>
              <span class="recent-text">盘点管理</span>
            </div>
          </div>

          <div class="card fav-card">
            <div class="title-row">
              <span class="card-title">我的收藏</span>
              <span class="action">+ 添加</span>
            </div>
            <div class="empty-state">
              <span class="empty-text">暂无收藏</span>
              <span class="empty-action">添加</span>
            </div>
          </div>

          <div class="card quick-card">
            <div class="card-title">快捷入口</div>
            <div class="empty-text">暂无设置快捷入口</div>
          </div>
        </div>

        <!-- 右列：我的待办 / 我的应用 / 我的图表 -->
        <div class="right-col">
          <div class="card todo-card">
            <div class="todo-header">
              <div class="todo-left">
                <div class="todo-icon">✓</div>
                <span class="todo-title">我的待办</span>
              </div>
              <div class="todo-right">
                <div class="todo-item">
                  <div class="todo-item-icon" style="background: #e8f3ff; color: #165dff">→</div>
                  <span class="todo-item-text">我发起的</span>
                </div>
                <div class="todo-item">
                  <div class="todo-item-icon" style="background: #e8ffea; color: #00b42a">✓</div>
                  <span class="todo-item-text">我处理的</span>
                </div>
                <div class="todo-item">
                  <div class="todo-item-icon" style="background: #fff7e8; color: #f7ba1e">@</div>
                  <span class="todo-item-text">抄送我的</span>
                </div>
                <div class="todo-item">
                  <div class="todo-item-icon" style="background: #f5e8ff; color: #722ed1">+</div>
                  <span class="todo-item-text">发起流程</span>
                </div>
              </div>
            </div>
          </div>

          <div class="card app-card">
            <div class="app-title">我的应用</div>
            <div class="app-section-title">资产管理</div>
            <div class="app-row">
              <div class="app-item">
                <div class="app-icon-wrap">
                  <svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <circle cx="24" cy="12" r="6.5" stroke="#165DFF" stroke-width="2.6" />
                    <path d="M24 19V32" stroke="#165DFF" stroke-width="2.6" stroke-linecap="round" />
                    <path d="M16 40L24 31L32 40" stroke="#165DFF" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" />
                    <path d="M15 25L24 21L33 25" stroke="#165DFF" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </div>
                <span class="app-label">资产管理</span>
              </div>
              <div class="app-item">
                <div class="app-icon-wrap">
                  <svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="10" y="10" width="28" height="16" rx="2" stroke="#14C9C9" stroke-width="2.4" />
                    <rect x="10" y="26" width="28" height="12" rx="2" stroke="#14C9C9" stroke-width="2.4" />
                    <rect x="14" y="16" width="20" height="2" rx="1" fill="#14C9C9" />
                    <rect x="14" y="31" width="20" height="2" rx="1" fill="#14C9C9" />
                  </svg>
                </div>
                <span class="app-label">库存管理</span>
              </div>
            </div>

            <!-- 鉴权全链路验证（M-FE01 完成标准）：当前用户信息来自 GET /api/v1/me -->
            <div class="me-panel">
              <div class="me-panel-title">登录信息</div>
              <div class="me-grid">
                <div class="me-field">
                  <span class="me-label">姓名</span>
                  <span class="me-value">{{ me.name || '-' }}</span>
                </div>
                <div class="me-field">
                  <span class="me-label">部门</span>
                  <span class="me-value">{{ me.dept || '-' }}</span>
                </div>
                <div class="me-field">
                  <span class="me-label">岗位</span>
                  <span class="me-value">{{ me.job || '-' }}</span>
                </div>
                <div class="me-field">
                  <span class="me-label">角色</span>
                  <span class="me-value">{{ me.roles?.length ? me.roles.join('、') : '未分配' }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="card chart-card">
            <div class="title-row">
              <span class="card-title">我的图表</span>
              <span class="action">+ 添加</span>
            </div>
            <div class="chart-empty">
              <span class="empty-text">暂无图表</span>
              <span class="empty-action">添加</span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <el-result
      v-else-if="failed && !loading"
      icon="warning"
      title="用户信息加载失败"
      sub-title="请检查后端服务与登录状态，或重新从应用中心进入"
    />
  </div>
</template>

<style scoped>
.workbench {
  min-height: 100%;
}

.columns {
  display: flex;
  gap: 24px;
  min-height: calc(100vh - 56px - 48px);
}

.card {
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(217, 222, 232, 0.3);
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #1d2129;
  line-height: 22px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-row .action {
  font-size: 12px;
  font-weight: 400;
  color: #165dff;
  line-height: 20px;
  cursor: pointer;
}

.empty-state {
  display: flex;
  align-items: center;
  gap: 6px;
}

.empty-text {
  font-size: 13px;
  font-weight: 400;
  color: #86909c;
  line-height: 22px;
}

.empty-action {
  font-size: 13px;
  font-weight: 400;
  color: #165dff;
  line-height: 22px;
  cursor: pointer;
}

/* 左列 */
.left-col {
  width: 256px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex-shrink: 0;
}

.recent-card {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100px;
  cursor: pointer;
}

.recent-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recent-text {
  font-size: 16px;
  font-weight: 400;
  color: #4e5969;
  line-height: 24px;
}

.fav-card,
.quick-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 右列 */
.right-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 0;
}

.todo-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex-shrink: 0;
}

.todo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 0;
}

.todo-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.todo-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 20px;
  font-weight: 700;
  line-height: 28px;
}

.todo-title {
  font-size: 16px;
  font-weight: 500;
  color: #1d2129;
  line-height: 24px;
}

.todo-right {
  flex: 1;
  display: flex;
  align-items: center;
  min-width: 0;
}

.todo-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding-bottom: 4px;
  cursor: pointer;
}

.todo-item-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
}

.todo-item-text {
  font-size: 13px;
  font-weight: 400;
  color: #4e5969;
  line-height: 22px;
}

.app-card {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
}

.app-title {
  font-size: 16px;
  font-weight: 500;
  color: #1d2129;
  line-height: 24px;
}

.app-section-title {
  font-size: 13px;
  font-weight: 400;
  color: #86909c;
  line-height: 22px;
}

.app-row {
  display: flex;
  gap: 32px;
  align-items: center;
}

.app-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.app-icon-wrap {
  width: 77px;
  height: 77px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-icon {
  width: 48px;
  height: 48px;
  display: block;
}

.app-label {
  font-size: 13px;
  font-weight: 400;
  color: #1d2129;
  line-height: 22px;
}

/* 登录信息面板（M-FE01 鉴权验证） */
.me-panel {
  margin-top: auto;
  border-top: 1px solid #f0f2f5;
  padding-top: 16px;
}

.me-panel-title {
  font-size: 13px;
  font-weight: 400;
  color: #86909c;
  line-height: 22px;
  margin-bottom: 12px;
}

.me-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 48px;
}

.me-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.me-label {
  font-size: 12px;
  color: #86909c;
  line-height: 20px;
}

.me-value {
  font-size: 14px;
  color: #1d2129;
  line-height: 22px;
}

.chart-card {
  height: 140px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-shrink: 0;
}

.chart-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
</style>
