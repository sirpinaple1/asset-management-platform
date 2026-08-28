<script setup lang="ts">
/**
 * 审批链配置管理（组织架构管理，仅 systemAdmin）：
 * 两级审批人路由表——部门主管（键=部门路径）/ 领料仓管理员（键=领用区域位置）。
 * 领用/借用单提交时按此表解析审批人快照；配置缺失时后端 400 阻止提交并告警超管。
 * 契约：ApprovalConfigController（GET/POST/PUT/DELETE /v1/approval-configs，后端 403 门禁）。
 */
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { basedataApi } from '@/api/modules/basedata'
import type { ApprovalConfig, ApprovalConfigType } from '@/api/interface/basedata'
import { useBasedataStore } from '@/stores/basedata'
import { useUserStore } from '@/stores/user'
import ApprovalConfigModal from './components/ApprovalConfigModal.vue'

const basedataStore = useBasedataStore()
const userStore = useUserStore()

/** 超管门禁（菜单已隐藏，此为第二道防线；me 未加载时不渲染管理功能） */
const isSuperAdmin = computed(() => userStore.me?.roles?.includes('systemAdmin') ?? false)

/* ---------------- 类型 tabs + 列表 ---------------- */

const TYPE_TABS: { key: ApprovalConfigType | ''; label: string; hint: string }[] = [
  { key: 'DEPT_SUPERVISOR', label: '部门主管', hint: '一级审批：按发起人部门路径解析（精确优先，逐级向上回退）' },
  { key: 'WAREHOUSE_KEEPER', label: '领料仓管理员', hint: '二级审批：按领用区域（位置）解析' },
]

const activeType = ref<ApprovalConfigType | ''>('')
const keyword = ref('')
const loading = ref(false)
const tableRef = ref<TableInstance>()
const rows = ref<ApprovalConfig[]>([])
const total = ref(0)
const page = reactive({ current: 1, size: 20 })

const typeHint = computed(() => {
  if (!activeType.value) return '两类配置共用一张路由表：提交领用/借用单时，先按发起人部门找部门主管（一级），再按领用区域找仓管（二级）'
  return TYPE_TABS.find((t) => t.key === activeType.value)?.hint ?? ''
})

const fetchList = async () => {
  loading.value = true
  try {
    const resp = await basedataApi.getApprovalConfigs({
      page: page.current,
      size: page.size,
      type: activeType.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    rows.value = resp.records ?? []
    total.value = Number(resp.total ?? 0)
  } catch {
    /* 拦截器已提示（含非超管 403） */
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.current = 1
  void fetchList()
}

const switchType = (key: ApprovalConfigType | '') => {
  activeType.value = key
  page.current = 1
  void fetchList()
}

/* ---------------- 新增 / 编辑 / 删除 ---------------- */

const modalVisible = ref(false)
const editing = ref<ApprovalConfig | null>(null)

const openCreate = () => {
  editing.value = null
  modalVisible.value = true
}

const openEdit = (row: ApprovalConfig) => {
  editing.value = row
  modalVisible.value = true
}

const handleDelete = async (row: ApprovalConfig) => {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.configKeyLabel || row.configKey}」的审批人配置（${row.approverUserName || row.approverUserId}）？`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await basedataApi.deleteApprovalConfig(row.id)
    ElMessage.success('删除成功')
    /* 末页删空回退一页 */
    if (rows.value.length === 1 && page.current > 1) page.current--
    void fetchList()
  } catch {
    /* 拦截器已提示（配置被在途单据引用等 400 原因） */
  }
}

/* ---------------- 生命周期：keep-alive 切回刷新 ---------------- */

onMounted(() => {
  void basedataStore.fetchLocations()
  void fetchList()
})

let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  void fetchList()
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-radio-group :model-value="activeType" @update:model-value="switchType as any">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="t in TYPE_TABS" :key="t.key" :value="t.key">{{ t.label }}</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        placeholder="搜索配置键 / 备注"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button @click="handleSearch">搜索</el-button>
      <div class="spacer" />
      <el-button v-if="isSuperAdmin" type="primary" @click="openCreate">新增配置</el-button>
    </div>

    <el-alert :title="typeHint" type="info" :closable="false" show-icon class="type-hint" />

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="rows"
      row-key="id"
      stripe
      border
      height="100%"
      class="table"
      @row-dblclick="(row: ApprovalConfig) => isSuperAdmin && openEdit(row)"
    >
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="configTypeLabel" label="配置类型" width="130">
        <template #default="{ row }">
          <el-tag :type="row.configType === 'DEPT_SUPERVISOR' ? 'primary' : 'success'" effect="light">
            {{ row.configTypeLabel || row.configType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="配置键（部门路径 / 领用区域）" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.configKeyLabel || row.configKey }}
        </template>
      </el-table-column>
      <el-table-column label="审批人" width="140">
        <template #default="{ row }">
          {{ row.approverUserName || `#${row.approverUserId}` }}
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.remark || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170">
        <template #default="{ row }">
          {{ row.createdAt || '—' }}
        </template>
      </el-table-column>
      <el-table-column v-if="isSuperAdmin" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="handleSearch"
      />
    </div>

    <ApprovalConfigModal
      v-model:visible="modalVisible"
      :data="editing"
      @success="fetchList"
    />
  </div>
</template>

<style scoped>
.type-hint {
  margin-bottom: 12px;
}

.search-input {
  width: 240px;
}
</style>
