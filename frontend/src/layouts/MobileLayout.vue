<template>
  <div class="m-layout">
    <header class="m-header">
      <span class="m-header-title">资产管理系统</span>
      <span v-if="userStore.displayName" class="m-header-user">{{ userStore.displayName }}</span>
    </header>

    <main class="m-body">
      <router-view v-slot="{ Component }">
        <transition name="m-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <nav class="m-tabbar">
      <router-link
        to="/m/approvals"
        class="m-tab"
        :class="{ active: route.path === '/m/approvals' }"
      >
        <span class="m-tab-icon">
          <span v-if="approvalStore.todoCount > 0" class="m-tab-dot">{{
            approvalStore.todoCount > 99 ? '99+' : approvalStore.todoCount
          }}</span>
          <!-- 审批：PC 侧边栏同款清单图标 -->
          <svg width="22" height="22" viewBox="0 0 20 20" fill="none">
            <path d="M3 5L6 8L9 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
            <line x1="11" y1="6.5" x2="17" y2="6.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            <path d="M3 10L6 13L9 10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
            <line x1="11" y1="11.5" x2="17" y2="11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            <circle cx="5" cy="16" r="1.2" stroke="currentColor" stroke-width="1.5" />
            <line x1="11" y1="16" x2="17" y2="16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </span>
        <span class="m-tab-label">审批</span>
      </router-link>

      <router-link
        to="/m/assets"
        class="m-tab"
        :class="{ active: route.path === '/m/assets' }"
      >
        <span class="m-tab-icon">
          <!-- 资产：PC 侧边栏同款档案盒图标 -->
          <svg width="22" height="22" viewBox="0 0 20 20" fill="none">
            <rect x="3" y="6" width="14" height="11" rx="1.5" stroke="currentColor" stroke-width="1.5" />
            <path d="M3 9.5L10 6L17 9.5" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
            <line x1="10" y1="6" x2="10" y2="17" stroke="currentColor" stroke-width="1.5" />
            <rect x="7" y="3" width="6" height="3" rx="0.5" fill="currentColor" />
          </svg>
        </span>
        <span class="m-tab-label">我的资产</span>
      </router-link>

      <!-- 中央 FAB：发起申请 -->
      <div class="m-fab-slot">
        <button class="m-fab" type="button" aria-label="发起申请" @click="goApply">
          <svg width="22" height="22" viewBox="0 0 20 20" fill="none">
            <line x1="10" y1="4" x2="10" y2="16" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            <line x1="4" y1="10" x2="16" y2="10" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
        <span class="m-tab-label m-fab-label">发起</span>
      </div>

      <router-link to="/m/scan" class="m-tab" :class="{ active: route.path === '/m/scan' }">
        <span class="m-tab-icon">
          <!-- 扫码：取景框 + 扫描线 -->
          <svg width="22" height="22" viewBox="0 0 20 20" fill="none">
            <path d="M3 7V5C3 3.89543 3.89543 3 5 3H7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            <path d="M13 3H15C16.1046 3 17 3.89543 17 5V7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            <path d="M17 13V15C17 16.1046 16.1046 17 15 17H13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            <path d="M7 17H5C3.89543 17 3 16.1046 3 15V13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            <line x1="5.5" y1="10" x2="14.5" y2="10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </span>
        <span class="m-tab-label">扫码</span>
      </router-link>

      <router-link to="/m/profile" class="m-tab" :class="{ active: route.path === '/m/profile' }">
        <span class="m-tab-icon">
          <!-- 我的：用户轮廓 -->
          <svg width="22" height="22" viewBox="0 0 20 20" fill="none">
            <circle cx="10" cy="6.5" r="3" stroke="currentColor" stroke-width="1.5" />
            <path d="M4 17C4.5 14 7 12.5 10 12.5C13 12.5 15.5 14 16 17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </span>
        <span class="m-tab-label">我的</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useApprovalStore } from '@/stores/approval'
import '@/styles/mobile.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const approvalStore = useApprovalStore()

const goApply = () => {
  if (route.path !== '/m/apply') router.push('/m/apply')
}

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
  background: var(--color-bg-1);
}

.m-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 46px;
  background: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border-light);
}

.m-header-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-1);
  letter-spacing: 0.2px;
}

.m-header-user {
  font-size: var(--text-xs);
  color: var(--color-text-3);
  max-width: 40vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-body {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  /* tabbar 改为 fixed 后预留遮挡高度：48px 栏高 + 24px FAB 上凸 + 安全区 */
  padding-bottom: calc(72px + env(safe-area-inset-bottom));
}

/* 页面切换过渡 */
.m-fade-enter-active,
.m-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.m-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.m-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ---------- 底部导航（fixed 物理钉底，不随内容/整页弹性滚动移动） ---------- */
.m-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 90; /* 低于全屏弹层 m-sheet(100)，弹层打开时被完整覆盖 */
  display: flex;
  align-items: stretch;
  background: var(--color-bg-2);
  border-top: 1px solid var(--color-border-light);
  padding-bottom: env(safe-area-inset-bottom);
}

.m-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 7px 0 5px;
  text-decoration: none;
  color: var(--color-text-3);
  transition: color 0.2s;
}

.m-tab.active {
  color: var(--color-primary);
}

.m-tab-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 22px;
}

.m-tab-label {
  font-size: 11px;
  line-height: 1;
}

.m-tab-dot {
  position: absolute;
  top: -5px;
  right: -14px;
  min-width: 16px;
  padding: 0 4px;
  border-radius: var(--radius-round);
  background: var(--color-error);
  color: var(--color-text-inverse);
  font-size: 10px;
  line-height: 15px;
  font-weight: 500;
  text-align: center;
  box-shadow: 0 0 0 2px var(--color-bg-2);
}

/* ---------- 中央 FAB ---------- */
.m-fab-slot {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding-top: 7px;
}

.m-fab {
  width: 46px;
  height: 46px;
  margin-top: -24px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-primary-hover), var(--color-primary-active));
  color: var(--color-text-inverse);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(22, 93, 255, 0.35);
  transition: transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.18s ease;
}

.m-fab:active {
  transform: scale(0.9);
  box-shadow: 0 3px 8px rgba(22, 93, 255, 0.3);
}

.m-fab-label {
  font-size: 11px;
  color: var(--color-text-3);
}

@media (prefers-reduced-motion: reduce) {
  .m-fade-enter-active,
  .m-fade-leave-active {
    transition: none;
  }
  .m-fab {
    transition: none;
  }
}
</style>
