<script setup lang="ts">
import type { KeyboardLayout, KeyboardItem } from '@/types/virtual-keyboard'

const props = defineProps<{
  layout: KeyboardLayout
  layouts?: KeyboardLayout[]
}>()

const emit = defineEmits(['update:layoutId'])

const visible = ref(false)
const minimized = ref(false)
const rowIndex = ref(0)
const cellSize = ref(60)
const x = ref(100)
const y = ref(100)
const activeInput = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
const dismissedInput = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
const hasBeenPositioned = ref(false)

const isShiftPressed = ref(false)
const pressedKeys = ref(new Set<number>())
const echoingKeys = ref(new Set<number>())
const showInfo = ref(false)

const keyboardRootRef = ref<HTMLElement | null>(null)
const palette = useVirtualKeyboardPalette()

const drag = reactive({ active: false, startX: 0, startY: 0, initialWinX: 0, initialWinY: 0 })
const resize = reactive({ active: false, startX: 0, startWidth: 0 })

const isTextInputTarget = (target: EventTarget | null): target is HTMLInputElement | HTMLTextAreaElement =>
  target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement

const layoutSelectItems = computed(() =>
  (props.layouts ?? []).map(l => ({ label: l.name, value: l.id }))
)
const selectedLayoutId = computed({
  get: () => props.layout.id,
  set: id => emit('update:layoutId', id)
})

const isShiftKey = (item: KeyboardItem) => item.char === 'Shift' || item.description === 'Shift Modifier'

const typeKey = (item: KeyboardItem) => {
  pressedKeys.value.add(item.id)
  setTimeout(() => pressedKeys.value.delete(item.id), 150)

  if (isShiftKey(item)) {
    isShiftPressed.value = !isShiftPressed.value
    return
  }

  if (activeInput.value) {
    const charToType = (isShiftPressed.value && item.shiftChar) ? item.shiftChar : item.char
    const input = activeInput.value
    const start = input.selectionStart || 0
    const end = input.selectionEnd || 0
    const text = input.value

    input.value = text.substring(0, start) + charToType + text.substring(end)
    input.selectionStart = input.selectionEnd = start + charToType.length
    input.dispatchEvent(new Event('input', { bubbles: true }))
  }
}

const handlePhysicalKeyDown = (e: KeyboardEvent) => {
  if (!visible.value) return
  props.layout.items.forEach((item) => {
    if (item.char === e.key || (isShiftPressed.value && item.shiftChar === e.key) || (e.key === 'Shift' && isShiftKey(item))) {
      echoingKeys.value.add(item.id)
      setTimeout(() => echoingKeys.value.delete(item.id), 200)
    }
  })
  if (e.key === 'Shift') isShiftPressed.value = true
}

const handlePhysicalKeyUp = (e: KeyboardEvent) => {
  if (e.key === 'Shift') isShiftPressed.value = false
}

const updatePosition = () => {
  if (hasBeenPositioned.value || !activeInput.value || drag.active || resize.active) return

  const rect = activeInput.value.getBoundingClientRect()
  const actualHeight = minimized.value
    ? cellSize.value + 44
    : (props.layout.rows * cellSize.value) + 44

  const gap = 12
  const kbWidth = props.layout.cols * cellSize.value

  let left = rect.left + (rect.width / 2) - (kbWidth / 2)
  if (left < 10) left = 10
  if (left + kbWidth > window.innerWidth) left = window.innerWidth - kbWidth - 10
  x.value = left

  let top
  const spaceBelow = window.innerHeight - rect.bottom
  const spaceAbove = rect.top

  if (spaceBelow >= (actualHeight + gap)) top = rect.bottom + gap
  else if (spaceAbove >= (actualHeight + gap)) top = rect.top - actualHeight - gap
  else if (spaceBelow > spaceAbove) top = rect.bottom + gap
  else top = rect.top - actualHeight - gap

  y.value = Math.max(0, top)
  hasBeenPositioned.value = true
}

const onFocus = (e: FocusEvent) => {
  const target = e.target
  if (!(target instanceof HTMLElement)) return

  if (keyboardRootRef.value?.contains(target)) return
  if (!isTextInputTarget(target)) return
  if (dismissedInput.value === target) return

  if (activeInput.value !== target) {
    hasBeenPositioned.value = false
  }

  dismissedInput.value = null
  activeInput.value = target
  visible.value = true
  showInfo.value = false
  nextTick(updatePosition)
}

const onBlur = (e: FocusEvent) => {
  if (e.target === dismissedInput.value) {
    dismissedInput.value = null
  }

  setTimeout(() => {
    if (document.activeElement instanceof HTMLElement && keyboardRootRef.value?.contains(document.activeElement)) return
    if (document.activeElement !== activeInput.value && !resize.active && !drag.active) {
      visible.value = false
      activeInput.value = null
      hasBeenPositioned.value = false
    }
  }, 150)
}

const closeKeyboard = () => {
  dismissedInput.value = activeInput.value
  visible.value = false
  showInfo.value = false
}

const handleMouseMove = (e: MouseEvent) => {
  if (drag.active) {
    x.value = drag.initialWinX + (e.clientX - drag.startX)
    y.value = drag.initialWinY + (e.clientY - drag.startY)
  }
  if (resize.active) {
    const dx = e.clientX - resize.startX
    const newCell = (resize.startWidth + dx) / props.layout.cols
    cellSize.value = Math.max(15, Math.min(120, newCell))
  }
}

const handleMouseUp = () => {
  drag.active = false
  resize.active = false
}

onMounted(() => {
  window.addEventListener('keydown', handlePhysicalKeyDown)
  window.addEventListener('keyup', handlePhysicalKeyUp)
  window.addEventListener('focusin', onFocus)
  window.addEventListener('focusout', onBlur)
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseup', handleMouseUp)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handlePhysicalKeyDown)
  window.removeEventListener('keyup', handlePhysicalKeyUp)
  window.removeEventListener('focusin', onFocus)
  window.removeEventListener('focusout', onBlur)
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseup', handleMouseUp)
})

const startDrag = (e: MouseEvent) => {
  drag.active = true
  drag.startX = e.clientX
  drag.startY = e.clientY
  drag.initialWinX = x.value
  drag.initialWinY = y.value
  showInfo.value = false
}

const startResize = (e: MouseEvent) => {
  resize.active = true
  resize.startX = e.clientX
  resize.startWidth = props.layout.cols * cellSize.value
}

const changeMinimizedRow = (delta: number) => {
  const newRow = rowIndex.value + delta
  if (newRow >= 0 && newRow < props.layout.rows) rowIndex.value = newRow
}

const toggleMinimized = () => {
  minimized.value = !minimized.value
}

const gridLineColor = computed(() => getVirtualKeyboardGridLineColor(palette.value.boardStyle))

defineExpose({ visible })
</script>

<template>
  <Teleport to="body">
    <Transition name="vk">
      <div
        v-if="visible"
        ref="keyboardRootRef"
        class="flex flex-col box-content overflow-visible select-none font-junicode fixed rounded-sm shadow-xl border border-default bg-default"
        style="z-index: 9999;"
        :style="{
          left: x + 'px',
          top: y + 'px',
          width: (layout.cols * cellSize) + 'px'
        }"
      >
        <div
          class="h-11 bg-elevated border-b border-default rounded-t-lg flex items-center justify-between px-3"
        >
          <div class="flex items-center gap-2">
            <UButton
              variant="ghost"
              size="xs"
              icon="i-lucide-grip-vertical"
              class="cursor-move"
              title="Drag keyboard"
              @mousedown.stop.prevent="startDrag"
            />

            <USelectMenu
              v-if="layouts && layouts.length > 0"
              v-model="selectedLayoutId"
              :items="layoutSelectItems"
              value-key="value"
              :portal="false"
              :search-input="{ placeholder: 'Search keyboards...' }"
              class="w-40"
              size="xs"
              :ui="{ base: 'cursor-pointer' }"
              @mousedown.stop
            />

            <UButton
              v-if="layout.description"
              variant="ghost"
              size="xs"
              icon="i-lucide-info"
              @click.stop="showInfo = !showInfo"
              @mousedown.prevent.stop
            />
            <div v-if="showInfo" class="absolute top-12 left-3 w-56 bg-elevated border border-default p-3 rounded-sm shadow-xl text-xs z-50">
              {{ layout.description }}
            </div>

            <div v-if="minimized" class="flex items-center gap-1">
              <span class="text-xs text-muted">{{ rowIndex + 1 }}/{{ layout.rows }}</span>
              <UFieldGroup size="xs">
                <UButton
                  variant="ghost"
                  icon="i-lucide-chevron-up"
                  @click.stop="changeMinimizedRow(-1)"
                  @mousedown.prevent.stop
                />
                <UButton
                  variant="ghost"
                  icon="i-lucide-chevron-down"
                  @click.stop="changeMinimizedRow(1)"
                  @mousedown.prevent.stop
                />
              </UFieldGroup>
            </div>
          </div>

          <div class="flex items-center gap-1">
            <UButton
              variant="ghost"
              size="xs"
              :icon="minimized ? 'i-lucide-maximize-2' : 'i-lucide-minimize-2'"
              :title="minimized ? 'Expand' : 'Minimize'"
              @click.stop="toggleMinimized"
              @mousedown.prevent.stop
            />
            <UButton
              variant="ghost"
              size="xs"
              icon="i-lucide-x"
              @click.stop="closeKeyboard"
              @mousedown.prevent.stop
            />
          </div>
        </div>

        <div
          class="relative w-full transition-[height] duration-200 ease-out overflow-hidden rounded-b-lg"
          :class="[palette.boardClass, palette.boardBorderClass]"
          :style="{ height: minimized ? cellSize + 'px' : (layout.rows * cellSize) + 'px', background: palette.boardStyle }"
        >
          <div
            class="relative w-full transition-transform duration-200 ease-out"
            :style="{
              height: (layout.rows * cellSize) + 'px',
              transform: minimized ? `translateY(-${rowIndex * cellSize}px)` : 'translateY(0)'
            }"
          >
            <div class="absolute inset-0 grid pointer-events-none" :style="{ gridTemplateColumns: `repeat(${layout.cols}, 1fr)`, gridTemplateRows: `repeat(${layout.rows}, 1fr)` }">
              <div
                v-for="n in (layout.cols * layout.rows)"
                :key="n"
                class="border-[0.5px]"
                :class="palette.gridLineClass"
                :style="{ borderColor: gridLineColor }"
              />
            </div>

            <div
              v-for="item in layout.items"
              :key="item.id"
              class="absolute flex flex-col z-20 transition-transform active:scale-95"
              :style="{
                left: (item.x * cellSize) + 'px',
                top: (item.y * cellSize) + 'px',
                width: (item.w * cellSize) + 'px',
                height: cellSize + 'px',
                padding: (cellSize * 0.05) + 'px'
              }"
              @mousedown.prevent="typeKey(item)"
            >
              <VirtualKeyboardKeyCap
                :item="item"
                :is-shift-pressed="isShiftPressed"
                :is-pressed="pressedKeys.has(item.id)"
                :is-echoing="echoingKeys.has(item.id)"
                :palette="palette"
                :cell-size="cellSize"
              />
            </div>
          </div>
        </div>

        <div v-if="!minimized" class="absolute bottom-0 right-0 w-4 h-4 cursor-nwse-resize z-50 flex items-end justify-end p-[1px]" @mousedown.stop.prevent="startResize">
          <svg viewBox="0 0 10 10" class="w-2 h-2 text-muted fill-current opacity-50"><path d="M10 10 L10 0 L0 10 Z" /></svg>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.vk-enter-active, .vk-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.vk-enter-from, .vk-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
