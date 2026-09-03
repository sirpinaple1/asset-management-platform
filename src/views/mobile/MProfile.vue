<template>
  <div class="m-page">
    <!-- 账户卡片 -->
    <div class="profile-card">
      <div class="profile-avatar">{{ userStore.avatarChar }}</div>
      <div class="profile-info">
        <div class="profile-name">{{ userStore.me?.name || '-' }}</div>
        <div class="profile-meta">{{ userStore.me?.username || '-' }}</div>
      </div>
    </div>

    <div class="m-row"><span>部门</span><b>{{ userStore.me?.dept || '-' }}</b></div>
    <div class="m-row"><span>岗位</span><b>{{ userStore.me?.job || '-' }}</b></div>
    <div class="m-row roles-row">
      <span>角色</span>
      <b>
        <span v-if="!userStore.me?.roles?.length" class="no-role">未分配</span>
        <span v-for="r in userStore.me?.roles" :key="r" class="role-chip">{{ r }}</span>
      </b>
    </div>

    <!-- 版本信息 -->
    <div class="section-title">版本信息</div>
    <div class="m-row"><span>前端版本</span><b>v{{ FRONTEND_VERSION }}</b></div>
    <div class="m-row"><span>发布日期</span><b>{{ RELEASE_DATE }}</b></div>

    <div class="credit">Copyright © 2026 潘雨松. All rights reserved.</div>

    <!-- 退出 -->
    <button class="m-btn danger-ghost logout-btn" type="button" @click="onLogout">
      {{ isInDingTalk() ? '重置登录态并重新免登' : '退出登录' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { clearToken, redirectToAuthCenter, clearAuthCenterLocalSession } from '@/utils/token'
import { isInDingTalk } from '@/utils/dingtalk'
import { FRONTEND_VERSION, RELEASE_DATE } from '@/version'

const userStore = useUserStore()

/** 与 PC 端 DefaultLayout 退出逻辑一致：钉钉内清态刷新重免登；浏览器跳登录中心 */
const onLogout = () => {
  import('element-plus').then(({ ElMessageBox }) => {
    const inDingTalk = isInDingTalk()
    ElMessageBox.confirm(
      inDingTalk ? '确定要重置当前登录态并重新免登吗？' : '确定要退出当前账号吗？',
      inDingTalk ? '重置登录' : '退出登录',
      {
        confirmButtonText: inDingTalk ? '重置并重登' : '退出',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
      .then(() => {
        clearToken()
        userStore.reset()
        if (inDingTalk) {
          window.location.reload()
          return
        }
        clearAuthCenterLocalSession()
        redirectToAuthCenter()
      })
      .catch(() => {
        /* 取消 */
      })
  })
}

onMounted(() => {
  void userStore.loadMe()
})
</script>

<style scoped>
.profile-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--color-bg-2);
  border-radius: var(--radius-xl);
  padding: 18px 16px;
  margin-bottom: 12px;
  box-shadow: var(--shadow-sm);
  animation: m-enter 0.3s cubic-bezier(0.3, 0.7, 0.4, 1) both;
}

.profile-avatar {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-primary-hover), var(--color-primary-active));
  color: var(--color-text-inverse);
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-1);
}

.profile-meta {
  margin-top: 4px;
  font-size: var(--text-sm);
  color: var(--color-text-3);
}

.roles-row b {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.role-chip {
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: var(--text-xs);
}

.no-role {
  color: var(--color-text-4);
}

.section-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--color-text-1);
  margin: 18px 2px 10px;
}

.credit {
  text-align: center;
  font-size: var(--text-xs);
  color: var(--color-text-4);
  margin: 20px 0 24px;
}

.logout-btn {
  flex: none;
  width: 100%;
}
</style>
