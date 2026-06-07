<script setup lang="ts">
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { toProjectToolkitSettings, useProjectToolkitPresets } from '@/composables/editor/use-project-toolkit-presets'
import type { KeyboardItem } from '@/types/virtual-keyboard'
import { LazyEditorModalToolkitResourceEdit, LazyUiConfirmSlideover } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'

interface WrappedKeyboardItem {
  id: string
  item: KeyboardItem
  x: number
  w: number
}

interface WrappedKeyboardRow {
  id: string
  cols: number
  items: WrappedKeyboardItem[]
}

const {
  keyboards,
  selectedLayout,
  selectedKeyboardId
} = useVirtualKeyboards()
const palette = useVirtualKeyboardPalette()
const toast = useToast()
const overlay = useOverlay()
const workspace = useWorkspaceStore()
const editorStore = useEditorStore()
const sessionStore = useEditorSessionStore()
const { patchProjectToolkitPresets } = useProjectToolkitPresets()
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)
const editModal = overlay.create(LazyEditorModalToolkitResourceEdit)

const hasKeyboards = computed(() => (keyboards.value ?? []).length > 0)
const selectedWorkspaceId = computed(() => workspace.selectedWorkspaceId as string)
const keyboardsKey = computed(() => wsKey(selectedWorkspaceId.value, 'virtual-keyboards', 'list'))
const activeProjectId = computed(() => sessionStore.activeProjectId)
const canSetProjectPresets = computed(() => workspace.currentWorkspace?.capabilities?.canSetPresets ?? workspace.isCurrentUserOwner)
const canCreateKeyboard = computed(() => workspace.canManageToolkit)
const canSwitchVirtualKeyboard = computed(() => canSetProjectPresets.value || editorStore.projectToolkitSettings.allowVirtualKeyboardOverride)
const isSavingDefault = ref(false)

const layoutSelectItems = computed(() =>
  (keyboards.value ?? []).map(layout => ({ label: layout.name, value: layout.id }))
)

const selectedLayoutId = computed({
  get: () => selectedKeyboardId.value ?? selectedLayout.value?.id ?? '',
  set: (id: string | null | undefined) => {
    if (!canSwitchVirtualKeyboard.value) {
      toast.add({ title: 'Virtual keyboard switching is fixed for this project', color: 'warning' })
      return
    }
    selectedKeyboardId.value = id || null
  }
})
const selectedKeyboard = computed(() => (keyboards.value ?? []).find(keyboard => keyboard.id === selectedLayoutId.value) ?? null)
const canEditSelectedKeyboard = computed(() => Boolean(selectedKeyboard.value?.capabilities?.canEdit))
const canClearVirtualKeyboard = computed(() => Boolean(selectedLayoutId.value) && canSwitchVirtualKeyboard.value)

const keyboardRootRef = ref<HTMLElement | null>(null)
const keyboardViewportRef = ref<HTMLDivElement | null>(null)
const keyboardViewportWidth = ref(0)
let resizeObserver: ResizeObserver | null = null

const TARGET_CELL_SIZE = 38
const MIN_CELL_SIZE = 34
const MAX_CELL_SIZE = 60
const HORIZONTAL_PADDING_PX = 16

const activeInput = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)
const stickyShiftPressed = ref(false)
const physicalShiftPressed = ref(false)
const pressedKeys = ref(new Set<number>())
const isShiftPressed = computed(() => stickyShiftPressed.value || physicalShiftPressed.value)

const isTextInputTarget = (target: EventTarget | null): target is HTMLInputElement | HTMLTextAreaElement =>
  target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement

const isShiftKey = (item: KeyboardItem) => item.char === 'Shift' || item.description === 'Shift Modifier'

const wrapColumns = computed(() => {
  const layout = selectedLayout.value
  if (!layout) return 1

  const availableWidth = Math.max(120, keyboardViewportWidth.value - HORIZONTAL_PADDING_PX)
  const preferredCols = Math.max(1, Math.floor(availableWidth / TARGET_CELL_SIZE))
  return Math.max(1, Math.min(layout.cols, preferredCols))
})

const isWrappedLayout = computed(() => {
  const layout = selectedLayout.value
  if (!layout) return false
  return wrapColumns.value < layout.cols
})

const cellSize = computed(() => {
  const layout = selectedLayout.value
  if (!layout) return 42

  const colsPerLine = wrapColumns.value
  const availableWidth = Math.max(colsPerLine * MIN_CELL_SIZE, keyboardViewportWidth.value - HORIZONTAL_PADDING_PX)
  const fittedSize = Math.floor(availableWidth / colsPerLine)
  return Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, fittedSize))
})

const wrappedRows = computed<WrappedKeyboardRow[]>(() => {
  const layout = selectedLayout.value
  if (!layout) return []

  const colsPerLine = Math.max(1, wrapColumns.value)
  const rowSegments = Math.max(1, Math.ceil(layout.cols / colsPerLine))
  const rows: WrappedKeyboardRow[] = []
  const rowItems = [...(layout.items ?? [])].sort((a, b) => a.x - b.x || a.id - b.id)

  for (let y = 0; y < layout.rows; y++) {
    for (let segment = 0; segment < rowSegments; segment++) {
      const segmentStart = segment * colsPerLine
      const segmentEnd = Math.min(layout.cols, segmentStart + colsPerLine)
      const items: WrappedKeyboardItem[] = []

      for (const item of rowItems) {
        if (item.y !== y) continue

        const itemStart = item.x
        const itemEnd = item.x + Math.max(1, item.w)
        const overlapStart = Math.max(itemStart, segmentStart)
        const overlapEnd = Math.min(itemEnd, segmentEnd)

        if (overlapEnd <= overlapStart) continue

        items.push({
          id: `${item.id}:${segment}:${overlapStart}:${overlapEnd}`,
          item,
          x: overlapStart - segmentStart,
          w: overlapEnd - overlapStart
        })
      }

      rows.push({
        id: `row-${y}-segment-${segment}`,
        cols: segmentEnd - segmentStart,
        items
      })
    }
  }

  return rows
})

const gridLineColor = computed(() => getVirtualKeyboardGridLineColor(palette.value.boardStyle))

const activeInputLabel = computed(() => {
  const input = activeInput.value
  if (!input) return null

  const placeholder = input.getAttribute('placeholder')?.trim()
  if (placeholder) return `Focused: ${placeholder}`

  const id = input.getAttribute('id')?.trim()
  if (id) return `Focused: ${id}`

  const name = input.getAttribute('name')?.trim()
  if (name) return `Focused: ${name}`

  return 'Focused text field'
})

const typeKey = (item: KeyboardItem) => {
  pressedKeys.value.add(item.id)
  setTimeout(() => pressedKeys.value.delete(item.id), 150)

  if (isShiftKey(item)) {
    stickyShiftPressed.value = !stickyShiftPressed.value
    return
  }

  const input = activeInput.value
  if (!input) return

  const charToType = (isShiftPressed.value && item.shiftChar) ? item.shiftChar : item.char
  const start = input.selectionStart || 0
  const end = input.selectionEnd || 0
  const text = input.value

  input.value = text.substring(0, start) + charToType + text.substring(end)
  input.selectionStart = input.selectionEnd = start + charToType.length
  input.dispatchEvent(new Event('input', { bubbles: true }))
}

const onWindowKeyDown = (event: KeyboardEvent) => {
  physicalShiftPressed.value = event.shiftKey || event.key === 'Shift'
}

const onWindowKeyUp = (event: KeyboardEvent) => {
  physicalShiftPressed.value = event.shiftKey
}

const onWindowBlur = () => {
  physicalShiftPressed.value = false
}

const onFocusIn = (event: FocusEvent) => {
  const target = event.target
  if (!(target instanceof HTMLElement)) return
  if (keyboardRootRef.value?.contains(target)) return
  if (!isTextInputTarget(target)) return
  activeInput.value = target
}

const onFocusOut = (event: FocusEvent) => {
  if (event.target !== activeInput.value) return

  queueMicrotask(() => {
    const currentlyFocused = document.activeElement
    if (currentlyFocused instanceof HTMLElement && keyboardRootRef.value?.contains(currentlyFocused)) {
      return
    }
    if (!isTextInputTarget(currentlyFocused)) {
      activeInput.value = null
      stickyShiftPressed.value = false
      physicalShiftPressed.value = false
    }
  })
}

async function saveKeyboardDefault() {
  const workspaceId = selectedWorkspaceId.value
  const projectId = activeProjectId.value
  if (!workspaceId || !projectId || !canSetProjectPresets.value) return

  isSavingDefault.value = true
  try {
    const updated = await patchProjectToolkitPresets(workspaceId, projectId, {
      virtualKeyboardId: selectedKeyboardId.value ?? null
    })
    editorStore.setProjectToolkitSettings(toProjectToolkitSettings(updated), projectId)
    toast.add({ title: 'Project virtual keyboard default updated', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Could not save keyboard default', description: error instanceof Error ? error.message : undefined, color: 'error' })
  } finally {
    isSavingDefault.value = false
  }
}

async function confirmSaveKeyboardDefault() {
  if (!canSetProjectPresets.value || isSavingDefault.value) return
  const instance = confirmSlideover.open({
    title: 'Set Default Virtual Keyboard',
    message: 'This changes the project default virtual keyboard and affects all users working on this project.',
    confirmLabel: 'Set Default',
    confirmIcon: 'i-lucide-save',
    confirmColor: 'warning'
  })
  const confirmed = await instance.result
  if (!confirmed) return
  await saveKeyboardDefault()
}

async function openKeyboardEditor() {
  const keyboard = selectedKeyboard.value
  const workspaceId = selectedWorkspaceId.value
  if (!keyboard || !workspaceId || !canEditSelectedKeyboard.value) return

  const reloadKeyboards = async () => {
    await refreshNuxtData(keyboardsKey.value)
  }

  const instance = editModal.open({
    title: `Edit Virtual Keyboard · ${keyboard.name}`,
    src: `/virtual-keyboard/${keyboard.id}?embedded=toolkit-editor`,
    onSaved: reloadKeyboards
  })
  await instance.result
  await reloadKeyboards()
}

async function openKeyboardCreateModal() {
  const workspaceId = selectedWorkspaceId.value
  if (!workspaceId || !canCreateKeyboard.value) return

  const reloadKeyboards = async () => {
    await refreshNuxtData(keyboardsKey.value)
  }

  const instance = editModal.open({
    title: 'Create Virtual Keyboard',
    src: '/virtual-keyboard/new?embedded=toolkit-editor',
    onSaved: reloadKeyboards
  })
  await instance.result
  await reloadKeyboards()
}

const actionItems = computed<DropdownMenuItem[][]>(() => {
  const items: DropdownMenuItem[] = []
  if (canCreateKeyboard.value) {
    items.push({
      label: 'Create',
      icon: 'i-lucide-plus',
      onSelect: openKeyboardCreateModal
    })
  }
  if (canSetProjectPresets.value) {
    items.push({
      label: 'Set as default',
      icon: 'i-lucide-save',
      disabled: isSavingDefault.value,
      onSelect: confirmSaveKeyboardDefault
    })
  }
  if (canEditSelectedKeyboard.value) {
    items.push({
      label: 'Edit',
      icon: 'i-lucide-pencil',
      onSelect: openKeyboardEditor
    })
  }
  return items.length > 0 ? [items] : []
})

function updateKeyboardViewportWidth() {
  const width = keyboardViewportRef.value?.clientWidth ?? 0
  keyboardViewportWidth.value = width > 0 ? width : 0
}

onMounted(() => {
  window.addEventListener('focusin', onFocusIn)
  window.addEventListener('focusout', onFocusOut)
  document.addEventListener('keydown', onWindowKeyDown, true)
  document.addEventListener('keyup', onWindowKeyUp, true)
  window.addEventListener('blur', onWindowBlur)

  if (typeof ResizeObserver !== 'undefined' && keyboardViewportRef.value) {
    resizeObserver = new ResizeObserver(() => {
      updateKeyboardViewportWidth()
    })
    resizeObserver.observe(keyboardViewportRef.value)
  }

  updateKeyboardViewportWidth()
})

onBeforeUnmount(() => {
  window.removeEventListener('focusin', onFocusIn)
  window.removeEventListener('focusout', onFocusOut)
  document.removeEventListener('keydown', onWindowKeyDown, true)
  document.removeEventListener('keyup', onWindowKeyUp, true)
  window.removeEventListener('blur', onWindowBlur)

  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
</script>

<template>
  <div ref="keyboardRootRef" class="p-3 space-y-3">
    <div class="relative z-30 flex items-center gap-2">
      <USelectMenu
        v-model="selectedLayoutId"
        class="min-w-0 flex-1"
        :items="layoutSelectItems"
        value-key="value"
        :clear="canClearVirtualKeyboard"
        :search-input="{ placeholder: 'Search keyboards...' }"
        :disabled="!hasKeyboards || !canSwitchVirtualKeyboard"
        placeholder="Choose a virtual keyboard"
        size="sm"
      />
      <UDropdownMenu v-if="actionItems.length > 0" :items="actionItems" :content="{ align: 'end' }">
        <UButton
          size="sm"
          variant="ghost"
          color="neutral"
          icon="i-lucide-more-vertical"
          aria-label="Virtual keyboard actions"
          :loading="isSavingDefault"
        />
      </UDropdownMenu>
    </div>

    <UAlert
      v-if="!hasKeyboards"
      icon="i-lucide-keyboard-off"
      title="No Virtual Keyboards"
      description="No virtual keyboards are available in this workspace. Create one to use this panel."
      color="neutral"
      variant="soft"
    />

    <template v-else>
      <p v-if="!canSwitchVirtualKeyboard" class="text-xs text-muted">
        This project uses a fixed virtual keyboard.
      </p>

      <UAlert
        v-if="!selectedLayout"
        icon="i-lucide-keyboard"
        title="No virtual keyboard selected"
        :description="canSwitchVirtualKeyboard ? 'Choose a virtual keyboard to use it in this editor session.' : 'A project manager must set a default virtual keyboard.'"
        color="neutral"
        variant="soft"
      />

      <div v-else class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
        <span>{{ selectedLayout.cols }}x{{ selectedLayout.rows }} grid</span>
        <span v-if="isWrappedLayout">Wrapped to {{ wrapColumns }} columns per line</span>
        <span v-if="activeInputLabel" class="truncate max-w-full">{{ activeInputLabel }}</span>
        <span v-else>Focus a text input to type with this keyboard</span>
      </div>

      <div
        v-if="selectedLayout"
        ref="keyboardViewportRef"
        class="relative z-0 overflow-y-auto overflow-x-hidden"
      >
        <div
          class="mx-auto overflow-hidden font-junicode rounded-sm p-2"
          :class="[palette.boardClass]"
          :style="{ background: palette.boardStyle }"
        >
          <div class="flex flex-col gap-1.5">
            <div
              v-for="row in wrappedRows"
              :key="row.id"
              class="relative"
              :style="{
                width: `${row.cols * cellSize}px`,
                height: `${cellSize}px`
              }"
            >
              <div
                class="absolute inset-0 grid pointer-events-none"
                :style="{ gridTemplateColumns: `repeat(${row.cols}, 1fr)` }"
              >
                <div
                  v-for="n in row.cols"
                  :key="`${row.id}-cell-${n}`"
                  class="border-[0.5px]"
                  :class="palette.gridLineClass"
                  :style="{ borderColor: gridLineColor }"
                />
              </div>

              <div
                v-for="wrappedItem in row.items"
                :key="wrappedItem.id"
                class="absolute flex flex-col transition-transform active:scale-95 cursor-pointer"
                :style="{
                  left: `${wrappedItem.x * cellSize}px`,
                  width: `${wrappedItem.w * cellSize}px`,
                  height: `${cellSize}px`,
                  padding: `${cellSize * 0.05}px`
                }"
                @mousedown.prevent="typeKey(wrappedItem.item)"
              >
                <VirtualKeyboardKeyCap
                  :item="wrappedItem.item"
                  :is-shift-pressed="isShiftPressed"
                  :is-pressed="pressedKeys.has(wrappedItem.item.id)"
                  :is-echoing="false"
                  :palette="palette"
                  :cell-size="cellSize"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
