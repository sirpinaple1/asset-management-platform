<script setup lang="ts">
/** 调拨单图标（两位置块 + 传输箭头）：currentColor 跟随文字色 */
withDefaults(defineProps<{ size?: number }>(), { size: 20 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect x="2.5" y="8" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.5" />
    <rect x="11.5" y="8" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.5" />
    <path d="M8.5 11H10.8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
    <path d="M9.8 9.8L11.2 11L9.8 12.2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>
