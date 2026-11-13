<script setup lang="ts">
import type { MultiProjectPage } from '@/composables/use-multi-project-pages'
import PageWorkflowStateDropdown from '@/components/page/workflow-state-dropdown.vue'

type ResolvedTag = {
  id: string
  label: string
  color: string | null
}

type Project = {
  id: string
  name: string
  pageCount: number
  tags: string[]
  resolvedTags: ResolvedTag[] | null
}

export type SelectedTaskPage = {
  pageId: string
  pageName: string
  projectId: string
  projectName: string
}

const props = withDefaults(defineProps<{
  workspaceId: string
  excludedPageIds?: string[]
  initialSelection?: SelectedTaskPage[]
}>(), {
  excludedPageIds: () => [],
  initialSelection: () => []
})

const emit = defineEmits<{
  close: [pages: SelectedTaskPage[] | null]
}>()

const TABLE_ID = 'task-page-picker-v2'
const DEFAULT_VISIBLE_COLUMN_IDS = ['projectName', 'name', 'tags', 'workflowState', 'annotationState', 'imageCount']

const UIcon = resolveComponent('UIcon')

const formId = useId()

const { data: projects, status: projectsStatus } = await useFetch<Project[]>(
  () => `/api/workspaces/${props.workspaceId}/projects`,
  { key: wsKey(props.workspaceId, 'projects', 'list') }
)

const projectsById = computed(() => new Map((projects.value ?? []).map(project => [project.id, project])))

const selectedProjectIds = ref<string[]>([...new Set(props.initialSelection.map(page => page.projectId))])
const projectTagFilter = ref<string[]>([])

const projectTagOptions = computed(() => {
  const tags = new Map<string, { label: string, count: number }>()
  for (const project of projects.value ?? []) {
    for (const tagId of project.tags || []) {
      const label = project.resolvedTags?.find(tag => tag.id === tagId)?.label || tagId
      const current = tags.get(tagId)
      if (current) current.count++
      else tags.set(tagId, { label, count: 1 })
    }
  }
  return Array.from(tags.entries())
    .map(([value, tag]) => ({ value, label: `${tag.label} (${tag.count})` }))
    .sort((a, b) => a.label.localeCompare(b.label))
})

const projectOptions = computed(() => (projects.value ?? [])
  .filter(project => projectTagFilter.value.length === 0
    || selectedProjectIds.value.includes(project.id)
    || (project.tags || []).some(tag => projectTagFilter.value.includes(tag)))
  .map(project => ({
    value: project.id,
    label: project.name,
    pageCount: project.pageCount
  }))
  .sort((a, b) => a.label.localeCompare(b.label)))

function addFilteredProjects() {
  selectedProjectIds.value = [...new Set([...selectedProjectIds.value, ...projectOptions.value.map(option => option.value)])]
}

const { rows, pages, isLoading, loadingProjectIds, failedProjectIds, retry } = useMultiProjectPages(
  selectedProjectIds,
  projectId => projectsById.value.get(projectId)?.name ?? props.initialSelection.find(page => page.projectId === projectId)?.projectName ?? 'Unknown project'
)

const excludedIds = computed(() => new Set(props.excludedPageIds))
const availableRows = computed(() => excludedIds.value.size === 0
  ? rows.value
  : rows.value.filter(row => !excludedIds.value.has(row.id)))
const hiddenExcludedCount = computed(() => rows.value.length - availableRows.value.length)

const {
  sort,
  globalFilter,
  tagFilterOperator,
  annotationStatusFilter,
  workflowStateFilter,
  workflowStateOptions,
  activeFilters,
  filteredPages,
  uniqueTags,
  selectedTags,
  resetFilters
} = usePageTableFilters(availableRows, {
  tableId: TABLE_ID,
  defaultSort: { column: 'projectName', direction: 'asc' }
})

const selection = ref(new Map<string, SelectedTaskPage>(props.initialSelection.map(page => [page.pageId, page])))

watch(selectedProjectIds, (projectIds) => {
  const keep = new Set(projectIds)
  const next = new Map([...selection.value].filter(([, page]) => keep.has(page.projectId)))
  if (next.size !== selection.value.size) selection.value = next
})

function toSelectedPage(page: MultiProjectPage): SelectedTaskPage {
  return { pageId: page.id, pageName: page.name, projectId: page.projectId, projectName: page.projectName }
}

function togglePage(page: MultiProjectPage) {
  const next = new Map(selection.value)
  if (next.has(page.id)) next.delete(page.id)
  else next.set(page.id, toSelectedPage(page))
  selection.value = next
}

const selectedFilteredCount = computed(() => filteredPages.value.reduce((count, page) => count + (selection.value.has(page.id) ? 1 : 0), 0))
const allFilteredSelected = computed(() => filteredPages.value.length > 0 && selectedFilteredCount.value === filteredPages.value.length)

function toggleAllFiltered() {
  const next = new Map(selection.value)
  if (allFilteredSelected.value) {
    filteredPages.value.forEach(page => next.delete(page.id))
  } else {
    filteredPages.value.forEach(page => next.set(page.id, toSelectedPage(page)))
  }
  selection.value = next
}

function clearSelection() {
  selection.value = new Map()
}

const selectedProjectCount = computed(() => new Set([...selection.value.values()].map(page => page.projectId)).size)

const page = ref(1)
const itemsPerPageRef = usePersistentTablePageSize(TABLE_ID)
const totalItems = computed(() => filteredPages.value.length)
const totalPagesCount = computed(() => Math.max(1, Math.ceil(totalItems.value / itemsPerPageRef.value)))
const itemsPerPage = useItemsPerPageModel(page, itemsPerPageRef, totalItems)
const { rows: paginatedPages, infiniteScroll } = useTablePagination(filteredPages, page, itemsPerPageRef)
watch([globalFilter, selectedTags, annotationStatusFilter, workflowStateFilter, selectedProjectIds], () => {
  page.value = 1
})
useClampedPage(page, totalPagesCount)

const cells = usePageTableCells(sort)

const columns = [
  {
    id: 'select',
    header: () => h('input', {
      'type': 'checkbox',
      'checked': allFilteredSelected.value,
      'indeterminate': selectedFilteredCount.value > 0 && !allFilteredSelected.value,
      'aria-label': allFilteredSelected.value ? 'Deselect all matching pages' : 'Select all matching pages',
      'onChange': toggleAllFiltered,
      'class': 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    }),
    cell: ({ row }: { row: { original: MultiProjectPage } }) => h('input', {
      'type': 'checkbox',
      'checked': selection.value.has(row.original.id),
      'aria-label': `Select ${row.original.name}`,
      'onChange': () => togglePage(row.original),
      'onClick': (event: Event) => event.stopPropagation(),
      'class': 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    }),
    meta: { class: { th: 'w-10', td: 'w-10' } }
  },
  {
    accessorKey: 'projectName',
    header: cells.sortableHeader('Project', 'projectName'),
    meta: { label: 'Project' },
    cell: ({ row }: { row: { original: MultiProjectPage } }) => h('p', { class: 'truncate text-sm text-muted' }, row.original.projectName)
  },
  {
    accessorKey: 'name',
    header: cells.sortableHeader('Name', 'name'),
    meta: { label: 'Name' },
    cell: ({ row }: { row: { original: MultiProjectPage } }) => h('p', { class: 'min-w-0 truncate font-medium' }, row.original.name)
  },
  {
    accessorKey: 'description',
    header: 'Description',
    cell: ({ row }: { row: { original: MultiProjectPage } }) => row.original.description
      ? h('p', { class: 'text-sm text-muted truncate' }, row.original.description)
      : null
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }: { row: { original: MultiProjectPage } }) => cells.tagsCell(row.original)
  },
  {
    accessorKey: 'workflowState',
    header: 'State',
    cell: ({ row }: { row: { original: MultiProjectPage } }) => h('div', { class: 'flex items-center gap-1.5' }, [
      h(PageWorkflowStateDropdown, { state: row.original.workflowState, actions: [] }),
      row.original.changesRequested
        ? h(UIcon, {
            'name': 'i-lucide-message-square-warning',
            'class': 'size-4 text-warning',
            'aria-label': 'Changes requested',
            'title': 'Changes requested'
          })
        : null
    ])
  },
  {
    id: 'annotationState',
    header: () => h('div', { class: 'text-center' }, 'Annotation'),
    meta: { label: 'Annotation', class: { th: 'w-28 text-center', td: 'w-28 text-center' } },
    cell: ({ row }: { row: { original: MultiProjectPage } }) => {
      const hasAnnotation = row.original.xmlFileCount > 0
      return h(UIcon, {
        'name': hasAnnotation ? 'i-lucide-file-check-2' : 'i-lucide-file-minus-2',
        'class': ['size-4', hasAnnotation ? 'text-success' : 'text-dimmed'],
        'aria-label': hasAnnotation ? 'Has annotation' : 'No annotation',
        'title': hasAnnotation ? 'Has annotation' : 'No annotation'
      })
    }
  },
  {
    accessorKey: 'imageCount',
    header: cells.sortableHeader('Images', 'imageCount', { initialDirection: 'desc', align: 'center' }),
    meta: { label: 'Images', class: { th: 'w-24 text-center', td: 'w-24 text-center tabular-nums' } }
  },
  {
    accessorKey: 'updated',
    header: cells.sortableHeader('Updated', 'updated', { initialDirection: 'desc' }),
    meta: { label: 'Updated' }
  }
]

function selectPages() {
  emit('close', Array.from(selection.value.values()))
}
</script>

<template>
  <UiResponsiveSlideover
    :close="{ onClick: () => emit('close', null) }"
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-6xl' }"
  >
    <template #header>
      <UiSlideoverHeader
        title="Select Pages"
        icon="i-lucide-files"
        description="Choose pages from one or more projects. Each page becomes a Task in this Assignment."
      />
    </template>

    <template #body>
      <UForm :id="formId" class="flex min-h-0 flex-col gap-5" @submit="selectPages">
        <section class="space-y-3">
          <div class="flex flex-wrap items-end gap-2">
            <UFormField label="Projects" class="min-w-64 flex-1">
              <USelectMenu
                v-model="selectedProjectIds"
                :items="projectOptions"
                value-key="value"
                multiple
                :loading="projectsStatus === 'pending'"
                placeholder="Choose one or more projects..."
                :search-input="{ placeholder: 'Search projects...' }"
                class="w-full"
              >
                <template #leading>
                  <UIcon name="i-lucide-folder" />
                </template>
                <template #default>
                  <span v-if="selectedProjectIds.length === 0" class="text-dimmed">Choose one or more projects...</span>
                  <span v-else class="truncate">
                    {{ selectedProjectIds.length === 1 ? projectsById.get(selectedProjectIds[0]!)?.name : `${selectedProjectIds.length} projects` }}
                  </span>
                </template>
                <template #item-label="{ item }">
                  <span class="truncate">{{ item.label }}</span>
                </template>
                <template #item-trailing="{ item }">
                  <span class="text-xs text-muted tabular-nums">{{ item.pageCount }} pages</span>
                </template>
              </USelectMenu>
            </UFormField>

            <UFormField v-if="projectTagOptions.length > 0" label="Project tags" class="w-56">
              <USelectMenu
                v-model="projectTagFilter"
                :items="projectTagOptions"
                value-key="value"
                placeholder="All tags"
                multiple
                class="w-full"
              >
                <template #leading>
                  <UIcon name="i-lucide-tag" />
                </template>
              </USelectMenu>
            </UFormField>

            <UButton
              v-if="projectTagFilter.length > 0"
              color="neutral"
              variant="outline"
              icon="i-lucide-folder-plus"
              :disabled="projectOptions.length === 0"
              @click="addFilteredProjects"
            >
              Add {{ projectOptions.length }} tagged project{{ projectOptions.length === 1 ? '' : 's' }}
            </UButton>
          </div>

          <div v-if="selectedProjectIds.length > 0" class="flex flex-wrap items-center gap-1.5">
            <UBadge
              v-for="projectId in selectedProjectIds"
              :key="projectId"
              color="neutral"
              variant="subtle"
              size="sm"
              :icon="loadingProjectIds.has(projectId) ? 'i-lucide-loader-circle' : (failedProjectIds.has(projectId) ? 'i-lucide-triangle-alert' : 'i-lucide-folder')"
              :ui="loadingProjectIds.has(projectId) ? { leadingIcon: 'animate-spin' } : undefined"
            >
              {{ projectsById.get(projectId)?.name ?? 'Unknown project' }}
              <UButton
                v-if="failedProjectIds.has(projectId)"
                size="xs"
                variant="link"
                color="error"
                :padded="false"
                @click="retry(projectId)"
              >
                Retry
              </UButton>
              <UButton
                size="xs"
                variant="link"
                color="neutral"
                icon="i-lucide-x"
                :padded="false"
                :aria-label="`Remove ${projectsById.get(projectId)?.name ?? 'project'}`"
                @click="selectedProjectIds = selectedProjectIds.filter(id => id !== projectId)"
              />
            </UBadge>
            <UButton
              v-if="selectedProjectIds.length > 1"
              size="xs"
              variant="ghost"
              color="neutral"
              @click="selectedProjectIds = []"
            >
              Remove all
            </UButton>
          </div>
        </section>

        <section v-if="selectedProjectIds.length > 0" class="space-y-3 border-t border-default pt-5">
          <div class="flex flex-wrap items-center gap-2">
            <PageTableFilters
              v-model:search="globalFilter"
              v-model:tags="selectedTags"
              v-model:tag-operator="tagFilterOperator"
              v-model:annotation-status="annotationStatusFilter"
              v-model:workflow-state="workflowStateFilter"
              :tag-options="uniqueTags"
              :workflow-state-options="workflowStateOptions"
              :active="activeFilters.length > 0"
              search-class="w-full sm:w-56"
              @clear="resetFilters"
            />
            <div class="ml-auto">
              <UTooltip text="Columns">
                <AppTableColumnsDropdown
                  :table-id="TABLE_ID"
                  :columns="columns"
                  :default-visible-column-ids="DEFAULT_VISIBLE_COLUMN_IDS"
                />
              </UTooltip>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted">
            <span class="tabular-nums">
              {{ totalItems }} matching page{{ totalItems === 1 ? '' : 's' }}<template v-if="selectedFilteredCount > 0">, {{ selectedFilteredCount }} selected</template>
              <template v-if="hiddenExcludedCount > 0">
                · {{ hiddenExcludedCount }} already in this Assignment
              </template>
            </span>
            <UButton
              v-if="filteredPages.length > 0"
              size="xs"
              variant="link"
              color="primary"
              :padded="false"
              @click="toggleAllFiltered"
            >
              {{ allFilteredSelected ? 'Deselect' : 'Select' }} all {{ filteredPages.length }} matching
            </UButton>
          </div>

          <div v-if="isLoading && !pages" class="flex items-center justify-center py-12">
            <UIcon name="i-lucide-loader-2" class="size-6 animate-spin text-muted" />
          </div>
          <div v-else-if="!isLoading && availableRows.length === 0" class="py-10 text-center text-muted">
            <UIcon name="i-lucide-file-x" class="mx-auto mb-2 size-10" />
            <p class="text-sm">
              {{ rows.length > 0 ? 'Every page from these projects is already in this Assignment.' : 'These projects have no pages.' }}
            </p>
          </div>
          <div v-else-if="filteredPages.length === 0" class="py-10 text-center text-muted">
            <UIcon name="i-lucide-filter-x" class="mx-auto mb-2 size-10" />
            <p class="mb-3 text-sm">
              No pages match your filters.
            </p>
            <UButton
              color="neutral"
              variant="ghost"
              size="sm"
              @click="resetFilters"
            >
              Clear all filters
            </UButton>
          </div>
          <div v-else>
            <AppTable
              :table-id="TABLE_ID"
              :columns="columns"
              :data="paginatedPages"
              :infinite-scroll="infiniteScroll"
              :default-visible-column-ids="DEFAULT_VISIBLE_COLUMN_IDS"
              :loading="isLoading"
            />
            <div v-if="totalItems > 0" class="hidden items-center justify-between gap-4 border-t border-default pt-3 sm:flex">
              <span class="text-sm text-muted">
                Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }}
              </span>
              <div class="flex items-center gap-4">
                <USelect
                  v-model="itemsPerPage"
                  :items="[10, 20, 50, 100]"
                  class="w-24"
                  size="sm"
                />
                <UPagination
                  v-model:page="page"
                  :total="totalItems"
                  :items-per-page="itemsPerPage"
                  :disabled="totalPagesCount <= 1"
                  show-edges
                  :sibling-count="1"
                  size="sm"
                />
              </div>
            </div>
          </div>
        </section>

        <div v-else class="border-t border-default py-12 text-center text-muted">
          <UIcon name="i-lucide-folder-search" class="mx-auto mb-3 size-12" />
          <p class="font-medium">
            Choose projects
          </p>
          <p class="mt-1 text-sm">
            Pages from every chosen project appear in one table you can filter and sort.
          </p>
        </div>
      </UForm>
    </template>

    <template #footer>
      <div class="flex w-full flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-2 text-sm text-muted">
          <span>
            {{ selection.size }} page{{ selection.size === 1 ? '' : 's' }} selected<template v-if="selectedProjectCount > 1"> across {{ selectedProjectCount }} projects</template>
          </span>
          <UButton
            v-if="selection.size > 0"
            size="xs"
            variant="ghost"
            color="neutral"
            @click="clearSelection"
          >
            Clear
          </UButton>
        </div>
        <div class="flex gap-2">
          <UButton color="neutral" variant="outline" @click="emit('close', null)">
            Cancel
          </UButton>
          <UButton type="submit" :form="formId" :disabled="selection.size === 0 && initialSelection.length === 0">
            {{ selection.size === 0 && initialSelection.length > 0 ? 'Clear Selection' : `Select ${selection.size} Page${selection.size === 1 ? '' : 's'}` }}
          </UButton>
        </div>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
