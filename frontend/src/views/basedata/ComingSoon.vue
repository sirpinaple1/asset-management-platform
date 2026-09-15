<script setup lang="ts">
import { useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()
const title = computed(() => String(route.meta.title || '页面'))
</script>

<template>
  <div class="page">
    <div class="page-container">
      <div class="page-header">
        <h2 class="page-title">{{ title }}</h2>
      </div>
      <div class="coming-soon">
        <el-empty :description="`${title}功能建设中，敬请期待`" />
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
}

.page-title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-1);
  margin: 0;
}

.coming-soon {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
