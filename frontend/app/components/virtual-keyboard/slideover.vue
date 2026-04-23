<script setup lang="ts">
import type { KeyboardLayout, KeyboardItem } from '@/types/virtual-keyboard'

const props = defineProps<{
  layout: KeyboardLayout
  layouts?: KeyboardLayout[]
}>()

const emit = defineEmits(['update:layoutId'])

const open = ref(false)
const cellSize = ref(50)
const activeInput = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
const dismissedInput = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
const keyboardRootRef = ref<HTMLElement | null>(null)
const palette = useVirtualKeyboardPalette()

const isShiftPressed = ref(false)
const pressedKeys = ref(new Set<number>())

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

const onFocus = (e: FocusEvent) => {
  const target = e.target
  if (!(target instanceof HTMLElement)) return

  if (keyboardRootRef.value?.contains(target)) return
  if (!isTextInputTarget(target)) return
  if (dismissedInput.value === target) return

  dismissedInput.value = null
  activeInput.value = target
  open.value = true
}

const onBlur = (e: FocusEvent) => {
  if (e.target === dismissedInput.value) {
    dismissedInput.value = null
  }
}

const closeKeyboard = () => {
  dismissedInput.value = activeInput.value
  open.value = false
}

onMounted(() => {
  window.addEventListener('focusin', onFocus)
  window.addEventListener('focusout', onBlur)
})

onUnmounted(() => {
  window.removeEventListener('focusin', onFocus)
  window.removeEventListener('focusout', onBlur)
})

const gridLineColor = computed(() => getVirtualKeyboardGridLineColor(palette.value.boardStyle))

defineExpose({ open })
</script>

<template>
  <Teleport to="body">
    <Transition name="vk-slide">
      <div
        v-if="open"
        ref="keyboardRootRef"
        class="fixed bottom-0 left-0 right-0 bg-default border-t border-default shadow-2xl"
        style="z-index: 9998;"
      >
        <div class="flex flex-col max-h-[50vh]">
          <div class="flex items-center justify-between px-4 py-3 border-b border-default bg-elevated">
            <div class="flex items-center gap-3">
              <UIcon name="i-lucide-keyboard" class="w-5 h-5 text-muted" />
              <USelectMenu
                v-if="layouts && layouts.length > 0"
                v-model="selectedLayoutId"
                :items="layoutSelectItems"
                value-key="value"
                :portal="false"
                :search-input="{ placeholder: 'Search keyboards...' }"
                class="w-48"
                size="sm"
                @click.stop
              />
            </div>
            <UButton
              variant="ghost"
              size="sm"
              icon="i-lucide-x"
              @click="closeKeyboard"
            />
          </div>

          <div class="flex-1 overflow-auto p-6 flex justify-center bg-default">
            <div
              class="relative font-junicode rounded-sm p-3 shadow-[0_6px_20px_rgba(0,0,0,0.4),inset_0_1px_0_rgba(255,255,255,0.1)]"
              :class="[palette.boardClass]"
              :style="{ background: palette.boardStyle }"
            >
              <div
                class="relative"
                :style="{
                  width: (layout.cols * cellSize) + 'px',
                  height: (layout.rows * cellSize) + 'px'
                }"
                @mousedown.prevent
              >
                <div
                  class="absolute inset-0 grid pointer-events-none"
                  :style="{
                    gridTemplateColumns: `repeat(${layout.cols}, 1fr)`,
                    gridTemplateRows: `repeat(${layout.rows}, 1fr)`
                  }"
                >
                  <div
                    v-for="n in (layout.cols * layout.rows)"
                    :key="n"
                    class="border"
                    :class="palette.gridLineClass"
                    :style="{ borderColor: gridLineColor }"
                  />
                </div>

                <div
                  v-for="item in layout.items"
                  :key="item.id"
                  class="absolute flex flex-col z-20 transition-transform active:scale-95 cursor-pointer"
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
                    :is-echoing="false"
                    :palette="palette"
                    :cell-size="cellSize"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.vk-slide-enter-active,
.vk-slide-leave-active {
  transition: transform 0.2s ease-out;
}

.vk-slide-enter-from,
.vk-slide-leave-to {
  transform: translateY(100%);
}
</style>
