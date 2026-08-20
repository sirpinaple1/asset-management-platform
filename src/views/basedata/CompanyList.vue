<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useBasedataStore } from '@/stores/basedata'
import type { Company } from '@/api/interface/basedata'

const store = useBasedataStore()
const companies = computed(() => store.companies)
const loading = computed(() => store.loading.companies)

/** 空值统一显示占位符 */
const formatText = (_row: Company, _column: unknown, cellValue: unknown) =>
  cellValue === undefined || cellValue === null || cellValue === '' ? '—' : cellValue

/** 创建时间只展示日期 */
const formatDate = (_row: Company, _column: unknown, cellValue: unknown) =>
  typeof cellValue === 'string' && cellValue ? cellValue.slice(0, 10) : '—'

onMounted(() => store.fetchCompanies())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">公司主体</h1>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="companies" row-key="id">
        <el-table-column prop="code" label="公司编码" width="120" />
        <el-table-column prop="name" label="公司名称" min-width="240" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="160" :formatter="formatText" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="120" :formatter="formatDate" />
      </el-table>
    </div>
  </div>
</template>
