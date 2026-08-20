<script setup lang="ts">
import type { ContextMenuItem } from '@/composables/useListInteractions'

defineProps<{
  visible: boolean
  x: number
  y: number
  items: ContextMenuItem[]
}>()

const emit = defineEmits<{ (e: 'select', key: string): void }>()
</script>

<template>
  <teleport to="body">
    <div
      v-if="visible"
      class="ctx-menu"
      role="menu"
      :style="{ left: `${x}px`, top: `${y}px` }"
      @click.stop
      @contextmenu.prevent
    >
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        role="menuitem"
        class="ctx-menu__item"
        :class="{ 'is-danger': item.danger }"
        @click="emit('select', item.key)"
      >
        {{ item.label }}
      </button>
    </div>
  </teleport>
</template>

<style scoped>
.ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 140px;
  padding: 4px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}

.ctx-menu__item {
  display: block;
  width: 100%;
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: none;
  font-size: 13px;
  color: #4b5563;
  text-align: left;
  cursor: pointer;
}

.ctx-menu__item:hover {
  background: #f3f4f6;
}

.ctx-menu__item.is-danger {
  color: #f53f3f;
}

.ctx-menu__item.is-danger:hover {
  background: #fef0f0;
}
</style>
