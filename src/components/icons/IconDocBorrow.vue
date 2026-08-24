<script setup lang="ts">
/** 借用单图标（盒 + 双向箭头：借出/归还）：currentColor 跟随文字色 */
withDefaults(defineProps<{ size?: number }>(), { size: 20 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <rect x="3" y="10.5" width="14" height="6.5" rx="1.5" stroke="currentColor" stroke-width="1.5" />
    <path d="M8.5 4.5L6 7L8.5 9.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
    <path d="M11.5 4.5L14 7L11.5 9.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
    <path d="M6 7H14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
  </svg>
</template>
