<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

/** 骨架期仅工作台一个路由；其余导航为 M02+ 模块占位 */
const activeNav = computed(() => route.name)

const user = computed(() => userStore.me)
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="topbar-logo">
        <svg width="30" height="30" viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg">
          <rect width="32" height="32" rx="7" fill="#165DFF" />
          <path d="M16 7.5 L25 12 L16 16.5 L7 12 Z" fill="#FFFFFF" />
          <path
            d="M9.5 15.5 L16 18.75 L22.5 15.5 L25 16.75 L16 21.25 L7 16.75 Z"
            fill="#FFFFFF"
            opacity="0.85"
          />
          <rect x="7" y="22" width="18" height="2.2" rx="1.1" fill="#FFFFFF" opacity="0.7" />
        </svg>
        <span class="logo-text">森科资产管理</span>
      </div>

      <div class="user-area">
        <el-dropdown trigger="click">
          <div class="user-trigger">
            <span class="user-name">{{ userStore.displayName || '未登录' }}</span>
            <div class="user-avatar">{{ userStore.avatarChar }}</div>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <div class="user-panel">
                <div class="user-panel-name">{{ user?.name || '-' }}</div>
                <div class="user-panel-row"><span>用户名</span><span>{{ user?.username || '-' }}</span></div>
                <div class="user-panel-row"><span>部门</span><span>{{ user?.dept || '-' }}</span></div>
                <div class="user-panel-row"><span>岗位</span><span>{{ user?.job || '-' }}</span></div>
                <div class="user-panel-row">
                  <span>角色</span>
                  <span>{{ user?.roles?.length ? user.roles.join('、') : '未分配' }}</span>
                </div>
              </div>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="main">
      <aside class="sidebar">
        <router-link to="/dashboard" class="nav-item" :class="{ active: activeNav === 'dashboard' }" title="工作台">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="2" width="7" height="7" rx="1.5" :fill="activeNav === 'dashboard' ? 'white' : '#86909C'" />
            <rect x="11" y="2" width="7" height="7" rx="1.5" :fill="activeNav === 'dashboard' ? 'white' : '#86909C'" />
            <rect x="2" y="11" width="7" height="7" rx="1.5" :fill="activeNav === 'dashboard' ? 'white' : '#86909C'" />
            <rect x="11" y="11" width="7" height="7" rx="1.5" :fill="activeNav === 'dashboard' ? 'white' : '#86909C'" />
          </svg>
        </router-link>
        <div class="nav-item" title="消息（待接入）">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path
              d="M3 6C3 4.34315 4.34315 3 6 3H14C15.6569 3 17 4.34315 17 6V12C17 13.6569 15.6569 15 14 15H9L6 17.5V15H6C4.34315 15 3 13.6569 3 12V6Z"
              stroke="#86909C" stroke-width="1.5"
            />
          </svg>
        </div>
        <div class="nav-item" title="用户（待接入）">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="10" cy="6" r="3" stroke="#86909C" stroke-width="1.5" />
            <path d="M4 16C4 13.7909 5.79086 12 8 12H12C14.2091 12 16 13.7909 16 16" stroke="#86909C" stroke-width="1.5" />
          </svg>
        </div>
        <div class="nav-item" title="应用（待接入）">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="2" width="7" height="7" rx="1.5" fill="#86909C" />
            <rect x="11" y="2" width="7" height="7" rx="1.5" fill="#86909C" />
            <rect x="2" y="11" width="7" height="7" rx="1.5" fill="#86909C" />
            <rect x="11" y="11" width="7" height="7" rx="1.5" fill="#86909C" />
          </svg>
        </div>
        <div class="nav-item" title="资产（待接入）">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="0" y="0" width="20" height="20" rx="4" stroke="#86909C" stroke-width="1.5" />
            <rect x="0" y="6" width="20" height="1.5" fill="#86909C" />
            <rect x="6" y="6" width="1.5" height="14" fill="#86909C" />
          </svg>
        </div>
        <div class="nav-item" title="库存（待接入）">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="4" width="16" height="12" rx="2" stroke="#86909C" stroke-width="1.5" />
            <rect x="2" y="8" width="16" height="1.5" fill="#86909C" />
            <circle cx="7" cy="12" r="1.5" fill="#86909C" />
            <circle cx="13" cy="12" r="1.5" fill="#86909C" />
          </svg>
        </div>

        <div class="sidebar-spacer"></div>

        <div class="bottom-nav">
          <div class="bottom-nav-item" title="通知">
            <span class="red-dot"></span>
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M10 17C11.1046 17 12 16.1046 12 15H8C8 16.1046 8.89543 17 10 17Z" fill="#86909C" />
              <path
                d="M15 11V7C15 4.79086 13.2091 3 11 3H9C6.79086 3 5 4.79086 5 7V11L3 13H17L15 11Z"
                stroke="#86909C" stroke-width="1.5"
              />
            </svg>
          </div>
          <div class="bottom-nav-item" title="帮助">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="10" cy="10" r="8" stroke="#86909C" stroke-width="1.5" />
              <path
                d="M10 14V13.5C10 12.5 10.5 12 11.5 11.5C12.5 11 13 10.2 13 9C13 7.5 11.5 6.5 10 6.5C8.5 6.5 7 7.5 7 9"
                stroke="#86909C" stroke-width="1.5" stroke-linecap="round"
              />
              <circle cx="10" cy="15.5" r="0.8" fill="#86909C" />
            </svg>
          </div>
        </div>
      </aside>

      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<style scoped>
.app {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f6fa;
  min-width: 0;
}

.topbar {
  height: 56px;
  background: #ffffff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px 0 16px;
  box-shadow: 0 1px 4px rgba(217, 222, 232, 0.2);
  flex-shrink: 0;
}

.topbar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-text {
  font-size: 17px;
  font-weight: 600;
  color: #1d2129;
  letter-spacing: 1px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  outline: none;
}

.user-name {
  font-size: 14px;
  font-weight: 400;
  color: #1d2129;
  line-height: 22px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
}

.user-panel {
  padding: 8px 16px;
  min-width: 240px;
}

.user-panel-name {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f2f5;
  margin-bottom: 8px;
}

.user-panel-row {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  font-size: 13px;
  line-height: 24px;
  color: #4e5969;
}

.user-panel-row span:first-child {
  color: #86909c;
}

.main {
  flex: 1;
  display: flex;
  min-height: 0;
}

.sidebar {
  width: 64px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 16px;
  gap: 8px;
  flex-shrink: 0;
}

.nav-item {
  width: 64px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  text-decoration: none;
}

.nav-item.active {
  background: #165dff;
}

.sidebar-spacer {
  flex: 1;
  width: 100%;
  min-height: 0;
}

.bottom-nav {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-bottom: 16px;
}

.bottom-nav-item {
  width: 64px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
}

.red-dot {
  position: absolute;
  top: 8px;
  right: 18px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f53f3f;
}

.content {
  flex: 1;
  background: #f5f6fa;
  padding: 24px;
  overflow: auto;
  min-width: 0;
  min-height: 0;
}
</style>
