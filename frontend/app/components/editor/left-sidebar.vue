<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { LabelDefinition as ApiLabelDefinition } from '@/types/label-set'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'

const props = defineProps<{
  leftRailWidthPx: number
  logoMenuItems: DropdownMenuItem[][]
  currentProjectId: string | null
  pageNameFilter: string
  filterPopoverOpen: boolean
  availableLabels: ApiLabelDefinition[]
  availableTags: Array<{ label: string, value: string, count: number }>
  openSubtaskPageIds: Set<string>
  hasAdvancedFilters: boolean
  isFiltering: boolean
  totalFilteredPagesAcrossProjects: number
  globalVariantItems: Array<{ label: string, value: string }>
}>()

const emit = defineEmits<{
  'update:pageNameFilter': [value: string]
  'update:filterPopoverOpen': [value: boolean]
  'open-command-center': []
  'confirm-unload-active-project': [projectId: string]
}>()

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()

const pageNameFilterModel = computed({
  get: () => props.pageNameFilter,
  set: (value: string) => emit('update:pageNameFilter', value)
})

const filterPopoverOpenModel = computed({
  get: () => props.filterPopoverOpen,
  set: (value: boolean) => emit('update:filterPopoverOpen', value)
})
</script>

<template>
  <aside
    data-tour="editor-left-sidebar"
    class="h-full flex flex-col border-r border-default bg-elevated/25"
    :style="{ width: (editorUiStore.leftCollapsed ? leftRailWidthPx : editorUiStore.leftWidthPx) + 'px' }"
  >
    <div class="shrink-0 px-0 py-2 border-b border-default">
      <div class="flex w-full h-full items-center" :class="[editorUiStore.leftCollapsed ? 'px-2 flex-col justify-center gap-1' : 'px-4 justify-between']">
        <UDropdownMenu :items="logoMenuItems">
          <div class="flex items-center gap-x-0.5 p-1 hover:bg-accented rounded-sm cursor-pointer">
            <UiLogo size="32" class="self-center" />
            <Icon v-if="!editorUiStore.leftCollapsed" name="i-lucide-chevron-down" class="self-center" />
          </div>
        </UDropdownMenu>
        <div class="flex items-center gap-1">
          <UButton
            type="button"
            variant="ghost"
            color="neutral"
            :icon="editorUiStore.leftCollapsed ? 'i-lucide-panel-left-open' : 'i-lucide-panel-left-close'"
            :aria-label="editorUiStore.leftCollapsed ? 'Expand sidebar' : 'Collapse sidebar'"
            @click="editorUiStore.toggleLeftCollapsed"
          />
        </div>
      </div>
    </div>

    <div class="flex-1 min-h-0 flex flex-col px-2 pt-2 gap-y-2">
      <div v-if="!editorUiStore.leftCollapsed" class="shrink-0 flex flex-col gap-2">
        <UDashboardSearchButton
          class="bg-transparent ring-default"
          :kbds="['meta', 'k']"
          @click="emit('open-command-center')"
        />

        <div class="border-t border-default" />

        <div class="flex items-center gap-2">
          <UInput
            v-model="pageNameFilterModel"
            size="sm"
            placeholder="Filter pages…"
            aria-label="Filter pages by name"
            class="flex-1"
          />
          <EditorPageFilterPopover
            v-if="currentProjectId"
            v-model:open="filterPopoverOpenModel"
            :project-id="currentProjectId"
            :available-labels="availableLabels"
            :available-tags="availableTags"
            :open-subtask-page-ids="openSubtaskPageIds"
          />
        </div>

        <div v-if="hasAdvancedFilters" class="text-xs text-muted flex items-center gap-1">
          <UIcon v-if="isFiltering" name="i-lucide-loader-2" class="animate-spin" />
          <span v-else>{{ totalFilteredPagesAcrossProjects }} pages match filters</span>
        </div>

        <div class="flex items-center gap-2">
          <span class="text-xs font-medium text-muted whitespace-nowrap">Variant</span>
          <USelect
            :model-value="editorStore.preferredImageVariantKey ?? undefined"
            :items="globalVariantItems"
            placeholder="Default"
            size="sm"
            class="w-full"
            @update:model-value="(key) => editorStore.setPreferredImageVariantKey(key)"
          />
        </div>
      </div>

      <div v-else class="shrink-0 flex flex-col items-center gap-2">
        <UButton
          variant="ghost"
          color="neutral"
          icon="i-lucide-search"
          size="sm"
          aria-label="Open command center"
          @click="emit('open-command-center')"
        />
        <UTooltip v-if="currentProjectId" text="Unload active project" :content="{ side: 'right' }">
          <UButton
            variant="ghost"
            color="neutral"
            icon="i-lucide-folder-x"
            size="sm"
            aria-label="Unload active project"
            @click="currentProjectId && emit('confirm-unload-active-project', currentProjectId)"
          />
        </UTooltip>
        <EditorPageFilterPopover
          v-if="currentProjectId"
          v-model:open="filterPopoverOpenModel"
          v-model:page-name-filter="pageNameFilterModel"
          :project-id="currentProjectId"
          :available-labels="availableLabels"
          :available-tags="availableTags"
          :open-subtask-page-ids="openSubtaskPageIds"
          popover-side="right"
        />
        <UPopover :content="{ side: 'right', align: 'start' }">
          <UTooltip text="Image variant" :content="{ side: 'right' }">
            <UButton
              variant="ghost"
              color="neutral"
              icon="i-lucide-image"
              size="sm"
              aria-label="Image variant"
            />
          </UTooltip>
          <template #content>
            <div class="p-3 flex flex-col gap-1" style="width: 200px">
              <span class="text-xs font-medium text-muted">Variant</span>
              <USelect
                :model-value="editorStore.preferredImageVariantKey ?? undefined"
                :items="globalVariantItems"
                placeholder="Default"
                size="sm"
                class="w-full"
                @update:model-value="(key) => editorStore.setPreferredImageVariantKey(key)"
              />
            </div>
          </template>
        </UPopover>
      </div>

      <div class="min-h-0 flex-1 overflow-auto editor-sidebar-image-scroll">
        <slot />
      </div>
    </div>

    <div class="shrink-0 border-t border-default p-2">
      <UserMenu :collapsed="editorUiStore.leftCollapsed" />
    </div>
  </aside>
</template>
