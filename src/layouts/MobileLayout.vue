<template>
  <div class="m-layout">
    <header class="m-header">
      <span class="m-header-title">资产管理系统</span>
      <span v-if="userStore.displayName" class="m-header-user">{{ userStore.displayName }}</span>
    </header>

    <main class="m-body">
      <router-view />
    </main>

    <nav class="m-tabbar">
      <router-link
        v-for="tab in TABS"
        :key="tab.path"
        :to="tab.path"
        class="m-tab"
        :class="{ active: route.path === tab.path }"
      >
        <el-badge
          v-if="tab.badge && approvalStore.todoCount > 0"
          :value="approvalStore.todoCount > 99 ? '99+' : approvalStore.todoCount"
          :offset="[-2, 2]"
        >
          <span class="m-tab-icon">{{ tab.icon }}</span>
        </el-badge>
        <span v-else class="m-tab-icon">{{ tab.icon }}</span>
        <span class="m-tab-label">{{ tab.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'

const route = useRoute()
const userStore = useUserStore()
const approvalStore = useApprovalStore()

const TABS = [
  { path: '/m/approvals', label: '审批', icon: '📋', badge: true },
  { path: '/m/assets', label: '我的资产', icon: '📦', badge: false },
  { path: '/m/scan', label: '扫码', icon: '📷', badge: false },
] as const

onMounted(() => {
  void userStore.loadMe()
  void approvalStore.refresh()
})
</script>

<style scoped>
.m-layout {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: var(--el-bg-color-page);
}

.m-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 46px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.m-header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.m-header-user {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  max-width: 40vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.m-tabbar {
  flex-shrink: 0;
  display: flex;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-light);
  padding-bottom: env(safe-area-inset-bottom);
}

.m-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 7px 0 5px;
  text-decoration: none;
  color: var(--el-text-color-secondary);
}

.m-tab.active .m-tab-label {
  color: var(--el-color-primary);
  font-weight: 600;
}

.m-tab-icon {
  font-size: 20px;
  line-height: 1;
}

.m-tab-label {
  font-size: 11px;
}
</style>
