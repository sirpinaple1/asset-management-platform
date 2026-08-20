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
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}

.coming-soon {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
