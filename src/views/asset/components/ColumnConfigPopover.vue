<script setup lang="ts">
import { ref } from 'vue'
import type { ColumnDef } from '@/composables/useColumnConfig'

/**
 * 列配置弹层：勾选显隐 + HTML5 拖拽排序（无第三方依赖）
 * 拖拽过程中显示落点指示线，松手调 move(from, to)
 */
const props = defineProps<{
  columns: ColumnDef[]
  visibleCount: number
}>()

const emit = defineEmits<{
  (e: 'toggle', key: string): void
  (e: 'move', from: number, to: number): void
  (e: 'reset'): void
}>()

let dragIndex = -1
const dragOverIndex = ref(-1)

const onDragStart = (index: number) => {
  dragIndex = index
}
const onDragOver = (index: number, e: DragEvent) => {
  e.preventDefault()
  dragOverIndex.value = index
}
const onDrop = (index: number) => {
  if (dragIndex >= 0 && dragIndex !== index) emit('move', dragIndex, index)
  dragIndex = -1
  dragOverIndex.value = -1
}
const onDragEnd = () => {
  dragIndex = -1
  dragOverIndex.value = -1
}
</script>

<template>
  <div class="col-config">
    <div class="col-config__header">
      <span class="col-config__title">列显示（{{ visibleCount }}/{{ props.columns.length }}）</span>
      <button class="col-config__reset" type="button" @click="emit('reset')">恢复默认</button>
    </div>
    <ul class="col-config__list">
      <li
        v-for="(col, index) in props.columns"
        :key="col.key"
        class="col-config__item"
        :class="{ 'is-drag-over': dragOverIndex === index && dragIndex !== index }"
        draggable="true"
        @dragstart="onDragStart(index)"
        @dragover="onDragOver(index, $event)"
        @drop="onDrop(index)"
        @dragend="onDragEnd"
      >
        <el-checkbox
          :model-value="col.visible"
          :disabled="col.visible && visibleCount <= 1"
          @change="emit('toggle', col.key)"
        >
          {{ col.label }}
        </el-checkbox>
        <span class="col-config__drag" title="拖拽调整列顺序">⠿</span>
      </li>
    </ul>
    <div class="col-config__footer">拖拽 ⠿ 调整列顺序，勾选控制显隐，偏好自动保存</div>
  </div>
</template>

<style scoped>
.col-config {
  width: 200px;
  display: flex;
  flex-direction: column;
}

.col-config__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 4px;
}

.col-config__title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-1);
}

.col-config__reset {
  font-size: var(--text-xs);
  color: var(--color-primary);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.col-config__reset:hover {
  text-decoration: underline;
}

.col-config__list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 300px;
  overflow-y: auto;
}

.col-config__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
  border-radius: var(--radius-sm);
  cursor: grab;
  user-select: none;
}

.col-config__item:hover {
  background: var(--color-bg-3);
}

.col-config__item.is-drag-over {
  box-shadow: inset 0 2px 0 var(--color-primary);
}

.col-config__drag {
  color: var(--color-text-4);
  font-size: var(--text-md);
  line-height: 1;
  cursor: grab;
}

.col-config__footer {
  padding: 8px 4px 0;
  border-top: 1px solid var(--color-border-light);
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--color-text-4);
}
</style>
