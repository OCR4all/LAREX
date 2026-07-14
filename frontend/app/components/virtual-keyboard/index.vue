<script setup lang="ts">
import { LazyVirtualKeyboardSlideoverEditKey } from '#components'
import type { KeyboardLayout, KeyboardItem } from '@/types/virtual-keyboard'
import { wsKey } from '@/utils/fetch-keys'

const props = defineProps<{
  layout: KeyboardLayout
  layouts?: KeyboardLayout[]
  editable?: boolean
  workspaceId?: string | null
}>()

const emit = defineEmits<{
  'update:layoutId': [id: string]
  'updated': [layout: KeyboardLayout]
}>()

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
const editMode = ref(false)
const isSavingLayout = ref(false)

const keyboardRootRef = ref<HTMLElement | null>(null)
const keyboardGridRef = ref<HTMLElement | null>(null)
const palette = useVirtualKeyboardPalette()
const toast = useToast()
const overlay = useOverlay()
const workspaceStore = useWorkspaceStore()
const { allow } = useActionVisibility()
const editKeySlideover = overlay.create(LazyVirtualKeyboardSlideoverEditKey)

const drag = reactive({ active: false, startX: 0, startY: 0, initialWinX: 0, initialWinY: 0 })
const resize = reactive({ active: false, startX: 0, startWidth: 0 })
const keyDrag = reactive({
  active: false,
  draggingId: null as number | null,
  startX: 0,
  startY: 0,
  currentX: 0,
  currentY: 0,
  offsetX: 0,
  offsetY: 0,
  isValidDrop: false,
  snappedX: 0,
  snappedY: 0,
  hasMovedEnough: false
})

const isTextInputTarget = (target: EventTarget | null): target is HTMLInputElement | HTMLTextAreaElement =>
  target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement

const resolvedWorkspaceId = computed(() => props.workspaceId ?? workspaceStore.selectedWorkspaceId as string | null)
const canEditLayout = computed(() => props.editable === true && allow(props.layout.capabilities?.canEdit))
const layoutSelectItems = computed(() =>
  (props.layouts ?? []).map(l => ({ label: l.name, value: l.id }))
)
const selectedLayoutId = computed({
  get: () => props.layout.id,
  set: id => emit('update:layoutId', id)
})

const isShiftKey = (item: KeyboardItem) => item.char === 'Shift' || item.description === 'Shift Modifier'

const typeKey = (item: KeyboardItem) => {
  if (editMode.value) return

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

const isCellOccupied = (x: number, y: number, excludeId: number | null = null) =>
  props.layout.items.some(item => item.id !== excludeId && x >= item.x && x <= item.x + item.w - 1 && y === item.y)

const isValidPlacement = (x: number, y: number, w: number, excludeId: number | null) => {
  if (x < 0 || y < 0 || x + w > props.layout.cols || y >= props.layout.rows) return false
  for (let i = 0; i < w; i++) if (isCellOccupied(x + i, y, excludeId)) return false
  return true
}

const getRequestErrorMessage = (error: unknown) => {
  if (error && typeof error === 'object' && 'data' in error) {
    const data = (error as { data?: { message?: string, error?: string } }).data
    if (data?.message) return data.message
    if (data?.error) return data.error
  }
  return error instanceof Error ? error.message : 'Unexpected error'
}

const saveLayout = async (layout: KeyboardLayout) => {
  const workspaceId = resolvedWorkspaceId.value
  if (!canEditLayout.value || !workspaceId || !layout.id) return false

  isSavingLayout.value = true
  try {
    const saved = await $fetch<KeyboardLayout>(`/api/workspaces/${workspaceId}/virtual-keyboards/${layout.id}`, {
      method: 'PUT',
      body: layout
    })
    await refreshNuxtData(wsKey(workspaceId, 'virtual-keyboards', 'list'))
    await refreshNuxtData(wsKey(workspaceId, 'virtual-keyboards', layout.id))
    emit('updated', saved)
    return true
  } catch (error: unknown) {
    toast.add({
      title: 'Could not update keyboard',
      description: getRequestErrorMessage(error),
      color: 'error'
    })
    return false
  } finally {
    isSavingLayout.value = false
  }
}

const saveItems = async (items: KeyboardItem[]) => {
  const saved = await saveLayout({
    ...props.layout,
    items
  })
  if (saved) {
    toast.add({ title: 'Keyboard updated', color: 'success' })
  }
}

const openEditKey = async (item: KeyboardItem) => {
  if (!canEditLayout.value) return

  const instance = editKeySlideover.open({ item, isValidPlacement })
  const result = await instance.result as (KeyboardItem & { _delete?: boolean }) | null
  if (!result) return

  if (result._delete) {
    await saveItems(props.layout.items.filter(i => i.id !== result.id))
    return
  }

  if (result.id === 0) {
    await saveItems([
      ...props.layout.items,
      {
        ...result,
        id: Date.now()
      }
    ])
    return
  }

  const nextItems = props.layout.items.map(item => item.id === result.id ? { ...result } : item)
  await saveItems(nextItems)
}

const handleGridClick = (flatIndex: number) => {
  if (!editMode.value || !canEditLayout.value || keyDrag.active) return
  const x = flatIndex % props.layout.cols
  const y = Math.floor(flatIndex / props.layout.cols)
  if (!isCellOccupied(x, y)) {
    void openEditKey({ id: 0, x, y, w: 1, char: '', shiftChar: '' })
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
    if (editMode.value) return
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
  editMode.value = false
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

const startKeyDrag = (e: MouseEvent, item: KeyboardItem) => {
  if (!editMode.value || !canEditLayout.value) return

  e.preventDefault()
  e.stopPropagation()
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  Object.assign(keyDrag, {
    offsetX: e.clientX - rect.left,
    offsetY: e.clientY - rect.top,
    startX: e.clientX,
    startY: e.clientY,
    currentX: item.x * cellSize.value,
    currentY: item.y * cellSize.value,
    active: true,
    hasMovedEnough: false,
    draggingId: item.id,
    isValidDrop: true,
    snappedX: item.x,
    snappedY: item.y
  })
  window.addEventListener('mousemove', onKeyDragMove)
  window.addEventListener('mouseup', onKeyDragEnd)
}

const onKeyDragMove = (e: MouseEvent) => {
  if (!keyDrag.active || !keyboardGridRef.value) return
  if (!keyDrag.hasMovedEnough && Math.hypot(e.clientX - keyDrag.startX, e.clientY - keyDrag.startY) <= 3) return
  keyDrag.hasMovedEnough = true

  const rect = keyboardGridRef.value.getBoundingClientRect()
  keyDrag.currentX = e.clientX - rect.left - keyDrag.offsetX
  keyDrag.currentY = e.clientY - rect.top - keyDrag.offsetY

  const snapX = Math.round(keyDrag.currentX / cellSize.value)
  const snapY = Math.round(keyDrag.currentY / cellSize.value)
  const item = props.layout.items.find(i => i.id === keyDrag.draggingId)
  if (!item) return

  keyDrag.isValidDrop = isValidPlacement(snapX, snapY, item.w, item.id)
  if (keyDrag.isValidDrop) {
    keyDrag.snappedX = snapX
    keyDrag.snappedY = snapY
  }
}

const onKeyDragEnd = () => {
  const item = props.layout.items.find(i => i.id === keyDrag.draggingId)
  if (keyDrag.active && item) {
    if (!keyDrag.hasMovedEnough) {
      void openEditKey({ ...item })
    } else if (keyDrag.isValidDrop && (item.x !== keyDrag.snappedX || item.y !== keyDrag.snappedY)) {
      const nextItems = props.layout.items.map(current =>
        current.id === item.id
          ? { ...current, x: keyDrag.snappedX, y: keyDrag.snappedY }
          : current
      )
      void saveItems(nextItems)
    }
  }

  keyDrag.active = false
  keyDrag.draggingId = null
  window.removeEventListener('mousemove', onKeyDragMove)
  window.removeEventListener('mouseup', onKeyDragEnd)
}

const handleKeyMouseDown = (e: MouseEvent, item: KeyboardItem) => {
  if (editMode.value) {
    startKeyDrag(e, item)
    return
  }

  e.preventDefault()
  typeKey(item)
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
  window.removeEventListener('mousemove', onKeyDragMove)
  window.removeEventListener('mouseup', onKeyDragEnd)
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

watch(canEditLayout, (canEdit) => {
  if (!canEdit) editMode.value = false
})

watch(() => props.layout.id, () => {
  editMode.value = false
  keyDrag.active = false
  keyDrag.draggingId = null
})

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
              @click.stop="() => { showInfo = !showInfo }"
              @mousedown.prevent.stop
            />
            <div v-if="showInfo" class="absolute top-12 left-3 w-56 bg-elevated border border-default p-3 rounded-sm shadow-xl text-xs z-50">
              {{ layout.description }}
            </div>

            <UButton
              v-if="canEditLayout"
              variant="ghost"
              size="xs"
              :color="editMode ? 'primary' : 'neutral'"
              :icon="editMode ? 'i-lucide-check' : 'i-lucide-pencil'"
              :title="editMode ? 'Finish editing keyboard' : 'Edit keyboard layout'"
              :loading="isSavingLayout"
              @click.stop="() => { editMode = !editMode }"
              @mousedown.prevent.stop
            />

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
            ref="keyboardGridRef"
            class="relative w-full transition-transform duration-200 ease-out"
            :style="{
              height: (layout.rows * cellSize) + 'px',
              transform: minimized ? `translateY(-${rowIndex * cellSize}px)` : 'translateY(0)'
            }"
          >
            <div
              class="absolute inset-0 grid"
              :class="editMode ? 'pointer-events-auto' : 'pointer-events-none'"
              :style="{ gridTemplateColumns: `repeat(${layout.cols}, 1fr)`, gridTemplateRows: `repeat(${layout.rows}, 1fr)` }"
            >
              <div
                v-for="n in (layout.cols * layout.rows)"
                :key="n"
                class="border-[0.5px]"
                :class="[palette.gridLineClass, editMode ? 'cursor-pointer hover:bg-white/5' : '']"
                :style="{ borderColor: gridLineColor }"
                @mousedown.prevent.stop="handleGridClick(n - 1)"
              />
            </div>

            <div
              v-if="editMode && keyDrag.active && keyDrag.isValidDrop"
              class="absolute border-2 border-primary-400/50 bg-primary-500/10 rounded-sm z-10 pointer-events-none"
              :style="{
                left: (keyDrag.snappedX * cellSize) + 'px',
                top: (keyDrag.snappedY * cellSize) + 'px',
                width: ((layout.items.find(i => i.id === keyDrag.draggingId)?.w || 1) * cellSize) + 'px',
                height: cellSize + 'px'
              }"
            />

            <div
              v-for="item in layout.items"
              :key="item.id"
              class="absolute flex flex-col z-20"
              :class="[
                editMode
                  ? (keyDrag.draggingId === item.id ? 'cursor-grabbing z-50 opacity-90' : 'cursor-grab transition-all duration-200')
                  : 'cursor-pointer transition-transform active:scale-95'
              ]"
              :style="{
                left: ((keyDrag.draggingId === item.id ? keyDrag.currentX : item.x * cellSize)) + 'px',
                top: ((keyDrag.draggingId === item.id ? keyDrag.currentY : item.y * cellSize)) + 'px',
                width: (item.w * cellSize) + 'px',
                height: cellSize + 'px',
                padding: (cellSize * 0.05) + 'px'
              }"
              @mousedown="handleKeyMouseDown($event, item)"
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
