# M02 基础数据 CRUD 页面实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现资产管理系统 M02 基础数据模块的前端 CRUD 页面，包括公司、分类、位置、厂商、供应商、型号六张表的查询、新增、编辑、删除功能。

**Architecture:** 
- 基于 Vue 3 + TypeScript + Arco Design 构建
- 采用设计规范文档定义的视觉风格和交互能力
- API 层使用 axios 封装，状态管理使用 Pinia
- 位置和分类支持树形结构展示

**Tech Stack:**
- Vue 3.4 + TypeScript 5.3
- Arco Design Vue 2.56
- Vue Router 4.2
- Pinia 2.1
- Axios 1.6
- Vite 5.0

**Spec:** `docs/superpowers/specs/2026-08-20-frontend-design-system.md`

## Global Constraints

- Node.js ≥ 18.0
- Vue 3.4+ (Composition API)
- TypeScript strict mode
- Arco Design 按需引入
- 所有图标使用描边风格（stroke-width: 1.5 ~ 2.6）
- 间距遵循 8px 网格系统
- 色彩值严格遵循设计规范（品牌蓝 #165DFF，中性色 #1D2129/#4E5969/#86909C）
- 表单校验使用 Arco Design 内置规则
- 所有 API 请求需携带 Authorization 头
- 提交消息格式：`feat: 简短描述`（一行）

---

### Task 1: 项目结构初始化与 CSS 变量

**Files:**
- Create: `src/assets/styles/variables.css`
- Create: `src/assets/styles/reset.css`
- Create: `src/assets/styles/common.css`
- Modify: `src/main.ts`（引入全局样式）

**Interfaces:**
- Produces: CSS 变量系统（`--color-primary`, `--spacing-md` 等）供所有组件使用

- [ ] **Step 1: 创建 CSS 变量文件**

```css
/* src/assets/styles/variables.css */
:root {
  /* 品牌色 */
  --color-primary: #165DFF;
  --color-primary-hover: #4080FF;
  --color-secondary: #14C9C9;
  
  /* 中性色 */
  --color-text-1: #1D2129;
  --color-text-2: #4E5969;
  --color-text-3: #86909C;
  --color-border: #E5E6EB;
  --color-bg-1: #F7F8FA;
  --color-bg-2: #FFFFFF;
  --color-nav-bg: #1D2129;
  --color-nav-active: #272E3B;
  
  /* 功能色 */
  --color-success: #00B42A;
  --color-warning: #FF7D00;
  --color-error: #F53F3F;
  --color-info: #165DFF;
  
  /* 间距 */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;
  
  /* 布局尺寸 */
  --nav-width: 64px;
  --header-height: 60px;
}
```

- [ ] **Step 2: 创建 CSS 重置文件**

```css
/* src/assets/styles/reset.css */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 
               'Helvetica Neue', Arial, sans-serif;
  font-size: 14px;
  line-height: 22px;
  color: var(--color-text-1);
  background: var(--color-bg-1);
}

a {
  text-decoration: none;
  color: inherit;
}

ul, ol {
  list-style: none;
}

button {
  border: none;
  background: none;
  font: inherit;
  cursor: pointer;
}
```

- [ ] **Step 3: 创建通用样式文件**

```css
/* src/assets/styles/common.css */
.page-container {
  padding: var(--spacing-lg);
}

.page-title {
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
  color: var(--color-text-1);
  margin-bottom: var(--spacing-lg);
}

.card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.btn-group {
  display: flex;
  gap: var(--spacing-sm);
}
```

- [ ] **Step 4: 在 main.ts 引入全局样式**

```typescript
// src/main.ts（在现有代码基础上添加）
import './assets/styles/variables.css'
import './assets/styles/reset.css'
import './assets/styles/common.css'
```

- [ ] **Step 5: 验证样式生效**

启动开发服务器：`npm run dev`

在浏览器控制台输入：
```javascript
getComputedStyle(document.documentElement).getPropertyValue('--color-primary')
```
预期输出：`#165DFF`

- [ ] **Step 6: 提交**

```bash
git add src/assets/styles/*.css src/main.ts
git commit -m "feat: CSS变量系统"
```

---

### Task 2: API 层封装与类型定义

**Files:**
- Create: `src/api/request.ts`（axios 实例）
- Create: `src/api/basedata.ts`（基础数据 API）
- Create: `src/types/basedata.ts`（类型定义）

**Interfaces:**
- Consumes: 后端 API `/api/v1/companies`, `/api/v1/manufacturers`
- Produces: `basedataApi.getCompanies()`, `basedataApi.getManufacturers()` 等方法

- [ ] **Step 1: 创建 axios 实例**

```typescript
// src/api/request.ts
import axios, { AxiosInstance, AxiosResponse, AxiosError } from 'axios'
import { Message } from '@arco-design/web-vue'

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000,
})

// 请求拦截器：添加 Authorization 头
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, data, message } = response.data
    if (code === 200) {
      return data
    } else {
      Message.error(message || '请求失败')
      return Promise.reject(new Error(message))
    }
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      Message.error('登录已过期，请重新登录')
      localStorage.removeItem('access_token')
      window.location.href = '/login'
    } else {
      Message.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
```

- [ ] **Step 2: 定义基础数据类型**

```typescript
// src/types/basedata.ts
export interface Company {
  id: number
  code: string
  name: string
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface Manufacturer {
  id: number
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface ManufacturerForm {
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
}

export interface Supplier {
  id: number
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface SupplierForm {
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
}

export interface Category {
  id: number
  name: string
  code?: string
  parentId?: number
  sortOrder: number
  remark?: string
  children?: Category[]
}

export interface Location {
  id: number
  name: string
  code?: string
  parentId?: number
  path: string
  sortOrder: number
  remark?: string
  children?: Location[]
}

export interface AssetModel {
  id: number
  name: string
  modelNumber?: string
  categoryId?: number
  manufacturerId?: number
  depreciationId?: number
  eolMonths?: number
  notes?: string
  createdAt: string
  updatedAt: string
}

export interface AssetModelForm {
  name: string
  modelNumber?: string
  categoryId?: number
  manufacturerId?: number
  depreciationId?: number
  eolMonths?: number
  notes?: string
}
```

- [ ] **Step 3: 创建基础数据 API**

```typescript
// src/api/basedata.ts
import request from './request'
import type {
  Company,
  Manufacturer,
  ManufacturerForm,
  Supplier,
  SupplierForm,
  Category,
  Location,
  AssetModel,
  AssetModelForm,
} from '@/types/basedata'

export const basedataApi = {
  // 公司
  getCompanies: () => request.get<any, Company[]>('/api/v1/companies'),
  getCompanyById: (id: number) => request.get<any, Company>(`/api/v1/companies/${id}`),

  // 厂商
  getManufacturers: () => request.get<any, Manufacturer[]>('/api/v1/manufacturers'),
  getManufacturerById: (id: number) => request.get<any, Manufacturer>(`/api/v1/manufacturers/${id}`),
  createManufacturer: (data: ManufacturerForm) => request.post<any, Manufacturer>('/api/v1/manufacturers', data),
  updateManufacturer: (id: number, data: ManufacturerForm) => request.put<any, Manufacturer>(`/api/v1/manufacturers/${id}`, data),
  deleteManufacturer: (id: number) => request.delete<any, void>(`/api/v1/manufacturers/${id}`),

  // 供应商
  getSuppliers: () => request.get<any, Supplier[]>('/api/v1/suppliers'),
  getSupplierById: (id: number) => request.get<any, Supplier>(`/api/v1/suppliers/${id}`),
  createSupplier: (data: SupplierForm) => request.post<any, Supplier>('/api/v1/suppliers', data),
  updateSupplier: (id: number, data: SupplierForm) => request.put<any, Supplier>(`/api/v1/suppliers/${id}`, data),
  deleteSupplier: (id: number) => request.delete<any, void>(`/api/v1/suppliers/${id}`),

  // 分类（树形结构）
  getCategories: () => request.get<any, Category[]>('/api/v1/categories'),
  getCategoryById: (id: number) => request.get<any, Category>(`/api/v1/categories/${id}`),

  // 位置（树形结构）
  getLocations: (parentId?: number) => 
    request.get<any, Location[]>('/api/v1/locations', { params: { parentId } }),
  getLocationById: (id: number) => request.get<any, Location>(`/api/v1/locations/${id}`),
  createLocation: (data: { name: string; parentId?: number; remark?: string }) => 
    request.post<any, Location>('/api/v1/locations', data),

  // 型号
  getModels: (categoryId?: number) => 
    request.get<any, AssetModel[]>('/api/v1/models', { params: { categoryId } }),
  getModelById: (id: number) => request.get<any, AssetModel>(`/api/v1/models/${id}`),
  createModel: (data: AssetModelForm) => request.post<any, AssetModel>('/api/v1/models', data),
  updateModel: (id: number, data: AssetModelForm) => request.put<any, AssetModel>(`/api/v1/models/${id}`, data),
}
```

- [ ] **Step 4: 测试 API 调用**

创建临时测试文件：
```typescript
// src/api/__test__/basedata.test.ts
import { basedataApi } from '../basedata'

async function testAPIs() {
  try {
    const companies = await basedataApi.getCompanies()
    console.log('公司列表:', companies)
    
    const manufacturers = await basedataApi.getManufacturers()
    console.log('厂商列表:', manufacturers)
  } catch (error) {
    console.error('API 测试失败:', error)
  }
}

// 在浏览器控制台执行
testAPIs()
```

预期：无报错，返回空数组或种子数据

- [ ] **Step 5: 删除测试文件**

```bash
rm src/api/__test__/basedata.test.ts
```

- [ ] **Step 6: 提交**

```bash
git add src/api/*.ts src/types/*.ts
git commit -m "feat: API层封装"
```

---

### Task 3: Pinia Store 状态管理

**Files:**
- Create: `src/stores/basedata.ts`
- Create: `src/stores/index.ts`
- Modify: `src/main.ts`（注册 Pinia）

**Interfaces:**
- Consumes: `basedataApi.getCompanies()`, `basedataApi.getManufacturers()` 等方法
- Produces: `useBasedataStore()` 提供 `companies`, `manufacturers` 等响应式状态

- [ ] **Step 1: 创建 Pinia Store**

```typescript
// src/stores/basedata.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { basedataApi } from '@/api/basedata'
import type {
  Company,
  Manufacturer,
  Supplier,
  Category,
  Location,
  AssetModel,
} from '@/types/basedata'

export const useBasedataStore = defineStore('basedata', () => {
  // 状态
  const companies = ref<Company[]>([])
  const manufacturers = ref<Manufacturer[]>([])
  const suppliers = ref<Supplier[]>([])
  const categories = ref<Category[]>([])
  const locations = ref<Location[]>([])
  const models = ref<AssetModel[]>([])

  // 加载状态
  const loading = ref({
    companies: false,
    manufacturers: false,
    suppliers: false,
    categories: false,
    locations: false,
    models: false,
  })

  // 操作方法
  const fetchCompanies = async () => {
    loading.value.companies = true
    try {
      companies.value = await basedataApi.getCompanies()
    } finally {
      loading.value.companies = false
    }
  }

  const fetchManufacturers = async () => {
    loading.value.manufacturers = true
    try {
      manufacturers.value = await basedataApi.getManufacturers()
    } finally {
      loading.value.manufacturers = false
    }
  }

  const fetchSuppliers = async () => {
    loading.value.suppliers = true
    try {
      suppliers.value = await basedataApi.getSuppliers()
    } finally {
      loading.value.suppliers = false
    }
  }

  const fetchCategories = async () => {
    loading.value.categories = true
    try {
      categories.value = await basedataApi.getCategories()
    } finally {
      loading.value.categories = false
    }
  }

  const fetchLocations = async (parentId?: number) => {
    loading.value.locations = true
    try {
      locations.value = await basedataApi.getLocations(parentId)
    } finally {
      loading.value.locations = false
    }
  }

  const fetchModels = async (categoryId?: number) => {
    loading.value.models = true
    try {
      models.value = await basedataApi.getModels(categoryId)
    } finally {
      loading.value.models = false
    }
  }

  return {
    // 状态
    companies,
    manufacturers,
    suppliers,
    categories,
    locations,
    models,
    loading,
    // 方法
    fetchCompanies,
    fetchManufacturers,
    fetchSuppliers,
    fetchCategories,
    fetchLocations,
    fetchModels,
  }
})
```

- [ ] **Step 2: 创建 Store 入口文件**

```typescript
// src/stores/index.ts
import { createPinia } from 'pinia'

const pinia = createPinia()

export default pinia
```

- [ ] **Step 3: 在 main.ts 注册 Pinia**

```typescript
// src/main.ts（添加以下代码）
import pinia from './stores'

app.use(pinia)
```

- [ ] **Step 4: 验证 Store 可用**

在浏览器控制台执行：
```javascript
import { useBasedataStore } from './stores/basedata'
const store = useBasedataStore()
await store.fetchCompanies()
console.log(store.companies)
```

预期：返回公司列表数据

- [ ] **Step 5: 提交**

```bash
git add src/stores/*.ts src/main.ts
git commit -m "feat: Pinia状态管理"
```

---

### Task 4: 厂商管理页面（CRUD 完整示例）

**Files:**
- Create: `src/views/basedata/ManufacturerList.vue`
- Create: `src/views/basedata/components/ManufacturerModal.vue`
- Modify: `src/router/index.ts`（添加路由）

**Interfaces:**
- Consumes: `useBasedataStore().manufacturers`, `basedataApi.createManufacturer()`
- Produces: 厂商列表页面（含新增/编辑/删除）

- [ ] **Step 1: 创建厂商模态框组件**

```vue
<!-- src/views/basedata/components/ManufacturerModal.vue -->
<template>
  <a-modal
    :visible="visible"
    :title="isEdit ? '编辑厂商' : '新增厂商'"
    @ok="handleSubmit"
    @cancel="handleCancel"
    :confirm-loading="loading"
  >
    <a-form :model="formData" :rules="rules" ref="formRef">
      <a-form-item field="name" label="厂商名称" required>
        <a-input v-model="formData.name" placeholder="请输入厂商名称" />
      </a-form-item>
      <a-form-item field="contact" label="联系人">
        <a-input v-model="formData.contact" placeholder="请输入联系人" />
      </a-form-item>
      <a-form-item field="phone" label="联系电话">
        <a-input v-model="formData.phone" placeholder="请输入联系电话" />
      </a-form-item>
      <a-form-item field="address" label="地址">
        <a-input v-model="formData.address" placeholder="请输入地址" />
      </a-form-item>
      <a-form-item field="remark" label="备注">
        <a-textarea v-model="formData.remark" placeholder="请输入备注" :max-length="500" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { basedataApi } from '@/api/basedata'
import type { Manufacturer, ManufacturerForm } from '@/types/basedata'

interface Props {
  visible: boolean
  data?: Manufacturer
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref()
const loading = ref(false)
const isEdit = ref(false)
const formData = reactive<ManufacturerForm>({
  name: '',
  contact: '',
  phone: '',
  address: '',
  remark: '',
})

const rules = {
  name: [{ required: true, message: '请输入厂商名称' }],
}

watch(() => props.visible, (val) => {
  if (val && props.data) {
    isEdit.value = true
    Object.assign(formData, {
      name: props.data.name,
      contact: props.data.contact,
      phone: props.data.phone,
      address: props.data.address,
      remark: props.data.remark,
    })
  } else {
    isEdit.value = false
    Object.assign(formData, {
      name: '',
      contact: '',
      phone: '',
      address: '',
      remark: '',
    })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    if (isEdit.value && props.data) {
      await basedataApi.updateManufacturer(props.data.id, formData)
      Message.success('更新成功')
    } else {
      await basedataApi.createManufacturer(formData)
      Message.success('新增成功')
    }
    emit('success')
    emit('update:visible', false)
  } catch (error) {
    // 错误已在拦截器处理
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  emit('update:visible', false)
}
</script>
```

- [ ] **Step 2: 创建厂商列表页面**

```vue
<!-- src/views/basedata/ManufacturerList.vue -->
<template>
  <div class="page-container">
    <h1 class="page-title">厂商管理</h1>
    
    <div class="card">
      <div class="btn-group" style="margin-bottom: 16px;">
        <a-button type="primary" @click="handleAdd">
          <template #icon><icon-plus /></template>
          新增厂商
        </a-button>
      </div>

      <a-table
        :data="manufacturers"
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #columns>
          <a-table-column title="厂商名称" data-index="name" />
          <a-table-column title="联系人" data-index="contact" />
          <a-table-column title="联系电话" data-index="phone" />
          <a-table-column title="地址" data-index="address" :ellipsis="true" :tooltip="true" />
          <a-table-column title="操作" :width="180">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" @click="handleEdit(record)">编辑</a-button>
                <a-popconfirm content="确认删除该厂商吗？" @ok="handleDelete(record.id)">
                  <a-button type="text" status="danger">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </div>

    <ManufacturerModal
      v-model:visible="modalVisible"
      :data="currentRecord"
      @success="handleSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { IconPlus } from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'
import { useBasedataStore } from '@/stores/basedata'
import { basedataApi } from '@/api/basedata'
import ManufacturerModal from './components/ManufacturerModal.vue'
import type { Manufacturer } from '@/types/basedata'

const store = useBasedataStore()
const manufacturers = computed(() => store.manufacturers)
const loading = computed(() => store.loading.manufacturers)

const modalVisible = ref(false)
const currentRecord = ref<Manufacturer>()

const loadData = async () => {
  await store.fetchManufacturers()
}

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
    Message.success('删除成功')
    loadData()
  } catch (error) {
    // 错误已在拦截器处理
  }
}

const handleSuccess = () => {
  loadData()
}

onMounted(() => {
  loadData()
})
</script>
```

- [ ] **Step 3: 添加路由**

```typescript
// src/router/index.ts（在 routes 数组中添加）
{
  path: '/basedata/manufacturers',
  name: 'ManufacturerList',
  component: () => import('@/views/basedata/ManufacturerList.vue'),
  meta: { title: '厂商管理' },
},
```

- [ ] **Step 4: 启动开发服务器测试**

```bash
npm run dev
```

访问 `http://localhost:5173/basedata/manufacturers`

验证功能：
- 列表加载
- 新增厂商（填写必填项）
- 编辑厂商
- 删除厂商（二次确认）

- [ ] **Step 5: 测试键盘交互**

- `Tab` 键遍历按钮和表格行
- `Enter` 键提交表单
- `Esc` 键关闭模态框

预期：所有键盘操作正常

- [ ] **Step 6: 提交**

```bash
git add src/views/basedata/*.vue src/router/index.ts
git commit -m "feat: 厂商管理页面"
```

---

### Task 5: 供应商管理页面（复用厂商模式）

**Files:**
- Create: `src/views/basedata/SupplierList.vue`
- Create: `src/views/basedata/components/SupplierModal.vue`
- Modify: `src/router/index.ts`

**Interfaces:**
- Consumes: `useBasedataStore().suppliers`, `basedataApi.createSupplier()`
- Produces: 供应商列表页面（含新增/编辑/删除）

- [ ] **Step 1: 创建供应商模态框（复制厂商模态框并修改）**

```vue
<!-- src/views/basedata/components/SupplierModal.vue -->
<template>
  <a-modal
    :visible="visible"
    :title="isEdit ? '编辑供应商' : '新增供应商'"
    @ok="handleSubmit"
    @cancel="handleCancel"
    :confirm-loading="loading"
  >
    <a-form :model="formData" :rules="rules" ref="formRef">
      <a-form-item field="name" label="供应商名称" required>
        <a-input v-model="formData.name" placeholder="请输入供应商名称" />
      </a-form-item>
      <a-form-item field="contact" label="联系人">
        <a-input v-model="formData.contact" placeholder="请输入联系人" />
      </a-form-item>
      <a-form-item field="phone" label="联系电话">
        <a-input v-model="formData.phone" placeholder="请输入联系电话" />
      </a-form-item>
      <a-form-item field="address" label="地址">
        <a-input v-model="formData.address" placeholder="请输入地址" />
      </a-form-item>
      <a-form-item field="remark" label="备注">
        <a-textarea v-model="formData.remark" placeholder="请输入备注" :max-length="500" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { basedataApi } from '@/api/basedata'
import type { Supplier, SupplierForm } from '@/types/basedata'

interface Props {
  visible: boolean
  data?: Supplier
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref()
const loading = ref(false)
const isEdit = ref(false)
const formData = reactive<SupplierForm>({
  name: '',
  contact: '',
  phone: '',
  address: '',
  remark: '',
})

const rules = {
  name: [{ required: true, message: '请输入供应商名称' }],
}

watch(() => props.visible, (val) => {
  if (val && props.data) {
    isEdit.value = true
    Object.assign(formData, {
      name: props.data.name,
      contact: props.data.contact,
      phone: props.data.phone,
      address: props.data.address,
      remark: props.data.remark,
    })
  } else {
    isEdit.value = false
    Object.assign(formData, {
      name: '',
      contact: '',
      phone: '',
      address: '',
      remark: '',
    })
  }
})

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    if (isEdit.value && props.data) {
      await basedataApi.updateSupplier(props.data.id, formData)
      Message.success('更新成功')
    } else {
      await basedataApi.createSupplier(formData)
      Message.success('新增成功')
    }
    emit('success')
    emit('update:visible', false)
  } catch (error) {
    // 错误已在拦截器处理
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  emit('update:visible', false)
}
</script>
```

- [ ] **Step 2: 创建供应商列表页面**

```vue
<!-- src/views/basedata/SupplierList.vue -->
<template>
  <div class="page-container">
    <h1 class="page-title">供应商管理</h1>
    
    <div class="card">
      <div class="btn-group" style="margin-bottom: 16px;">
        <a-button type="primary" @click="handleAdd">
          <template #icon><icon-plus /></template>
          新增供应商
        </a-button>
      </div>

      <a-table
        :data="suppliers"
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #columns>
          <a-table-column title="供应商名称" data-index="name" />
          <a-table-column title="联系人" data-index="contact" />
          <a-table-column title="联系电话" data-index="phone" />
          <a-table-column title="地址" data-index="address" :ellipsis="true" :tooltip="true" />
          <a-table-column title="操作" :width="180">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" @click="handleEdit(record)">编辑</a-button>
                <a-popconfirm content="确认删除该供应商吗？" @ok="handleDelete(record.id)">
                  <a-button type="text" status="danger">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </div>

    <SupplierModal
      v-model:visible="modalVisible"
      :data="currentRecord"
      @success="handleSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { IconPlus } from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'
import { useBasedataStore } from '@/stores/basedata'
import { basedataApi } from '@/api/basedata'
import SupplierModal from './components/SupplierModal.vue'
import type { Supplier } from '@/types/basedata'

const store = useBasedataStore()
const suppliers = computed(() => store.suppliers)
const loading = computed(() => store.loading.suppliers)

const modalVisible = ref(false)
const currentRecord = ref<Supplier>()

const loadData = async () => {
  await store.fetchSuppliers()
}

const handleAdd = () => {
  currentRecord.value = undefined
  modalVisible.value = true
}

const handleEdit = (record: Supplier) => {
  currentRecord.value = record
  modalVisible.value = true
}

const handleDelete = async (id: number) => {
  try {
    await basedataApi.deleteSupplier(id)
    Message.success('删除成功')
    loadData()
  } catch (error) {
    // 错误已在拦截器处理
  }
}

const handleSuccess = () => {
  loadData()
}

onMounted(() => {
  loadData()
})
</script>
```

- [ ] **Step 3: 添加路由**

```typescript
// src/router/index.ts（在 routes 数组中添加）
{
  path: '/basedata/suppliers',
  name: 'SupplierList',
  component: () => import('@/views/basedata/SupplierList.vue'),
  meta: { title: '供应商管理' },
},
```

- [ ] **Step 4: 验证功能**

访问 `http://localhost:5173/basedata/suppliers`

测试：新增、编辑、删除供应商

- [ ] **Step 5: 提交**

```bash
git add src/views/basedata/SupplierList.vue src/views/basedata/components/SupplierModal.vue src/router/index.ts
git commit -m "feat: 供应商管理页面"
```

---

### Task 6: 公司列表页面（只读）

**Files:**
- Create: `src/views/basedata/CompanyList.vue`
- Modify: `src/router/index.ts`

**Interfaces:**
- Consumes: `useBasedataStore().companies`
- Produces: 公司列表页面（只读）

- [ ] **Step 1: 创建公司列表页面**

```vue
<!-- src/views/basedata/CompanyList.vue -->
<template>
  <div class="page-container">
    <h1 class="page-title">公司主体</h1>
    
    <div class="card">
      <a-table
        :data="companies"
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #columns>
          <a-table-column title="公司编码" data-index="code" :width="120" />
          <a-table-column title="公司名称" data-index="name" />
          <a-table-column title="备注" data-index="remark" :ellipsis="true" :tooltip="true" />
          <a-table-column title="创建时间" data-index="createdAt" :width="180" />
        </template>
      </a-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useBasedataStore } from '@/stores/basedata'

const store = useBasedataStore()
const companies = computed(() => store.companies)
const loading = computed(() => store.loading.companies)

onMounted(async () => {
  await store.fetchCompanies()
})
</script>
```

- [ ] **Step 2: 添加路由**

```typescript
// src/router/index.ts
{
  path: '/basedata/companies',
  name: 'CompanyList',
  component: () => import('@/views/basedata/CompanyList.vue'),
  meta: { title: '公司主体' },
},
```

- [ ] **Step 3: 验证功能**

访问页面，确认公司列表正常显示

- [ ] **Step 4: 提交**

```bash
git add src/views/basedata/CompanyList.vue src/router/index.ts
git commit -m "feat: 公司列表页面"
```

---

### Task 7: 基础数据导航菜单集成

**Files:**
- Modify: `src/components/layout/Sidebar.vue`（添加基础数据子菜单）

**Interfaces:**
- Consumes: Vue Router routes
- Produces: 左侧导航显示基础数据菜单项

- [ ] **Step 1: 修改侧边栏组件添加基础数据菜单**

```vue
<!-- src/components/layout/Sidebar.vue（在现有导航项后添加）-->
<template>
  <!-- 现有代码保持不变 -->
  
  <!-- 新增：基础数据菜单组 -->
  <a-menu-item-group title="基础数据">
    <a-menu-item key="companies">
      <router-link to="/basedata/companies">
        <icon-office />
        <span>公司主体</span>
      </router-link>
    </a-menu-item>
    <a-menu-item key="manufacturers">
      <router-link to="/basedata/manufacturers">
        <icon-tags />
        <span>厂商管理</span>
      </router-link>
    </a-menu-item>
    <a-menu-item key="suppliers">
      <router-link to="/basedata/suppliers">
        <icon-user-group />
        <span>供应商管理</span>
      </router-link>
    </a-menu-item>
  </a-menu-item-group>
</template>

<script setup lang="ts">
// 导入图标
import { IconOffice, IconTags, IconUserGroup } from '@arco-design/web-vue/es/icon'
</script>
```

- [ ] **Step 2: 验证导航功能**

刷新页面，点击侧边栏菜单项，确认路由跳转正常

- [ ] **Step 3: 提交**

```bash
git add src/components/layout/Sidebar.vue
git commit -m "feat: 基础数据导航菜单"
```

---

### Task 8: 验收测试与文档更新

**Files:**
- Create: `docs/M02-前端验收清单.md`
- Update: `README.md`（更新项目进度）

**Interfaces:**
- Consumes: 所有已实现页面
- Produces: 验收清单文档

- [ ] **Step 1: 创建验收清单**

```markdown
<!-- docs/M02-前端验收清单.md -->
# M02 基础数据前端验收清单

## 功能验收

### 公司主体
- [ ] GET /api/v1/companies 返回列表
- [ ] 表格正常显示
- [ ] 创建时间格式正确

### 厂商管理
- [ ] 列表加载正常
- [ ] 新增厂商：必填项校验
- [ ] 编辑厂商：回显数据
- [ ] 删除厂商：二次确认
- [ ] 操作成功显示 Toast 提示

### 供应商管理
- [ ] 列表加载正常
- [ ] 新增供应商：必填项校验
- [ ] 编辑供应商：回显数据
- [ ] 删除供应商：二次确认
- [ ] 操作成功显示 Toast 提示

## 交互验收

### 键盘操作
- [ ] Tab 遍历所有可交互元素
- [ ] Enter 提交表单
- [ ] Esc 关闭模态框
- [ ] 焦点状态清晰可见（蓝色描边）

### 鼠标交互
- [ ] 按钮 hover 状态正常
- [ ] 表格行 hover 背景变浅
- [ ] 表单控件 focus 显示蓝色描边

### 反馈机制
- [ ] 加载态：按钮显示 loading
- [ ] 成功提示：绿色 Toast 2 秒消失
- [ ] 失败提示：红色 Toast 5 秒消失
- [ ] 删除二次确认弹窗

## 视觉验收

### 色彩
- [ ] 主按钮：#165DFF
- [ ] 文字主色：#1D2129
- [ ] 边框色：#E5E6EB
- [ ] 页面背景：#F7F8FA

### 间距
- [ ] 页面内边距：24px
- [ ] 卡片内边距：20px
- [ ] 按钮组间距：8px

### 字体
- [ ] 页面标题：20px / 600
- [ ] 正文：14px / 400
- [ ] 按钮：14px / 500

## 性能验收

- [ ] 首屏渲染 < 2s
- [ ] 模块切换流畅
- [ ] 表格渲染 100 条数据 < 300ms
```

- [ ] **Step 2: 执行验收测试**

按清单逐项测试，全部通过后打勾

- [ ] **Step 3: 更新 README.md**

```markdown
<!-- README.md（更新进度部分）-->
## 开发进度

- [x] M-FE01: 前端骨架（鉴权验证、工作台）
- [x] M02-Frontend: 基础数据 CRUD（公司/厂商/供应商）
- [ ] M02-Frontend-Tree: 分类/位置树形结构
- [ ] M02-Frontend-Model: 型号管理（关联分类+厂商）
```

- [ ] **Step 4: 提交**

```bash
git add docs/M02-前端验收清单.md README.md
git commit -m "docs: M02验收清单"
```

---

## 执行说明

**计划完成！** 共 8 个任务，预计开发时间：**4-6 小时**

**两种执行方式：**

1. **Subagent-Driven（推荐）**：每个任务派发独立 subagent，任务间人工审阅，快速迭代
2. **Inline Execution**：在当前会话批量执行，设置检查点（Task 4 和 Task 8 后）

**下一步工作（M02 剩余部分）：**
- 分类树形结构页面（TreeSelect 组件）
- 位置树形结构页面（支持层级展示）
- 型号管理页面（关联分类和厂商下拉框）

请选择执行方式？
