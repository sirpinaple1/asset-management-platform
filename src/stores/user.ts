import { defineStore } from 'pinia'
import { fetchMe } from '@/api/modules/user'
import type { MeInfo } from '@/api/interface'

/** 用户登录态：token + /api/v1/me 信息（M-FE01 鉴权全链路验证） */
export const useUserStore = defineStore('user', {
  state: () => ({
    me: null as MeInfo | null,
    /** me 拉取中（避免路由守卫与页面重复请求） */
    loading: false
  }),

  getters: {
    displayName(state): string {
      return state.me?.name || state.me?.username || ''
    },
    avatarChar(state): string {
      return state.me?.name?.charAt(0) || state.me?.username?.charAt(0) || '用'
    }
  },

  actions: {
    /** 拉取当前用户信息；401/403 由 axios 拦截器统一处理 */
    async loadMe(force = false): Promise<MeInfo | null> {
      if (this.me && !force) return this.me
      if (this.loading) return null
      this.loading = true
      try {
        this.me = await fetchMe()
        return this.me
      } finally {
        this.loading = false
      }
    },
    reset(): void {
      this.me = null
    }
  }
})
