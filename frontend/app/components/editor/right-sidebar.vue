<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { CSSProperties } from 'vue'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'

const emit = defineEmits<{
  'save': []
  'open-history': []
  'open-xml-editor': []
  'save-and-complete': []
}>()

const props = defineProps<{
  rightRailWidthPx: number
  useFloatingCollapsed: boolean
  isSavingActiveCanvas: boolean
  canEditActiveCanvas: boolean
  canOpenActiveCanvasXmlEditor: boolean
  canCompleteActivePageSubtasks: boolean
  isCompletingOpenSubtasks: boolean
  isActivePageLocked: boolean
  actionItems: DropdownMenuItem[][]
}>()

const editorUiStore = useEditorUiStore()
const sidebarShellRef = ref<HTMLElement | null>(null)
const floatingPosition = ref<{ x: number, y: number } | null>(null)
const isDraggingSidebar = ref(false)
const isFloatingCollapsed = computed(() => editorUiStore.rightCollapsed && props.useFloatingCollapsed)

let dragPointerId: number | null = null
let dragStartClientX = 0
let dragStartClientY = 0
let dragStartSidebarX = 0
let dragStartSidebarY = 0

function getMeasuredShellWidth() {
  const width = sidebarShellRef.value?.getBoundingClientRect().width ?? 0
  return width > 0 ? width : 48
}

function getMeasuredShellHeight() {
  const height = sidebarShellRef.value?.getBoundingClientRect().height ?? 0
  return height > 0 ? height : 320
}

function getViewportSize() {
  return {
    width: window.innerWidth || document.documentElement.clientWidth,
    height: window.innerHeight || document.documentElement.clientHeight
  }
}

function clampSidebarPosition(x: number, y: number) {
  const { width, height } = getViewportSize()
  const shellWidth = getMeasuredShellWidth()
  const shellHeight = getMeasuredShellHeight()
  const maxX = Math.max(8, width - shellWidth - 8)
  const maxY = Math.max(8, height - shellHeight - 8)

  return {
    x: Math.min(Math.max(8, x), maxX),
    y: Math.min(Math.max(8, y), maxY)
  }
}

function getDefaultFloatingPosition() {
  const { width, height } = getViewportSize()
  const shellWidth = getMeasuredShellWidth()

  return clampSidebarPosition(width - shellWidth - 16, Math.round(height * 0.1))
}

function ensureFloatingPosition() {
  if (!import.meta.client || !isFloatingCollapsed.value) return

  if (!floatingPosition.value) {
    floatingPosition.value = getDefaultFloatingPosition()
    return
  }

  floatingPosition.value = clampSidebarPosition(floatingPosition.value.x, floatingPosition.value.y)
}

const floatingSidebarStyle = computed<CSSProperties | undefined>(() => {
  if (!isFloatingCollapsed.value) return undefined

  const position = floatingPosition.value ?? { x: 16, y: 96 }
  return {
    left: `${position.x}px`,
    top: `${position.y}px`
  }
})

function handleSidebarDragMove(event: PointerEvent) {
  if (!isDraggingSidebar.value || event.pointerId !== dragPointerId) return

  floatingPosition.value = clampSidebarPosition(
    dragStartSidebarX + event.clientX - dragStartClientX,
    dragStartSidebarY + event.clientY - dragStartClientY
  )
}

function stopSidebarDrag(event?: PointerEvent) {
  if (event && dragPointerId !== null && event.pointerId !== dragPointerId) return

  window.removeEventListener('pointermove', handleSidebarDragMove)
  window.removeEventListener('pointerup', stopSidebarDrag)
  window.removeEventListener('pointercancel', stopSidebarDrag)

  dragPointerId = null
  isDraggingSidebar.value = false
}

function startSidebarDrag(event: PointerEvent) {
  if (!isFloatingCollapsed.value) return

  ensureFloatingPosition()

  const position = floatingPosition.value ?? getDefaultFloatingPosition()
  dragPointerId = event.pointerId
  dragStartClientX = event.clientX
  dragStartClientY = event.clientY
  dragStartSidebarX = position.x
  dragStartSidebarY = position.y
  isDraggingSidebar.value = true

  window.addEventListener('pointermove', handleSidebarDragMove)
  window.addEventListener('pointerup', stopSidebarDrag)
  window.addEventListener('pointercancel', stopSidebarDrag)
}

function handleFloatingSidebarResize() {
  ensureFloatingPosition()
}

onMounted(() => {
  if (!import.meta.client) return

  requestAnimationFrame(() => ensureFloatingPosition())
  window.addEventListener('resize', handleFloatingSidebarResize)
})

onBeforeUnmount(() => {
  if (!import.meta.client) return

  window.removeEventListener('resize', handleFloatingSidebarResize)
  stopSidebarDrag()
})

watch(() => editorUiStore.rightCollapsed, (collapsed) => {
  if (!import.meta.client || !collapsed || !props.useFloatingCollapsed) return

  requestAnimationFrame(() => ensureFloatingPosition())
})

watch(() => props.useFloatingCollapsed, (enabled) => {
  if (!import.meta.client || !enabled || !editorUiStore.rightCollapsed) return

  requestAnimationFrame(() => ensureFloatingPosition())
})
</script>

<template>
  <aside
    ref="sidebarShellRef"
    data-tour="editor-right-sidebar"
    :class="isFloatingCollapsed
      ? 'fixed z-40 flex w-12 max-h-[calc(100vh-7rem)] flex-col items-center overflow-y-auto rounded-xl border border-default bg-neutral-50 px-1 py-2 shadow-2xl dark:bg-neutral-900'
      : 'h-full flex flex-col border-l border-default bg-elevated/25 gap-y-2 p-2'"
    :style="isFloatingCollapsed
      ? floatingSidebarStyle
      : { width: (editorUiStore.rightCollapsed ? props.rightRailWidthPx : editorUiStore.rightWidthPx) + 'px' }"
  >
    <template v-if="isFloatingCollapsed">
      <UTooltip text="Drag sidebar" :content="{ side: 'left' }">
        <UButton
          variant="ghost"
          color="neutral"
          icon="i-lucide-grip-horizontal"
          size="sm"
          aria-label="Drag sidebar"
          :class="isDraggingSidebar ? 'cursor-grabbing' : 'cursor-grab'"
          @pointerdown.prevent.stop="startSidebarDrag"
          @click.prevent.stop
        />
      </UTooltip>

      <USeparator orientation="horizontal" class="my-1 w-6" />

      <UTooltip text="Expand right sidebar" :content="{ side: 'left' }">
        <UButton
          variant="ghost"
          color="neutral"
          icon="i-lucide-panel-right-open"
          size="sm"
          class="shrink-0"
          aria-label="Expand sidebar"
          @click="editorUiStore.toggleRightCollapsed"
        />
      </UTooltip>

      <USeparator orientation="horizontal" class="my-1 w-6" />

      <UTooltip text="Save" :content="{ side: 'left' }">
        <UButton
          variant="ghost"
          color="neutral"
          icon="i-lucide-save"
          size="sm"
          class="shrink-0"
          aria-label="Save"
          :loading="isSavingActiveCanvas"
          loading-icon="i-lucide-loader"
          :disabled="isSavingActiveCanvas || !canEditActiveCanvas"
          @click="emit('save')"
        />
      </UTooltip>

      <UDropdownMenu :items="actionItems">
        <UButton
          color="neutral"
          variant="ghost"
          icon="i-lucide-ellipsis-vertical"
          size="sm"
          class="shrink-0"
          aria-label="Open page actions"
        />
      </UDropdownMenu>

      <USeparator orientation="horizontal" class="my-1 w-6" />

      <div class="flex flex-col items-center gap-1">
        <slot />
      </div>
    </template>

    <template v-else-if="editorUiStore.rightCollapsed">
      <div class="shrink-0 flex justify-center flex-col items-center gap-1">
        <UButton
          type="button"
          variant="ghost"
          color="neutral"
          icon="i-lucide-panel-right-open"
          aria-label="Expand sidebar"
          @click="editorUiStore.toggleRightCollapsed"
        />

        <UTooltip text="Save" :content="{ side: 'left' }">
          <UButton
            variant="ghost"
            color="neutral"
            icon="i-lucide-save"
            size="sm"
            aria-label="Save"
            :loading="isSavingActiveCanvas"
            loading-icon="i-lucide-loader"
            :disabled="isSavingActiveCanvas || !canEditActiveCanvas"
            @click="emit('save')"
          />
        </UTooltip>

        <UDropdownMenu :items="actionItems">
          <UButton
            color="neutral"
            variant="ghost"
            icon="i-lucide-ellipsis-vertical"
            size="sm"
            aria-label="Open page actions"
          />
        </UDropdownMenu>
      </div>

      <div class="min-h-0 flex-1 overflow-auto">
        <slot />
      </div>
    </template>

    <template v-else>
      <div class="shrink-0 flex justify-between">
        <UButton
          type="button"
          variant="ghost"
          color="neutral"
          icon="i-lucide-panel-right-close"
          aria-label="Collapse sidebar"
          @click="editorUiStore.toggleRightCollapsed"
        />

        <UFieldGroup>
          <UTooltip v-bind="getTooltipProps('save')">
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-save"
              label="Save"
              :loading="isSavingActiveCanvas"
              loading-icon="i-lucide-loader"
              :disabled="isSavingActiveCanvas || !canEditActiveCanvas"
              @click="emit('save')"
            />
          </UTooltip>

          <UDropdownMenu :items="actionItems">
            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-chevron-down"
            />
          </UDropdownMenu>
        </UFieldGroup>
      </div>

      <div class="min-h-0 flex-1 overflow-auto">
        <slot />
      </div>
    </template>
  </aside>
</template>
