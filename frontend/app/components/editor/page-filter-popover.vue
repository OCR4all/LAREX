<script setup lang="ts">
import type { LabelDefinition } from '@/types/label-set'
import {
  PAGE_CONFIDENCE_ELEMENT_TYPE_OPTIONS,
  isConfidenceFilterActive
} from '@/composables/use-page-filter'
import { createCanonicalLabelFilterOptions } from '@/utils/editor/page-filter-tokens'

const props = defineProps<{
  projectId: string
  /** Available labels from the project's label set */
  availableLabels?: LabelDefinition[]
  /** Available tags from all pages */
  availableTags?: { label: string, value: string, count?: number }[]
  /** Page IDs with open subtasks */
  openSubtaskPageIds?: Set<string>
  /** Optional page name filter — when provided, a name filter input is shown inside the popover */
  pageNameFilter?: string
  /** Side for the popover to open on */
  popoverSide?: 'bottom' | 'right' | 'left' | 'top'
  /** Optional externally controlled open state */
  open?: boolean
}>()

const emit = defineEmits<{
  'filter-changed': [pageIds: Set<string>]
  'rebuild-index': []
  'update:pageNameFilter': [value: string]
  'update:open': [value: boolean]
}>()

const projectIdRef = computed(() => props.projectId)
const {
  labelIds,
  textContent,
  tags,
  filterOperator,
  confidenceRange,
  confidenceElementTypes,
  hasComments,
  onlyWithOpenSubtasks,
  hasActiveFilters,
  isFiltering,
  filterError,
  filteredCount,
  clearFilters,
  clearLabelFilter,
  clearTextContentFilter,
  clearTagFilter,
  clearConfidenceFilter,
  applyFilters,
  fetchIndexStats,
  rebuildIndex
} = usePageFilter(projectIdRef)

const localOpen = ref(false)
const isOpen = computed({
  get: () => props.open ?? localOpen.value,
  set: (next: boolean) => {
    if (props.open === undefined) {
      localOpen.value = next
    }
    emit('update:open', next)
  }
})

const indexStats = ref<{
  totalPages: number
  indexedTextContentPages: number
  indexedLabelPages: number
  pagesNeedingIndex: number
} | null>(null)

const isLoadingStats = ref(false)

watch(isOpen, async (open) => {
  if (open && !indexStats.value) {
    isLoadingStats.value = true
    indexStats.value = await fetchIndexStats()
    isLoadingStats.value = false
  }
})

const labelItems = computed(() => {
  if (!props.availableLabels) return []
  return createCanonicalLabelFilterOptions(props.availableLabels)
})

const operatorOptions = [
  { label: 'Match all (AND)', value: 'and' as const },
  { label: 'Match any (OR)', value: 'or' as const }
]

const localPageNameFilter = computed({
  get: () => props.pageNameFilter ?? '',
  set: (val: string) => emit('update:pageNameFilter', val)
})

const hasPageNameFilter = computed(() => props.pageNameFilter !== undefined)
const confidenceElementTypeOptions = PAGE_CONFIDENCE_ELEMENT_TYPE_OPTIONS.map(option => ({ ...option }))
const confidenceFilterActive = computed(() => isConfidenceFilterActive({
  confidenceRange: confidenceRange.value,
  confidenceElementTypes: confidenceElementTypes.value
}))
const confidenceRangeModel = computed<[number, number]>({
  get: () => confidenceRange.value,
  set: (value) => {
    confidenceRange.value = [value[0] ?? 0, value[1] ?? 1]
  }
})

const activeFilterCount = computed(() => {
  let count = 0
  if (localPageNameFilter.value.trim()) count++
  if (labelIds.value.length > 0) count++
  if (textContent.value.trim()) count++
  if (tags.value.length > 0) count++
  if (confidenceFilterActive.value) count++
  if (hasComments.value) count++
  if (onlyWithOpenSubtasks.value) count++
  return count
})

const openSubtaskPageCount = computed(() => {
  return props.openSubtaskPageIds?.size ?? 0
})

async function handleApply() {
  const result = await applyFilters()
  emit('filter-changed', result.pageIds)
}

const isRebuilding = ref(false)
async function handleRebuildIndex() {
  isRebuilding.value = true
  const success = await rebuildIndex()
  isRebuilding.value = false
  if (success) {
    emit('rebuild-index')
    indexStats.value = await fetchIndexStats()
  }
}

function handleClearAll() {
  clearFilters()
  emit('filter-changed', new Set())
}
</script>

<template>
  <UPopover v-model:open="isOpen" :content="{ side: popoverSide ?? 'bottom', align: 'start', sideOffset: 8 }">
    <UButton
      data-tour="editor-page-filter-button"
      :icon="hasActiveFilters ? 'i-lucide-filter-x' : 'i-lucide-filter'"
      :color="hasActiveFilters ? 'primary' : 'neutral'"
      variant="ghost"
      size="sm"
      :aria-label="hasActiveFilters ? `Filter active (${activeFilterCount})` : 'Filter pages'"
    >
      <template v-if="hasActiveFilters" #trailing>
        <UBadge size="xs" color="primary" variant="solid">
          {{ activeFilterCount }}
        </UBadge>
      </template>
    </UButton>

    <template #content>
      <div class="w-80 p-4 space-y-4">
        <template v-if="hasPageNameFilter">
          <UInput
            :model-value="localPageNameFilter"
            size="sm"
            placeholder="Filter pages by name…"
            icon="i-lucide-search"
            aria-label="Filter pages by name"
            @update:model-value="localPageNameFilter = $event"
          />
          <USeparator />
        </template>

        <div class="flex items-center justify-between">
          <h3 class="font-semibold text-sm">
            Page Filters
          </h3>
          <UButton
            v-if="hasActiveFilters"
            size="xs"
            variant="ghost"
            color="neutral"
            @click="handleClearAll"
          >
            Clear all
          </UButton>
        </div>

        <div class="flex items-center justify-between p-2 bg-muted/30 rounded-sm">
          <span class="text-xs font-medium text-muted">Combine filters with:</span>
          <UFieldGroup size="xs">
            <UButton
              v-for="opt in operatorOptions"
              :key="opt.value"
              :color="filterOperator === opt.value ? 'primary' : 'neutral'"
              :variant="filterOperator === opt.value ? 'solid' : 'outline'"
              @click="filterOperator = opt.value"
            >
              {{ opt.value.toUpperCase() }}
            </UButton>
          </UFieldGroup>
        </div>

        <div v-if="isFiltering" class="absolute inset-0 bg-default/80 flex items-center justify-center z-10 rounded-sm">
          <div class="flex items-center gap-2 text-sm text-muted">
            <UIcon name="i-lucide-loader-2" class="animate-spin" />
            <span>Filtering...</span>
          </div>
        </div>

        <UAlert
          v-if="filterError"
          color="error"
          icon="i-lucide-alert-circle"
          :description="filterError"
        />

        <div data-tour="editor-page-filter-section-labels" class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-medium text-muted">Labels</label>
            <UButton
              v-if="labelIds.length > 0"
              size="xs"
              variant="link"
              color="neutral"
              class="h-auto p-0"
              @click="clearLabelFilter"
            >
              Clear
            </UButton>
          </div>
          <USelectMenu
            v-model="labelIds"
            :items="labelItems"
            multiple
            placeholder="Select labels..."
            size="sm"
            class="w-full"
            value-key="value"
          >
            <template #item="{ item }">
              <div class="flex items-center gap-2">
                <span
                  class="w-3 h-3 rounded-sm shrink-0"
                  :style="{ backgroundColor: item.color }"
                />
                <span class="truncate">{{ item.label }}</span>
                <UBadge
                  size="xs"
                  color="neutral"
                  variant="subtle"
                  class="ml-auto"
                >
                  {{ item.scope }}
                </UBadge>
              </div>
            </template>
          </USelectMenu>
        </div>

        <USeparator />

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-medium text-muted">Text Content</label>
            <UButton
              v-if="textContent.trim()"
              size="xs"
              variant="link"
              color="neutral"
              class="h-auto p-0"
              @click="clearTextContentFilter"
            >
              Clear
            </UButton>
          </div>
          <UInput
            v-model="textContent"
            placeholder="Search in text content..."
            size="sm"
            icon="i-lucide-search"
          />
          <p class="text-xs text-muted">
            Searches in all PAGE XML TextEquiv/Unicode content
          </p>
        </div>

        <USeparator />

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-medium text-muted">Tags</label>
            <UButton
              v-if="tags.length > 0"
              size="xs"
              variant="link"
              color="neutral"
              class="h-auto p-0"
              @click="clearTagFilter"
            >
              Clear
            </UButton>
          </div>
          <USelectMenu
            v-model="tags"
            :items="availableTags ?? []"
            multiple
            placeholder="Select tags..."
            size="sm"
            class="w-full"
            value-key="value"
          >
            <template #item="{ item }">
              <div class="flex items-center justify-between w-full">
                <span>{{ item.label }}</span>
                <UBadge
                  v-if="item.count"
                  size="xs"
                  color="neutral"
                  variant="subtle"
                >
                  {{ item.count }}
                </UBadge>
              </div>
            </template>
          </USelectMenu>
        </div>

        <USeparator />

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-medium text-muted">Confidence</label>
            <UButton
              v-if="confidenceFilterActive"
              size="xs"
              variant="link"
              color="neutral"
              class="h-auto p-0"
              @click="clearConfidenceFilter"
            >
              Clear
            </UButton>
          </div>
          <div class="space-y-2">
            <div class="flex items-center justify-between text-xs text-muted">
              <span>Range</span>
              <span class="font-medium text-default">
                {{ confidenceRangeModel[0].toFixed(2) }}-{{ confidenceRangeModel[1].toFixed(2) }}
              </span>
            </div>
            <USlider
              v-model="confidenceRangeModel"
              :min="0"
              :max="1"
              :step="0.01"
            />
          </div>
          <USelectMenu
            v-model="confidenceElementTypes"
            :items="confidenceElementTypeOptions"
            multiple
            placeholder="All PAGE @conf element types"
            size="sm"
            class="w-full"
            value-key="value"
          />
          <p class="text-xs text-muted">
            If no types are selected, all indexed confidence element types are included.
          </p>
        </div>

        <USeparator />

        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <UCheckbox v-model="hasComments" />
            <label class="text-xs text-default cursor-pointer" @click="hasComments = !hasComments">
              Only pages with comments
            </label>
          </div>
        </div>

        <USeparator />

        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <UCheckbox
              v-model="onlyWithOpenSubtasks"
              :disabled="openSubtaskPageCount === 0"
            />
            <label class="text-xs text-default cursor-pointer" @click="onlyWithOpenSubtasks = !onlyWithOpenSubtasks">
              Only pages with open tasks
            </label>
          </div>
          <UBadge
            v-if="openSubtaskPageCount > 0"
            size="xs"
            color="warning"
            variant="subtle"
          >
            {{ openSubtaskPageCount }}
          </UBadge>
        </div>

        <USeparator />

        <div class="space-y-2">
          <div v-if="hasActiveFilters" class="flex items-center justify-between text-sm">
            <span class="text-muted">Matching pages:</span>
            <span class="font-medium">{{ filteredCount }}</span>
          </div>

          <UButton
            block
            :loading="isFiltering"
            :disabled="!hasActiveFilters"
            @click="handleApply"
          >
            Apply Filters
          </UButton>
        </div>

        <UAccordion
          :items="[{ label: 'Index Status', icon: 'i-lucide-database', slot: 'stats' }]"
          size="sm"
        >
          <template #stats>
            <div class="space-y-2 py-2">
              <div v-if="isLoadingStats" class="flex items-center gap-2 text-sm text-muted">
                <UIcon name="i-lucide-loader-2" class="animate-spin" />
                <span>Loading stats...</span>
              </div>
              <template v-else-if="indexStats">
                <div class="grid grid-cols-2 gap-2 text-xs">
                  <div class="text-muted">
                    Total pages:
                  </div>
                  <div class="font-medium">
                    {{ indexStats.totalPages }}
                  </div>
                  <div class="text-muted">
                    Indexed (text):
                  </div>
                  <div class="font-medium">
                    {{ indexStats.indexedTextContentPages }}
                  </div>
                  <div class="text-muted">
                    Indexed (labels):
                  </div>
                  <div class="font-medium">
                    {{ indexStats.indexedLabelPages }}
                  </div>
                  <div class="text-muted">
                    Needs indexing:
                  </div>
                  <div class="font-medium" :class="{ 'text-warning': indexStats.pagesNeedingIndex > 0 }">
                    {{ indexStats.pagesNeedingIndex }}
                  </div>
                </div>
                <UButton
                  v-if="indexStats.pagesNeedingIndex > 0"
                  size="xs"
                  variant="outline"
                  color="warning"
                  block
                  :loading="isRebuilding"
                  @click="handleRebuildIndex"
                >
                  <UIcon name="i-lucide-refresh-cw" class="mr-1" />
                  Rebuild Index
                </UButton>
              </template>
              <div v-else class="text-xs text-muted">
                Failed to load index stats
              </div>
            </div>
          </template>
        </UAccordion>
      </div>
    </template>
  </UPopover>
</template>
