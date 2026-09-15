<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/** 全局异常兜底页（P0）：403 无权限 / 500 服务异常 / network 网络异常，可由路由参数或 prop 指定 */
defineOptions({ name: 'exception-page' })

const props = defineProps<{ type?: '403' | '500' | 'network' }>()

const route = useRoute()
const router = useRouter()

const EXCEPTION_META: Record<string, { icon: 'warning' | 'error'; title: string; desc: string }> = {
  '403': { icon: 'warning', title: '403', desc: '没有权限访问该页面' },
  '500': { icon: 'error', title: '500', desc: '服务异常，请稍后重试' },
  network: { icon: 'warning', title: '网络异常', desc: '网络连接失败，请检查网络后重试' },
}

const current = computed(() => {
  const t = props.type || String(route.params.type || '')
  return EXCEPTION_META[t] ? t : '500'
})
const meta = computed(() => EXCEPTION_META[current.value])

const goHome = () => router.push('/dashboard')
const reload = () => window.location.reload()
const goBack = () => router.back()
</script>

<template>
  <div class="exception-page">
    <el-result :icon="meta.icon" :title="meta.title" :sub-title="meta.desc">
      <template #extra>
        <el-button type="primary" @click="goHome">返回工作台</el-button>
        <el-button v-if="current !== '403'" @click="reload">刷新重试</el-button>
        <el-button text @click="goBack">返回上一页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.exception-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
