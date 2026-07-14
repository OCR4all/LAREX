<script setup lang="ts">
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import type { LineWidthPreset } from '@/stores/editor/types'

const editorUiStore = useEditorUiStore()
const toast = useToast()

const colorPickerOpen = ref(false)
const BACKGROUND_SAVE_DEBOUNCE_MS = 450
let backgroundSaveTimer: ReturnType<typeof setTimeout> | null = null
let backgroundSavePending = false

const lineWidthOptions = [
  { label: 'Thin', value: 'thin' },
  { label: 'Light', value: 'light' },
  { label: 'Normal', value: 'normal' },
  { label: 'Medium', value: 'medium' },
  { label: 'Bold', value: 'bold' },
  { label: 'Extra Bold', value: 'extraBold' }
]

function showSavedToast() {
  toast.add({ title: 'Setting saved', icon: 'i-lucide-check', color: 'success' })
}

function toggleSetting(toggleFn: () => void) {
  toggleFn()
  showSavedToast()
}

function onColorChange(color: string) {
  editorUiStore.setBackgroundColor(color, { persist: false })
  queueBackgroundSave()
}

function onOpacityChange(opacity: number) {
  editorUiStore.setBackgroundOpacity(opacity, { persist: false })
  queueBackgroundSave()
}

function onLineWidthChange(value: string) {
  const presets: LineWidthPreset[] = ['thin', 'light', 'normal', 'medium', 'bold', 'extraBold']
  if (!presets.includes(value as LineWidthPreset)) return
  editorUiStore.setDefaultLineWidth(value as LineWidthPreset)
  showSavedToast()
}

function clearBackgroundSaveTimer() {
  if (!backgroundSaveTimer) return
  clearTimeout(backgroundSaveTimer)
  backgroundSaveTimer = null
}

async function persistBackgroundSettings() {
  if (!backgroundSavePending) return
  backgroundSavePending = false
  const saved = await editorUiStore.saveBackgroundAppearance()
  if (saved) showSavedToast()
}

function queueBackgroundSave() {
  backgroundSavePending = true
  clearBackgroundSaveTimer()
  backgroundSaveTimer = setTimeout(() => {
    backgroundSaveTimer = null
    void persistBackgroundSettings()
  }, BACKGROUND_SAVE_DEBOUNCE_MS)
}

watch(colorPickerOpen, (isOpen, wasOpen) => {
  if (!wasOpen || isOpen) return
  clearBackgroundSaveTimer()
  void persistBackgroundSettings()
})

onBeforeUnmount(() => {
  clearBackgroundSaveTimer()
  void persistBackgroundSettings()
})
</script>

<template>
  <div class="p-3 space-y-3">
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm">Comments Overlay</span>
        <USwitch
          :model-value="editorUiStore.commentsOverlay.visible"
          @update:model-value="toggleSetting(() => editorUiStore.toggleCommentsOverlay())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Image Bounds</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.constrainToImage"
          @update:model-value="toggleSetting(() => editorUiStore.toggleConstrainToImage())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Parent Bounds</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.constrainToParent"
          @update:model-value="toggleSetting(() => editorUiStore.toggleConstrainToParent())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Auto-Select</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.autoSelect"
          @update:model-value="toggleSetting(() => editorUiStore.toggleAutoSelect())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Fill Regions & Textlines</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.showPolygonLabelFill"
          @update:model-value="toggleSetting(() => editorUiStore.togglePolygonLabelFill())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Prevent Overlap</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.preventOverlapOnCreate"
          @update:model-value="toggleSetting(() => editorUiStore.togglePreventOverlapOnCreate())"
        />
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm">Move with Children</span>
        <USwitch
          :model-value="editorUiStore.globalSettings.moveWithChildren"
          @update:model-value="toggleSetting(() => editorUiStore.toggleMoveWithChildren())"
        />
      </div>
    </div>

    <USeparator />

    <div class="flex items-center justify-between">
      <span class="text-sm">Line Width</span>
      <USelect
        :model-value="editorUiStore.globalSettings.defaultLineWidth"
        :items="lineWidthOptions"
        class="w-32"
        @update:model-value="onLineWidthChange"
      />
    </div>

    <div class="flex items-center justify-between">
      <span class="text-sm">Background Color</span>
      <UPopover v-model:open="colorPickerOpen">
        <button
          class="w-7 h-7 rounded-sm border border-default cursor-pointer checkerboard-bg"
          title="Pick background color"
        >
          <div
            class="w-full h-full rounded-sm"
            :style="{ backgroundColor: editorUiStore.backgroundColor, opacity: editorUiStore.backgroundOpacity }"
          />
        </button>
        <template #content>
          <EditorBackgroundColorPicker
            :model-value="editorUiStore.backgroundColor"
            :opacity="editorUiStore.backgroundOpacity"
            @update:model-value="onColorChange"
            @update:opacity="onOpacityChange"
          />
        </template>
      </UPopover>
    </div>
  </div>
</template>

<style scoped>
.checkerboard-bg {
  background-image: linear-gradient(45deg, #ccc 25%, transparent 25%),
    linear-gradient(-45deg, #ccc 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #ccc 75%),
    linear-gradient(-45deg, transparent 75%, #ccc 75%);
  background-size: 8px 8px;
  background-position: 0 0, 0 4px, 4px -4px, -4px 0px;
}
</style>
