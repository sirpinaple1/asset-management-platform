<script setup lang="ts">
import ReceiptList from './ReceiptList.vue'

/** 借用&归还（与路由 name 一致：多页签 keep-alive 缓存键） */
defineOptions({ name: 'receipts-borrow' })
</script>

<template>
  <ReceiptList type="BORROW" />
</template>
