<script setup lang="ts">
import { PAGE_WORKFLOW_STATES, PAGE_WORKFLOW_STATE_META } from '@/utils/page-workflow-state'
import type { LabelDefinition } from '@/types/label-set'
import type { PageWorkflowState } from '@/types/project-page'
import { createCanonicalLabelFilterOptions } from '@/utils/editor/page-filter-tokens'
import type { PageFilterType, SingletonPageFilterType, XmlAttributeFilterRow, XmlAttributeOperator, XmlAttributeWithCount } from '@/composables/use-page-filter'

const props = defineProps<{
  projectId: string
  availableLabels?: LabelDefinition[]
  availableTags?: { label: string, value: string, count?: number }[]
  openSubtaskPageIds?: Set<string>
  popoverSide?: 'bottom' | 'right' | 'left' | 'top'
}>()

const emit = defineEmits<{
  'rebuild-index': []
}>()

const isOpen = defineModel<boolean>('open', { default: false })

const projectIdRef = computed(() => props.projectId)
const {
  labelIds,
  textContent,
  tags,
  filterOperator,
  confidenceRange,
  confidenceElementTypes,
  commentText,
  onlyWithOpenSubtasks,
  changesRequested,
  workflowStates,
  annotationPresence,
  xmlAttributeFilters,
  visibleFilters,
  negatedFilters,
  activeFilterCount,
  hasActiveFilters,
  isFiltering,
  filterError,
  filteredCount,
  filtersApplied,
  addFilter,
  removeFilter,
  clearFilters,
  fetchIndexStats,
  fetchAvailableXmlAttributes,
  rebuildIndex
} = usePageFilter(projectIdRef)

const editorStore = useEditorStore()
const addMenuOpen = ref(false)
const addSearch = ref('')
const isLoadingStats = ref(false)
const isRebuilding = ref(false)
const indexStats = ref<IndexStats | null>(null)
const availableXmlAttributes = ref<XmlAttributeWithCount[]>([])

const filterDefinitions: Array<{ type: PageFilterType, label: string, description: string, icon: string }> = [
  { type: 'workflowStates', label: 'Page state', description: 'Open, in review, or done', icon: 'i-lucide-list-checks' },
  { type: 'annotationPresence', label: 'Annotation', description: 'With or without XML', icon: 'i-lucide-file-code-2' },
  { type: 'labels', label: 'Labels', description: 'PAGE label definitions', icon: 'i-lucide-tags' },
  { type: 'textContent', label: 'Text content', description: 'TextEquiv/Unicode contains', icon: 'i-lucide-text-search' },
  { type: 'tags', label: 'Tags', description: 'Page tags', icon: 'i-lucide-tag' },
  { type: 'confidence', label: 'Confidence', description: 'PAGE @conf range', icon: 'i-lucide-gauge' },
  { type: 'comments', label: 'Comments', description: 'Pages containing comments', icon: 'i-lucide-message-square' },
  { type: 'openSubtasks', label: 'Open tasks', description: 'Your incomplete Tasks', icon: 'i-lucide-square-check-big' },
  { type: 'changesRequested', label: 'Changes requested', description: 'Pages sent back by a reviewer', icon: 'i-lucide-message-square-warning' },
  { type: 'xmlAttribute', label: 'PAGE XML attribute', description: 'Source attribute presence or value', icon: 'i-lucide-brackets' }
]

const filteredAddDefinitions = computed(() => {
  const query = addSearch.value.trim().toLowerCase()
  return filterDefinitions.filter(definition => !query || `${definition.label} ${definition.description}`.toLowerCase().includes(query))
})
const visibleFilterSet = computed(() => new Set(visibleFilters.value))
const filterModes = [
  { label: 'Include', negated: false },
  { label: 'Exclude', negated: true }
]
const labelItems = computed(() => createCanonicalLabelFilterOptions(props.availableLabels ?? []))
const confidenceElementTypeOptions = PAGE_CONFIDENCE_ELEMENT_TYPE_OPTIONS.map(option => ({ ...option }))
const workflowStateOptions: Array<{ label: string, value: PageWorkflowState }> = PAGE_WORKFLOW_STATES.map(value => ({
  label: PAGE_WORKFLOW_STATE_META[value].label,
  value
}))
const annotationOptions = [
  { label: 'With annotation', value: 'with_xml' },
  { label: 'Without annotation', value: 'without_xml' }
]
const xmlOperatorOptions: Array<{ label: string, value: XmlAttributeOperator }> = [
  { label: 'Exists', value: 'exists' },
  { label: 'Does not exist', value: 'not_exists' },
  { label: 'Equals', value: 'equals' },
  { label: 'Does not equal', value: 'not_equals' },
  { label: 'Contains', value: 'contains' },
  { label: 'Does not contain', value: 'not_contains' }
]
const valueOperators = new Set(['equals', 'not_equals', 'contains', 'not_contains'])
const elementSuggestions = computed(() => [...new Set(availableXmlAttributes.value.map(item => item.elementName))].sort())
const attributeSuggestions = computed(() => [...new Set(availableXmlAttributes.value.map(item => item.attributeName))].sort())
function attributeSuggestionsFor(row: XmlAttributeFilterRow): string[] {
  const elementName = row.elementName.trim()
  if (!elementName) return attributeSuggestions.value
  return [...new Set(availableXmlAttributes.value
    .filter(item => item.elementName === elementName)
    .map(item => item.attributeName))].sort()
}
const matchingPageCount = computed(() => {
  if (!hasActiveFilters.value) return editorStore.getProjectPages(props.projectId).length
  return filtersApplied.value ? filteredCount.value : editorStore.getProjectPages(props.projectId).length
})
const confidenceRangeModel = computed<[number, number]>({
  get: () => confidenceRange.value,
  set: (value) => { confidenceRange.value = [value[0] ?? 0, value[1] ?? 1] }
})

watch(isOpen, async (open) => {
  if (!open) return
  if (!indexStats.value) {
    isLoadingStats.value = true
    indexStats.value = await fetchIndexStats()
    isLoadingStats.value = false
  }
  availableXmlAttributes.value = await fetchAvailableXmlAttributes()
})

function selectFilter(type: PageFilterType) {
  addFilter(type)
  addMenuOpen.value = false
  addSearch.value = ''
}

function setFilterMode(type: SingletonPageFilterType, negated: boolean) {
  if (negatedFilters.value.includes(type) === negated) return
  negatedFilters.value = negated
    ? [...negatedFilters.value, type]
    : negatedFilters.value.filter(value => value !== type)
}

function updateXmlRow(rowId: string, update: Partial<Omit<XmlAttributeFilterRow, 'id'>>) {
  xmlAttributeFilters.value = xmlAttributeFilters.value.map(row => row.id === rowId ? { ...row, ...update } : row)
}

async function handleRebuildIndex() {
  isRebuilding.value = true
  const success = await rebuildIndex()
  isRebuilding.value = false
  if (!success) return
  emit('rebuild-index')
  indexStats.value = await fetchIndexStats()
  availableXmlAttributes.value = await fetchAvailableXmlAttributes()
}

function titleFor(type: SingletonPageFilterType): string {
  return filterDefinitions.find(definition => definition.type === type)?.label ?? type
}
</script>

<template>
  <UPopover v-model:open="isOpen" :content="{ side: popoverSide ?? 'bottom', align: 'start', sideOffset: 8 }">
    <UButton
      data-tour="editor-page-filter-button"
      icon="i-lucide-funnel"
      :color="hasActiveFilters ? 'primary' : 'neutral'"
      variant="ghost"
      size="sm"
      :aria-label="hasActiveFilters ? `Page filters active (${activeFilterCount})` : 'Filter pages'"
    >
      <template v-if="hasActiveFilters" #trailing>
        <UBadge size="xs" color="primary" variant="solid">
          {{ activeFilterCount }}
        </UBadge>
      </template>
    </UButton>

    <template #content>
      <div class="flex max-h-[min(82vh,760px)] w-96 flex-col overflow-hidden">
        <div class="shrink-0 space-y-3 border-b border-default p-4">
          <div class="flex items-center justify-between gap-3">
            <div>
              <h3 class="text-sm font-semibold">
                Page filters
              </h3>
              <p class="text-xs text-muted">
                {{ matchingPageCount }} pages match in this project
              </p>
            </div>
            <UButton
              v-if="visibleFilters.length || xmlAttributeFilters.length"
              size="xs"
              variant="ghost"
              color="neutral"
              @click="clearFilters"
            >
              Clear all
            </UButton>
          </div>

          <UPopover v-model:open="addMenuOpen" :content="{ side: 'bottom', align: 'start', sideOffset: 6 }">
            <UButton
              data-tour="editor-page-filter-add"
              block
              icon="i-lucide-plus"
              variant="outline"
              color="neutral"
            >
              Add filter
            </UButton>
            <template #content>
              <div class="w-80 space-y-2 p-2">
                <UInput
                  v-model="addSearch"
                  autofocus
                  icon="i-lucide-search"
                  placeholder="Search filters…"
                  aria-label="Search filters"
                />
                <div class="max-h-72 space-y-1 overflow-y-auto">
                  <UButton
                    v-for="definition in filteredAddDefinitions"
                    :key="definition.type"
                    :icon="definition.icon"
                    :disabled="definition.type !== 'xmlAttribute' && visibleFilterSet.has(definition.type as SingletonPageFilterType)"
                    color="neutral"
                    variant="ghost"
                    class="h-auto w-full justify-start py-2 text-left"
                    @click="selectFilter(definition.type)"
                  >
                    <span class="min-w-0">
                      <span class="block text-sm">{{ definition.label }}</span>
                      <span class="block truncate text-xs font-normal text-muted">{{ definition.description }}</span>
                    </span>
                  </UButton>
                </div>
              </div>
            </template>
          </UPopover>

          <div v-if="activeFilterCount > 1" class="flex items-center justify-between rounded-md bg-muted/30 p-2">
            <span class="text-xs font-medium text-muted">Combine with</span>
            <UFieldGroup size="xs">
              <UButton :color="filterOperator === 'and' ? 'primary' : 'neutral'" :variant="filterOperator === 'and' ? 'solid' : 'outline'" @click="() => { filterOperator = 'and' }">
                AND
              </UButton>
              <UButton :color="filterOperator === 'or' ? 'primary' : 'neutral'" :variant="filterOperator === 'or' ? 'solid' : 'outline'" @click="() => { filterOperator = 'or' }">
                OR
              </UButton>
            </UFieldGroup>
          </div>

          <div v-if="isFiltering" class="flex items-center gap-2 text-xs text-muted" role="status">
            <UIcon name="i-lucide-loader-2" class="animate-spin" /> Updating results…
          </div>
          <UAlert
            v-if="filterError"
            color="warning"
            icon="i-lucide-triangle-alert"
            :description="filterError"
          />
        </div>

        <div class="min-h-0 flex-1 space-y-3 overflow-y-auto p-4">
          <div v-if="!visibleFilters.length && !xmlAttributeFilters.length" class="py-8 text-center text-sm text-muted">
            Add a filter to narrow the page list.
          </div>

          <section
            v-for="type in visibleFilters"
            :key="type"
            class="space-y-3 rounded-lg border border-default p-3"
            :data-tour="type === 'labels' ? 'editor-page-filter-section-labels' : undefined"
          >
            <div class="flex items-center justify-between gap-2">
              <h4 class="text-xs font-semibold">
                {{ titleFor(type) }}
              </h4>
              <div class="flex items-center gap-1">
                <UFieldGroup size="xs" role="group" :aria-label="`${titleFor(type)} filter mode`">
                  <UButton
                    v-for="mode in filterModes"
                    :key="mode.label"
                    :label="mode.label"
                    :color="negatedFilters.includes(type) === mode.negated ? 'primary' : 'neutral'"
                    :variant="negatedFilters.includes(type) === mode.negated ? 'solid' : 'outline'"
                    :aria-pressed="negatedFilters.includes(type) === mode.negated"
                    @click="setFilterMode(type, mode.negated)"
                  />
                </UFieldGroup>
                <UButton
                  icon="i-lucide-x"
                  size="xs"
                  variant="ghost"
                  color="neutral"
                  :aria-label="`Remove ${titleFor(type)} filter`"
                  @click="removeFilter(type)"
                />
              </div>
            </div>

            <USelectMenu
              v-if="type === 'workflowStates'"
              v-model="workflowStates"
              :items="workflowStateOptions"
              multiple
              value-key="value"
              placeholder="Select page states…"
              class="w-full"
            />
            <USelect
              v-else-if="type === 'annotationPresence'"
              :model-value="annotationPresence ?? undefined"
              :items="annotationOptions"
              value-key="value"
              placeholder="Choose annotation presence…"
              class="w-full"
              @update:model-value="annotationPresence = ($event === 'with_xml' || $event === 'without_xml') ? $event : null"
            />
            <USelectMenu
              v-else-if="type === 'labels'"
              v-model="labelIds"
              :items="labelItems"
              multiple
              value-key="value"
              placeholder="Select labels…"
              class="w-full"
            />
            <template v-else-if="type === 'textContent'">
              <UInput v-model="textContent" icon="i-lucide-search" placeholder="Search TextEquiv/Unicode…" />
              <p class="text-xs text-muted">
                Searches all indexed PAGE XML text content.
              </p>
            </template>
            <USelectMenu
              v-else-if="type === 'tags'"
              v-model="tags"
              :items="availableTags ?? []"
              multiple
              value-key="value"
              placeholder="Select tags…"
              class="w-full"
            />
            <template v-else-if="type === 'confidence'">
              <div class="flex justify-between text-xs text-muted">
                <span>Range</span><span>{{ confidenceRangeModel[0].toFixed(2) }}–{{ confidenceRangeModel[1].toFixed(2) }}</span>
              </div>
              <USlider
                v-model="confidenceRangeModel"
                :min="0"
                :max="1"
                :step="0.01"
              />
              <USelectMenu
                v-model="confidenceElementTypes"
                :items="confidenceElementTypeOptions"
                multiple
                value-key="value"
                placeholder="All @conf element types"
                class="w-full"
              />
            </template>
            <template v-else-if="type === 'comments'">
              <UInput
                v-model="commentText"
                icon="i-lucide-search"
                placeholder="Search comments…"
                aria-label="Search comments"
              />
              <p class="text-xs text-muted">
                Leave empty to match any metadata or PAGE XML comment.
              </p>
            </template>
            <div v-else-if="type === 'changesRequested'" class="flex items-center justify-between gap-2">
              <UCheckbox v-model="changesRequested" label="Only pages with requested changes" />
            </div>
            <div v-else-if="type === 'openSubtasks'" class="flex items-center justify-between gap-2">
              <UCheckbox v-model="onlyWithOpenSubtasks" label="Only pages with my open tasks" />
              <UBadge color="warning" variant="subtle" size="xs">
                {{ openSubtaskPageIds?.size ?? 0 }}
              </UBadge>
            </div>
          </section>

          <section v-for="(row, index) in xmlAttributeFilters" :key="row.id" class="space-y-3 rounded-lg border border-default p-3">
            <div class="flex items-center justify-between gap-2">
              <h4 class="text-xs font-semibold">
                PAGE XML attribute {{ index + 1 }}
              </h4>
              <div class="flex items-center gap-1">
                <UFieldGroup size="xs" role="group" :aria-label="`PAGE XML attribute ${index + 1} filter mode`">
                  <UButton
                    v-for="mode in filterModes"
                    :key="mode.label"
                    :label="mode.label"
                    :color="Boolean(row.negated) === mode.negated ? 'primary' : 'neutral'"
                    :variant="Boolean(row.negated) === mode.negated ? 'solid' : 'outline'"
                    :aria-pressed="Boolean(row.negated) === mode.negated"
                    @click="updateXmlRow(row.id, { negated: mode.negated })"
                  />
                </UFieldGroup>
                <UButton
                  icon="i-lucide-x"
                  size="xs"
                  variant="ghost"
                  color="neutral"
                  aria-label="Remove PAGE XML attribute filter"
                  @click="removeFilter('xmlAttribute', row.id)"
                />
              </div>
            </div>
            <div class="grid grid-cols-2 gap-2">
              <UInputMenu
                :model-value="row.elementName"
                :items="elementSuggestions"
                mode="autocomplete"
                open-on-focus
                placeholder="Any element"
                aria-label="PAGE XML element name"
                @update:model-value="updateXmlRow(row.id, { elementName: String($event) })"
              />
              <UInputMenu
                :model-value="row.attributeName"
                :items="attributeSuggestionsFor(row)"
                mode="autocomplete"
                open-on-focus
                placeholder="Attribute name"
                aria-label="PAGE XML attribute name"
                @update:model-value="updateXmlRow(row.id, { attributeName: String($event) })"
              />
            </div>
            <USelect
              :model-value="row.operator"
              :items="xmlOperatorOptions"
              value-key="value"
              class="w-full"
              @update:model-value="updateXmlRow(row.id, { operator: $event })"
            />
            <UInput
              v-if="valueOperators.has(row.operator)"
              :model-value="row.value"
              placeholder="Attribute value (case-sensitive)"
              @update:model-value="updateXmlRow(row.id, { value: String($event) })"
            />
            <p v-if="!row.attributeName.trim()" class="text-xs text-muted">
              Enter an attribute name to activate this filter.
            </p>
          </section>

          <UAccordion :items="[{ label: 'Index status', icon: 'i-lucide-database', slot: 'stats' }]" size="sm">
            <template #stats>
              <div class="space-y-2 py-2">
                <div v-if="isLoadingStats" class="flex items-center gap-2 text-xs text-muted">
                  <UIcon name="i-lucide-loader-2" class="animate-spin" /> Loading…
                </div>
                <template v-else-if="indexStats">
                  <div class="grid grid-cols-2 gap-2 text-xs">
                    <span class="text-muted">Total pages</span><span>{{ indexStats.totalPages }}</span>
                    <span class="text-muted">Indexed text</span><span>{{ indexStats.indexedTextContentPages }}</span>
                    <span class="text-muted">Indexed labels</span><span>{{ indexStats.indexedLabelPages }}</span>
                    <span class="text-muted">Indexed attributes</span><span>{{ indexStats.indexedXmlAttributePages }}</span>
                    <span class="text-muted">Needs indexing</span><span :class="{ 'text-warning': indexStats.pagesNeedingIndex > 0 }">{{ indexStats.pagesNeedingIndex }}</span>
                  </div>
                  <UButton
                    v-if="indexStats.pagesNeedingIndex > 0"
                    block
                    size="xs"
                    variant="outline"
                    color="warning"
                    :loading="isRebuilding"
                    @click="handleRebuildIndex"
                  >
                    Rebuild index
                  </UButton>
                </template>
              </div>
            </template>
          </UAccordion>
        </div>
      </div>
    </template>
  </UPopover>
</template>
