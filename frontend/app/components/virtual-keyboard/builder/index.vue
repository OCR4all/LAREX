<script setup lang="ts">
import type { KeyboardItem } from '~/types/virtual-keyboard'
import { LazyVirtualKeyboardSlideoverEditKey } from '#components'

const props = defineProps<{
  state: VirtualKeyboardBuilderState
}>()

const CELL_SIZE = 60
const palette = useVirtualKeyboardPalette()

const { gridCols, gridRows, items } = props.state

const cellSize = ref(CELL_SIZE)
const overlay = useOverlay()

const editKeySlideover = overlay.create(LazyVirtualKeyboardSlideoverEditKey)

const openEditKey = async (item: KeyboardItem) => {
  const instance = editKeySlideover.open({ item, isValidPlacement })
  const result = await instance.result as (KeyboardItem & { _delete?: boolean }) | null
  if (!result) return
  if (result._delete) {
    items.value = items.value.filter(i => i.id !== result.id)
  } else if (result.id === 0) {
    result.id = Date.now()
    items.value.push(result)
  } else {
    const idx = items.value.findIndex(i => i.id === result.id)
    if (idx !== -1) items.value[idx] = result
  }
}

const gridRef = ref<HTMLElement | null>(null)

const isCellOccupied = (x: number, y: number, excludeId: number | null = null) =>
  items.value.some(item => item.id !== excludeId && x >= item.x && x <= item.x + item.w - 1 && y === item.y)

const isValidPlacement = (x: number, y: number, w: number, excludeId: number | null) => {
  if (x < 0 || y < 0 || x + w > gridCols.value || y >= gridRows.value) return false
  for (let i = 0; i < w; i++) if (isCellOccupied(x + i, y, excludeId)) return false
  return true
}

const handleGridClick = (flatIndex: number) => {
  const x = flatIndex % gridCols.value
  const y = Math.floor(flatIndex / gridCols.value)
  if (!isCellOccupied(x, y)) openEditKey({ id: 0, x, y, w: 1, char: '', shiftChar: '' })
}

const dragState = reactive({
  isDragging: false,
  draggingId: null as number | null,
  startX: 0, startY: 0,
  currentX: 0, currentY: 0,
  offsetX: 0, offsetY: 0,
  isValidDrop: false,
  snappedX: 0, snappedY: 0,
  hasMovedEnough: false
})

const startDrag = (e: MouseEvent, item: KeyboardItem) => {
  e.preventDefault()
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  Object.assign(dragState, {
    offsetX: e.clientX - rect.left,
    offsetY: e.clientY - rect.top,
    startX: e.clientX,
    startY: e.clientY,
    currentX: item.x * cellSize.value,
    currentY: item.y * cellSize.value,
    isDragging: true,
    hasMovedEnough: false,
    draggingId: item.id,
    isValidDrop: true,
    snappedX: item.x,
    snappedY: item.y
  })
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
}

const onDragMove = (e: MouseEvent) => {
  if (!dragState.hasMovedEnough && Math.hypot(e.clientX - dragState.startX, e.clientY - dragState.startY) <= 3) return
  dragState.hasMovedEnough = true

  const rect = gridRef.value!.getBoundingClientRect()
  dragState.currentX = e.clientX - rect.left - dragState.offsetX
  dragState.currentY = e.clientY - rect.top - dragState.offsetY

  const snapX = Math.round(dragState.currentX / cellSize.value)
  const snapY = Math.round(dragState.currentY / cellSize.value)
  const item = items.value.find(i => i.id === dragState.draggingId)
  if (item) {
    dragState.isValidDrop = isValidPlacement(snapX, snapY, item.w, item.id)
    if (dragState.isValidDrop) {
      dragState.snappedX = snapX
      dragState.snappedY = snapY
    }
  }
}

const onDragEnd = () => {
  if (dragState.isDragging) {
    const item = items.value.find(i => i.id === dragState.draggingId)
    if (!dragState.hasMovedEnough && item) {
      openEditKey({ ...item })
    } else if (dragState.isValidDrop && item) {
      item.x = dragState.snappedX
      item.y = dragState.snappedY
    }
  }
  dragState.isDragging = false
  dragState.draggingId = null
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
}

const gridLineColor = computed(() => getVirtualKeyboardGridLineColor(palette.value.boardStyle))
</script>

<template>
  <div class="relative w-full h-full flex flex-col items-center justify-center p-4 overflow-auto">
    <div
      ref="gridRef"
      data-tour="vk-builder-grid"
      class="relative rounded-sm border-[3px] shadow-2xl box-content"
      :class="[palette.boardClass, palette.boardBorderClass]"
      :style="{ width: (gridCols * cellSize) + 'px', height: (gridRows * cellSize) + 'px', background: palette.boardStyle }"
    >
      <div class="absolute inset-0 grid" :style="{ gridTemplateColumns: `repeat(${gridCols}, 1fr)`, gridTemplateRows: `repeat(${gridRows}, 1fr)` }">
        <div
          v-for="i in (gridCols * gridRows)"
          :key="i"
          class="border transition-colors cursor-pointer hover:bg-white/5"
          :class="palette.gridLineClass"
          :style="{ borderColor: gridLineColor }"
          @click="handleGridClick(i - 1)"
        />
      </div>

      <div
        v-if="dragState.isDragging && dragState.isValidDrop"
        class="absolute border-2 border-primary-400/50 bg-primary-500/10 rounded-sm z-10 pointer-events-none"
        :style="{
          left: (dragState.snappedX * cellSize) + 'px',
          top: (dragState.snappedY * cellSize) + 'px',
          width: ((items.find(i => i.id === dragState.draggingId)?.w || 1) * cellSize) + 'px',
          height: cellSize + 'px'
        }"
      />

      <div
        v-for="item in items"
        :key="item.id"
        class="absolute flex flex-col z-20"
        :class="dragState.draggingId === item.id ? 'cursor-grabbing z-50 opacity-90' : 'cursor-grab transition-all duration-200'"
        :style="{
          left: ((dragState.draggingId === item.id ? dragState.currentX : item.x * cellSize)) + 'px',
          top: ((dragState.draggingId === item.id ? dragState.currentY : item.y * cellSize)) + 'px',
          width: (item.w * cellSize) + 'px',
          height: cellSize + 'px',
          padding: (cellSize * 0.05) + 'px'
        }"
        @mousedown.stop="startDrag($event, item)"
      >
        <VirtualKeyboardKeyCap
          :item="item"
          :is-shift-pressed="false"
          :is-pressed="false"
          :is-echoing="false"
          :palette="palette"
          :cell-size="cellSize"
        />
      </div>
    </div>
  </div>
</template>
