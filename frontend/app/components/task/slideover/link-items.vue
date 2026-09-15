<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'

const UCheckbox = resolveComponent('UCheckbox')

type ResolvedTag = {
  id: string
  label: string
  color: string | null
}

type Project = {
  id: string
  name: string
  description: string
  pageCount: number
  tags: string[]
  resolvedTags: ResolvedTag[] | null
}

type Page = {
  id: string
  name: string
  description: string
  imageCount: number
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
}>(), {
  excludedPageIds: () => []
})

const emit = defineEmits<{
  close: [pages: SelectedTaskPage[] | null]
}>()

const formId = useId()
const selectedProjectId = ref<string | undefined>()
const searchQuery = ref('')
const selectedPageIds = ref<Set<string>>(new Set())
const selectedPages = ref<Map<string, SelectedTaskPage>>(new Map())
const projectTagFilter = ref<string[]>([])
const pageTagFilter = ref<string[]>([])

const { data: projects, status: projectsStatus } = await useFetch<Project[]>(
  () => `/api/workspaces/${props.workspaceId}/projects`,
  {
    key: wsKey(props.workspaceId, 'projects', 'list'),
    default: () => []
  }
)

const { data: pages, status: pagesStatus } = await useFetch<Page[]>(
  () => selectedProjectId.value ? `/api/projects/${selectedProjectId.value}/pages` : '',
  {
    default: () => [],
    immediate: false,
    watch: [selectedProjectId]
  }
)

const projectTags = computed(() => {
  const tagCounts = new Map<string, { label: string, count: number }>()
  for (const project of projects.value ?? []) {
    for (const tagId of project.tags || []) {
      const label = project.resolvedTags?.find(tag => tag.id === tagId)?.label || tagId
      const current = tagCounts.get(tagId)
      if (current) current.count++
      else tagCounts.set(tagId, { label, count: 1 })
    }
  }
  return Array.from(tagCounts.entries())
    .sort((a, b) => a[1].label.localeCompare(b[1].label))
    .map(([value, tag]) => ({ value, label: `${tag.label} (${tag.count})` }))
})

const pageTags = computed(() => {
  const tagCounts = new Map<string, { label: string, count: number }>()
  for (const page of pages.value ?? []) {
    for (const tagId of page.tags || []) {
      const label = page.resolvedTags?.find(tag => tag.id === tagId)?.label || tagId
      const current = tagCounts.get(tagId)
      if (current) current.count++
      else tagCounts.set(tagId, { label, count: 1 })
    }
  }
  return Array.from(tagCounts.entries())
    .sort((a, b) => a[1].label.localeCompare(b[1].label))
    .map(([value, tag]) => ({ value, label: `${tag.label} (${tag.count})` }))
})

const filteredProjects = computed(() => (projects.value ?? [])
  .filter(project => projectTagFilter.value.length === 0
    || (project.tags || []).some(tag => projectTagFilter.value.includes(tag)))
  .slice()
  .sort((a, b) => a.name.localeCompare(b.name)))

const projectOptions = computed(() => filteredProjects.value.map(project => ({
  label: `${project.name} (${project.pageCount} pages)`,
  value: project.id
})))

const selectedProject = computed(() => projects.value?.find(project => project.id === selectedProjectId.value) ?? null)

const availablePages = computed(() => (pages.value ?? [])
  .filter(page => !props.excludedPageIds.includes(page.id))
  .slice()
  .sort((a, b) => a.name.localeCompare(b.name)))

const filteredPages = computed(() => {
  let result = availablePages.value
  if (pageTagFilter.value.length > 0) {
    result = result.filter(page => (page.tags || []).some(tag => pageTagFilter.value.includes(tag)))
  }
  const query = searchQuery.value.trim().toLowerCase()
  if (query) {
    result = result.filter(page => page.name.toLowerCase().includes(query)
      || (page.description || '').toLowerCase().includes(query))
  }
  return result
})

const allSelected = computed(() => filteredPages.value.length > 0
  && filteredPages.value.every(page => selectedPageIds.value.has(page.id)))
const someSelected = computed(() => selectedPageIds.value.size > 0 && !allSelected.value)

function togglePageSelection(page: Page) {
  const nextIds = new Set(selectedPageIds.value)
  const nextPages = new Map(selectedPages.value)
  if (nextIds.has(page.id)) {
    nextIds.delete(page.id)
    nextPages.delete(page.id)
  } else {
    nextIds.add(page.id)
    nextPages.set(page.id, {
      pageId: page.id,
      pageName: page.name,
      projectId: selectedProjectId.value || '',
      projectName: selectedProject.value?.name || ''
    })
  }
  selectedPageIds.value = nextIds
  selectedPages.value = nextPages
}

function toggleSelectAll() {
  if (allSelected.value) {
    filteredPages.value.forEach(page => togglePageSelection(page))
  } else {
    filteredPages.value
      .filter(page => !selectedPageIds.value.has(page.id))
      .forEach(page => togglePageSelection(page))
  }
}

function clearPageFilters() {
  searchQuery.value = ''
  pageTagFilter.value = []
}

function selectPages() {
  emit('close', Array.from(selectedPages.value.values()))
}

const columns: TableColumn<Page>[] = [
  {
    id: 'select',
    header: () => h(UCheckbox, {
      'modelValue': allSelected.value,
      'indeterminate': someSelected.value,
      'onUpdate:modelValue': toggleSelectAll
    }),
    cell: ({ row }) => h(UCheckbox, {
      'modelValue': selectedPageIds.value.has(row.original.id),
      'onUpdate:modelValue': () => togglePageSelection(row.original),
      'onClick': (event: Event) => event.stopPropagation()
    })
  },
  {
    accessorKey: 'name',
    header: 'Page Name',
    cell: ({ row }) => h('div', { class: 'min-w-0' }, [
      h('p', { class: 'font-medium truncate' }, row.original.name),
      row.original.description ? h('p', { class: 'text-sm text-muted truncate' }, row.original.description) : null
    ])
  },
  {
    accessorKey: 'imageCount',
    header: 'Images',
    cell: ({ row }) => h('span', { class: 'text-sm text-muted' }, row.original.imageCount)
  }
]
</script>

<template>
  <UiResponsiveSlideover :close="{ onClick: () => emit('close', null) }">
    <template #header>
      <UiSlideoverHeader title="Select Pages" icon="i-lucide-files" description="Choose pages that should become Tasks in this Assignment." />
    </template>

    <template #body>
      <UForm :id="formId" class="space-y-6" @submit="selectPages">
        <div v-if="projectTags.length > 0" class="space-y-2">
          <label class="text-sm font-medium">Filter projects by tag</label>
          <USelectMenu
            v-model="projectTagFilter"
            :items="projectTags"
            value-key="value"
            placeholder="All tags"
            multiple
            class="w-full"
          />
        </div>

        <UFormField label="Project" required>
          <USelect
            v-model="selectedProjectId"
            :items="projectOptions"
            placeholder="Choose a project..."
            :loading="projectsStatus === 'pending'"
            value-key="value"
          />
          <p v-if="filteredProjects.length === 0 && projectTagFilter.length > 0" class="mt-1 text-xs text-warning">
            No projects match the selected tags.
          </p>
        </UFormField>

        <template v-if="selectedProjectId">
          <div class="space-y-3">
            <div v-if="pageTags.length > 0" class="space-y-2">
              <label class="text-xs font-medium text-muted">Filter by tag</label>
              <USelectMenu
                v-model="pageTagFilter"
                :items="pageTags"
                value-key="value"
                placeholder="All tags"
                multiple
                size="sm"
                class="w-full"
              />
            </div>

            <div class="flex items-center gap-2">
              <UInput
                v-model="searchQuery"
                placeholder="Search pages..."
                icon="i-lucide-search"
                class="min-w-0 flex-1"
              />
              <AppTableClearFiltersButton :active="Boolean(searchQuery || pageTagFilter.length)" @clear="clearPageFilters" />
            </div>

            <div v-if="pagesStatus === 'pending'" class="flex items-center justify-center py-8">
              <UIcon name="i-lucide-loader-2" class="size-6 animate-spin text-muted" />
            </div>
            <div v-else-if="availablePages.length === 0" class="text-center py-6 text-muted">
              <UIcon name="i-lucide-file-x" class="size-10 mx-auto mb-2" />
              <p class="text-sm">
                All pages from this project already have Tasks in this Assignment.
              </p>
            </div>
            <div v-else-if="filteredPages.length === 0" class="text-center py-6 text-muted">
              <p class="text-sm">
                No pages match the current filters.
              </p>
            </div>
            <div v-else class="max-h-64 overflow-auto border border-default rounded-sm">
              <AppTable table-id="task-page-picker" :data="filteredPages" :columns="columns" />
            </div>
          </div>
        </template>

        <div v-else class="text-center py-8 text-muted">
          <UIcon name="i-lucide-folder-search" class="size-12 mx-auto mb-3" />
          <p class="font-medium">
            Select a project
          </p>
          <p class="text-sm mt-1">
            Choose a project to see its pages.
          </p>
        </div>

        <div class="rounded-sm border border-info/20 bg-info/10 p-3 text-sm text-muted">
          <span class="font-medium text-info">{{ selectedPages.size }} page{{ selectedPages.size === 1 ? '' : 's' }} selected.</span>
          Switch projects to add pages from more than one project.
        </div>
      </UForm>
    </template>

    <template #footer>
      <div class="flex items-center justify-between w-full gap-3">
        <span class="text-sm text-muted">{{ selectedPages.size }} page{{ selectedPages.size === 1 ? '' : 's' }} selected</span>
        <div class="flex gap-2">
          <UButton color="neutral" variant="outline" @click="emit('close', null)">
            Cancel
          </UButton>
          <UButton type="submit" :form="formId" :disabled="selectedPages.size === 0">
            Select {{ selectedPages.size }} Page{{ selectedPages.size === 1 ? '' : 's' }}
          </UButton>
        </div>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
