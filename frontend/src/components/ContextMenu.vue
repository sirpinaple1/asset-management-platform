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
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}

.ctx-menu__item {
  display: block;
  width: 100%;
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: var(--radius-md);
  background: none;
  font-size: var(--text-sm);
  color: var(--color-text-2);
  text-align: left;
  cursor: pointer;
}

.ctx-menu__item:hover {
  background: var(--color-bg-3);
}

.ctx-menu__item.is-danger {
  color: var(--color-error-text);
}

.ctx-menu__item.is-danger:hover {
  background: var(--color-error-bg);
}
</style>
