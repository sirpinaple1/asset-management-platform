<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useBasedataStore } from '@/stores/basedata'
import { basedataApi } from '@/api/modules/basedata'
import type { Manufacturer } from '@/api/interface/basedata'
import ManufacturerModal from './components/ManufacturerModal.vue'

const store = useBasedataStore()
const manufacturers = computed(() => store.manufacturers)
const loading = computed(() => store.loading.manufacturers)

const modalVisible = ref(false)
const currentRecord = ref<Manufacturer>()

const loadData = () => store.fetchManufacturers()

const handleAdd = () => {
  currentRecord.value = undefined
  modalVisible.value = true
}

const handleEdit = (record: Manufacturer) => {
  currentRecord.value = record
  modalVisible.value = true
}

const handleDelete = async (id: number) => {
  try {
    await basedataApi.deleteManufacturer(id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // 错误已由拦截器统一提示
  }
}

/** 空值统一显示占位符 */
const formatText = (_row: Manufacturer, _column: unknown, cellValue: unknown) =>
  cellValue === undefined || cellValue === null || cellValue === '' ? '—' : cellValue

onMounted(loadData)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h1 class="page-title">厂商管理</h1>
      <el-button type="primary" @click="handleAdd">新增厂商</el-button>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="manufacturers" row-key="id">
        <el-table-column prop="name" label="厂商名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" min-width="100" :formatter="formatText" />
        <el-table-column prop="phone" label="联系电话" min-width="130" :formatter="formatText" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该厂商吗？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <ManufacturerModal v-model:visible="modalVisible" :data="currentRecord" @success="loadData" />
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
