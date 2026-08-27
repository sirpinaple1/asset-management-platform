<script setup lang="ts">
/**
 * 通用选人器：基于 B1 GET /v1/users 远程搜索（keyword 模糊工号/姓名/部门）。
 * 用法：
 *   <UserSelector v-model="userId" v-model:user-name="userName" placeholder="选择指定处理人" />
 *   - v-model       : number | undefined   （用户ID）
 *   - v-model:user-name : string            （姓名快照，发起申请时需一并提交）
 */
import { computed, ref, watch } from 'vue'
import { userApi } from '@/api/modules/user'
import type { UserSearchOption } from '@/api/interface/user'

const props = defineProps<{
  modelValue: number | undefined
  userName?: string
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: number | undefined): void
  (e: 'update:userName', v: string): void
}>()

const searchLoading = ref(false)
const options = ref<UserSearchOption[]>([])
const keyword = ref('')

/** 当前选中的完整对象（用于 option label 回显） */
const selectedOption = ref<UserSearchOption | null>(null)

/** 父组件直接传入的 modelValue 若变更，尝试在 options 里匹配，匹配不到就单独构造回显 */
watch(
  () => props.modelValue,
  (id) => {
    if (id === undefined || id === null) {
      selectedOption.value = null
      return
    }
    const found = options.value.find((o) => o.id === id)
    if (found) {
      selectedOption.value = found
      emit('update:userName', found.name)
    } else if (!selectedOption.value || selectedOption.value.id !== id) {
      // 本地不在 options 时：构造一个最小化 option 保持展示正确
      selectedOption.value = { id, name: props.userName || String(id) }
    }
  },
  { immediate: true },
)

watch(
  () => props.userName,
  (name) => {
    if (selectedOption.value && name && selectedOption.value.name !== name) {
      selectedOption.value = { ...selectedOption.value, name }
    }
  },
)

/** 首次聚焦且无 keyword 时，给一个空列表即可（避免一次性拉太多人；用户输入关键字再查） */
const handleRemoteQuery = async (query: string) => {
  keyword.value = query
  if (!query.trim()) {
    options.value = selectedOption.value ? [selectedOption.value] : []
    return
  }
  searchLoading.value = true
  try {
    const resp = await userApi.searchUsers(query, 1, 50)
    options.value = resp.records || []
    // 如果当前已选不在搜索结果里，补入第一项以便保持选中态不消失
    if (selectedOption.value && !options.value.some((o) => o.id === selectedOption.value!.id)) {
      options.value = [selectedOption.value, ...options.value]
    }
  } catch {
    options.value = selectedOption.value ? [selectedOption.value] : []
  } finally {
    searchLoading.value = false
  }
}

/** 选中某个人 */
const handleChange = (val: number | undefined) => {
  if (val === undefined || val === null) {
    selectedOption.value = null
    emit('update:modelValue', undefined)
    emit('update:userName', '')
    return
  }
  const found = options.value.find((o) => o.id === val)
  selectedOption.value = found || { id: val, name: String(val) }
  emit('update:modelValue', val)
  emit('update:userName', found?.name || String(val))
}

/** el-select 选项 label（工号 - 姓名 - 部门） */
const renderLabel = (o: UserSearchOption | undefined) => {
  if (!o) return ''
  const parts: string[] = []
  if (o.username) parts.push(o.username)
  parts.push(o.name)
  if (o.dept) parts.push(o.dept)
  return parts.join(' · ')
}

const displayValue = computed(() => props.modelValue)
</script>

<template>
  <el-select
    :model-value="displayValue"
    :placeholder="placeholder || '搜索并选择用户'"
    :loading="searchLoading"
    filterable
    remote
    reserve-keyword
    :clearable="clearable !== false"
    :disabled="disabled"
    remote-show-suffix
    class="user-selector full-width"
    @update:model-value="handleChange"
    @visible-change="(v: boolean) => { if (v && !keyword) handleRemoteQuery('') }"
    @remote-method="handleRemoteQuery"
  >
    <el-option
      v-for="opt in options"
      :key="opt.id"
      :value="opt.id"
      :label="renderLabel(opt)"
    >
      <div class="user-opt">
        <span class="user-opt-name">{{ opt.name }}</span>
        <span v-if="opt.username" class="user-opt-uname">{{ opt.username }}</span>
        <span v-if="opt.dept" class="user-opt-dept">{{ opt.dept }}</span>
      </div>
    </el-option>
    <template #empty>
      <span class="empty-tip">{{ searchLoading ? '搜索中...' : keyword ? '无匹配用户' : '输入关键字搜索' }}</span>
    </template>
  </el-select>
</template>

<style scoped>
.user-selector {
  width: 100%;
}
.user-opt {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.user-opt-name {
  font-weight: 600;
  color: #1d2129;
}
.user-opt-uname {
  color: #4e5969;
  font-size: 12px;
  background: #f2f3f5;
  border-radius: 4px;
  padding: 1px 6px;
}
.user-opt-dept {
  color: #86909c;
  font-size: 12px;
  margin-left: auto;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.empty-tip {
  color: #86909c;
  font-size: 12px;
  padding: 0 8px;
}
.full-width {
  width: 100%;
}
</style>
