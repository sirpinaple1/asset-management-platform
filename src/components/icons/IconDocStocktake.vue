<script setup lang="ts">
/** 盘点单图标（清单 + 对勾）：currentColor 跟随文字色 */
withDefaults(defineProps<{ size?: number }>(), { size: 20 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect x="3" y="2.5" width="14" height="15" rx="1.5" stroke="currentColor" stroke-width="1.5" />
    <path d="M6 6.5H14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
    <path d="M6 10H9.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
    <path d="M6.3 15.2L8.6 17.5L13.8 12.3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>
