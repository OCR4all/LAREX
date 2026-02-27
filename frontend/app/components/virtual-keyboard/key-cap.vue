<script setup lang="ts">
import type { KeyboardItem, BoardTheme } from '@/types/keyboard'

const props = defineProps<{
  item: KeyboardItem
  isShiftPressed: boolean
  isPressed: boolean
  isEchoing: boolean
  theme: BoardTheme
  cellSize: number
}>()

const isShiftKey = computed(() => props.item.char === 'Shift' || props.item.description === 'Shift Modifier')
</script>

<template>
  <div
    class="keycap w-full h-full flex items-center justify-center relative overflow-hidden cursor-pointer select-none"
    :class="[
      item.colorClass || theme.keyBgClass,
      item.textClass || theme.keyTextClass,
      (isPressed || isEchoing) ? 'is-pressed' : '',
      isEchoing ? 'is-echoing ring-2 ring-primary-400' : '',
      (isShiftKey && isShiftPressed) ? 'shift-active' : ''
    ]"
    :style="{
      background: theme.keyBgStyle,
      color: theme.keyTextStyle
    }"
  >
    <div class="absolute inset-1 keycap-surface" />
    <div
      class="relative z-10 font-bold break-all text-center leading-none font-junicode"
      :style="{ fontSize: (cellSize * ((item.char || '').length > 1 ? 0.2 : 0.35)) + 'px' }"
    >
      {{ (isShiftPressed && item.shiftChar) ? item.shiftChar : item.char }}
    </div>
    <div
      v-if="item.shiftChar"
      class="absolute top-1 right-2 font-bold opacity-60 z-10 font-junicode"
      :style="{ fontSize: (cellSize * 0.15) + 'px' }"
    >
      {{ isShiftPressed ? item.char : item.shiftChar }}
    </div>
  </div>
</template>

<style scoped>
.keycap {
  border-radius: 0.4rem;
  background: linear-gradient(to bottom right, rgba(255,255,255,0.1), rgba(0,0,0,0.05));
  border: 1px solid rgba(255,255,255,0.1);
  box-shadow: inset 0 1px 1px rgba(255,255,255,0.2), 0 2px 4px rgba(0,0,0,0.2);
  transition: all 0.05s ease-out;
  transform: translateY(-1px);
}
.keycap:active, .keycap.is-pressed, .keycap.is-echoing {
  transform: scale(0.96);
  box-shadow: inset 0 2px 4px rgba(0,0,0,0.15), 0 0 1px rgba(0,0,0,0.1);
  background: linear-gradient(to bottom right, rgba(0,0,0,0.05), rgba(255,255,255,0.05));
}
.keycap.shift-active {
  border-color: #818cf8;
  box-shadow: 0 0 8px rgba(99, 102, 241, 0.5);
  transform: translateY(1px);
}
.keycap-surface {
  background: linear-gradient(to bottom right, rgba(0,0,0,0.02), rgba(0,0,0,0.08));
  border-radius: 0.3rem;
  box-shadow: inset 0 1px 3px rgba(0,0,0,0.1);
}
</style>
