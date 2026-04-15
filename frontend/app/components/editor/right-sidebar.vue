<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { getTooltipProps } from '@/composables/editor/use-keyboard-shortcuts'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'

defineProps<{
  rightRailWidthPx: number
  isSavingActiveCanvas: boolean
  canEditActiveCanvas: boolean
  canOpenActiveCanvasXmlEditor: boolean
  canCompleteActivePageSubtasks: boolean
  isCompletingOpenSubtasks: boolean
  isActivePageLocked: boolean
  actionItems: DropdownMenuItem[][]
}>()

const emit = defineEmits<{
  'save': []
  'open-history': []
  'open-xml-editor': []
  'save-and-complete': []
}>()

const editorUiStore = useEditorUiStore()
</script>

<template>
  <aside
    data-tour="editor-right-sidebar"
    class="h-full flex flex-col border-l border-default bg-elevated/25 gap-y-2"
    :class="[editorUiStore.rightCollapsed ? 'py-2' : 'p-2']"
    :style="{ width: (editorUiStore.rightCollapsed ? rightRailWidthPx : editorUiStore.rightWidthPx) + 'px' }"
  >
    <div class="shrink-0 flex" :class="[editorUiStore.rightCollapsed ? 'justify-center flex-col items-center gap-1' : 'justify-between']">
      <UButton
        type="button"
        variant="ghost"
        color="neutral"
        :icon="editorUiStore.rightCollapsed ? 'i-lucide-panel-right-open' : 'i-lucide-panel-right-close'"
        :aria-label="editorUiStore.rightCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        @click="editorUiStore.toggleRightCollapsed"
      />

      <template v-if="editorUiStore.rightCollapsed">
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
          />
        </UDropdownMenu>
      </template>

      <UFieldGroup v-else>
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
  </aside>
</template>
