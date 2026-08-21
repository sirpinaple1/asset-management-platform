<script setup lang="ts">
import ReceiptList from './ReceiptList.vue'

/** 领用&退库（与路由 name 一致：多页签 keep-alive 缓存键） */
defineOptions({ name: 'receipts-receive' })
</script>

<template>
  <ReceiptList type="RECEIVE" />
</template>
