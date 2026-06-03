<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { useFloatingAnchorPosition } from '@/composables/editor/use-floating-anchor-position'
import { EDITOR_WORKSPACE_FLOATING_ANCHOR_ID } from '@/session/editor/editor-session'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import type { FloatingControlOffset } from '@/utils/editor/floating-anchor-position'

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
const floatingOffset = ref<{ dx: number, dy: number } | null>(null)
const floatingAnchorId = computed(() => EDITOR_WORKSPACE_FLOATING_ANCHOR_ID)
const isFloatingCollapsed = computed(() => editorUiStore.rightCollapsed && props.useFloatingCollapsed)

const DEFAULT_FLOATING_SIDEBAR_TOP = 120
const DEFAULT_FLOATING_RIGHT_SIDEBAR_GAP = 24
const DEFAULT_FLOATING_RIGHT_SIDEBAR_RIGHT = 24
const {
  style: floatingSidebarStyle,
  isDragging: isDraggingSidebar,
  startDrag: startSidebarDrag
} = useFloatingAnchorPosition({
  enabled: isFloatingCollapsed,
  anchorId: floatingAnchorId,
  shellRef: sidebarShellRef,
  placement: 'right-sidebar',
  fallbackSize: { width: 48, height: 320 },
  gap: DEFAULT_FLOATING_RIGHT_SIDEBAR_GAP,
  sidebarTop: DEFAULT_FLOATING_SIDEBAR_TOP,
  viewportMargin: { right: DEFAULT_FLOATING_RIGHT_SIDEBAR_RIGHT },
  getOffset: () => floatingOffset.value,
  setOffset: (offset: FloatingControlOffset | null) => {
    floatingOffset.value = offset
  }
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
