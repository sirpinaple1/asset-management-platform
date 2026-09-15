<script setup lang="ts">
/** 变更单图标（文档 + 铅笔）：currentColor 跟随文字色 */
withDefaults(defineProps<{ size?: number }>(), { size: 20 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <path d="M4 3.5H11.5L15.5 7.5V10.5" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
    <path d="M4 3.5V16.5H8.5" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
    <path d="M11.5 3.5V7.5H15.5" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
    <path d="M10.5 15.6L16.4 9.7L17.9 11.2L12 17.1L10 17.6L10.5 15.6Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
  </svg>
</template>
