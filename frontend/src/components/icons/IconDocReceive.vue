<script setup lang="ts">
/** 领用单图标（盒 + 向上取出箭头）：currentColor 跟随文字色 */
withDefaults(defineProps<{ size?: number }>(), { size: 20 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect x="3" y="9.5" width="9" height="7.5" rx="1.5" stroke="currentColor" stroke-width="1.5" />
    <path d="M15.5 13V4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
    <path d="M12.5 7.5L15.5 4.5L18.5 7.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>
