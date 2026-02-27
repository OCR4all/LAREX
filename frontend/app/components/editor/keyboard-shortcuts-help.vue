<script setup lang="ts">
import { SHORTCUT_DEFINITIONS } from '@/composables/editor/use-keyboard-shortcuts'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const isOpen = computed({
  get: () => props.open,
  set: value => emit('update:open', value)
})

const shortcutGroups = computed(() => [
  {
    title: 'Undo & Redo',
    shortcuts: [
      SHORTCUT_DEFINITIONS.undo,
      SHORTCUT_DEFINITIONS.redo
    ]
  },
  {
    title: 'Tools',
    shortcuts: [
      SHORTCUT_DEFINITIONS.selectMode,
      SHORTCUT_DEFINITIONS.moveMode,
      SHORTCUT_DEFINITIONS.regionPolygon,
      SHORTCUT_DEFINITIONS.regionRectangle,
      SHORTCUT_DEFINITIONS.textlinePolygon,
      SHORTCUT_DEFINITIONS.textlineRectangle,
      SHORTCUT_DEFINITIONS.baseline
    ]
  },
  {
    title: 'Cut Tools',
    shortcuts: [
      SHORTCUT_DEFINITIONS.cutLine,
      SHORTCUT_DEFINITIONS.cutPolygon,
      SHORTCUT_DEFINITIONS.cutRectangle
    ]
  },
  {
    title: 'View Modes',
    shortcuts: [
      SHORTCUT_DEFINITIONS.defaultView,
      SHORTCUT_DEFINITIONS.textlineView,
      SHORTCUT_DEFINITIONS.baselineView
    ]
  },
  {
    title: 'Editor Mode',
    shortcuts: [
      SHORTCUT_DEFINITIONS.layoutMode,
      SHORTCUT_DEFINITIONS.textMode,
      SHORTCUT_DEFINITIONS.toggleVirtualKeyboard
    ]
  },
  {
    title: 'Selection',
    shortcuts: [
      SHORTCUT_DEFINITIONS.selectAll,
      SHORTCUT_DEFINITIONS.clearSelection,
      SHORTCUT_DEFINITIONS.delete,
      SHORTCUT_DEFINITIONS.merge
    ]
  },
  {
    title: 'Navigation',
    shortcuts: [
      SHORTCUT_DEFINITIONS.prevElement,
      SHORTCUT_DEFINITIONS.nextElement,
      SHORTCUT_DEFINITIONS.prevImage,
      SHORTCUT_DEFINITIONS.nextImage
    ]
  },
  {
    title: 'View Controls',
    shortcuts: [
      SHORTCUT_DEFINITIONS.zoomIn,
      SHORTCUT_DEFINITIONS.zoomOut,
      SHORTCUT_DEFINITIONS.fitToContent,
      SHORTCUT_DEFINITIONS.centerOnSelection
    ]
  },
  {
    title: 'Panels',
    shortcuts: [
      SHORTCUT_DEFINITIONS.toggleLeftSidebar,
      SHORTCUT_DEFINITIONS.toggleRightSidebar,
      SHORTCUT_DEFINITIONS.closeActiveTab
    ]
  },
  {
    title: 'Actions',
    shortcuts: [
      SHORTCUT_DEFINITIONS.save
    ]
  }
])

const isMac = computed(() => {
  if (typeof navigator !== 'undefined') {
    return navigator.platform?.toLowerCase().includes('mac')
      || navigator.userAgent?.toLowerCase().includes('mac')
  }
  return false
})

function formatKey(key: string): string {
  if (isMac.value) {
    return key
  }
  return key
    .replace(/⌘/g, 'Ctrl')
    .replace(/⇧/g, 'Shift')
    .replace(/⌥/g, 'Alt')
}
</script>

<template>
  <UModal v-model:open="isOpen">
    <template #content>
      <UCard>
        <template #header>
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-semibold">
              Keyboard Shortcuts
            </h2>
            <UButton
              icon="i-lucide-x"
              color="neutral"
              variant="ghost"
              size="sm"
              aria-label="Close"
              @click="isOpen = false"
            />
          </div>
        </template>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 max-h-[60vh] overflow-y-auto p-1">
          <div
            v-for="group in shortcutGroups"
            :key="group.title"
            class="space-y-2"
          >
            <h3 class="text-sm font-medium text-muted-foreground">
              {{ group.title }}
            </h3>
            <div class="space-y-1">
              <div
                v-for="shortcut in group.shortcuts"
                :key="shortcut.key"
                class="flex items-center justify-between py-1.5 px-2 rounded-sm hover:bg-muted/50"
              >
                <span class="text-sm">{{ shortcut.description }}</span>
                <kbd class="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-mono bg-muted rounded-sm border border-border">
                  {{ formatKey(shortcut.key) }}
                </kbd>
              </div>
            </div>
          </div>
        </div>

        <template #footer>
          <div class="flex items-center justify-between text-xs text-muted-foreground">
            <span>
              Press <kbd class="px-1.5 py-0.5 bg-muted rounded-sm border border-border font-mono">?</kbd> to toggle this dialog
            </span>
            <span v-if="!isMac">
              ⌘ = Ctrl on Windows/Linux
            </span>
          </div>
        </template>
      </UCard>
    </template>
  </UModal>
</template>
