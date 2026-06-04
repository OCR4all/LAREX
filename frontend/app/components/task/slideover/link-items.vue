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

const props = defineProps<{
  taskId: string
  workspaceId: string
  linkedPageIds: string[]
  onLinked?: (linkedPages: { pageId: string, pageName: string, projectId: string, projectName: string }[]) => void | Promise<void>
}>()

const emit = defineEmits<{
  close: []
}>()

const toast = useToast()

const { data: projects, status: projectsStatus } = await useFetch<Project[]>(
  () => `/api/workspaces/${props.workspaceId}/projects`,
  {
    key: wsKey(props.workspaceId, 'projects', 'list'),
    default: () => []
  }
)

const selectedProjectId = ref<string | undefined>(undefined)
const linkAllPages = ref(true)
const searchQuery = ref('')
const selectedPageIds = ref<Set<string>>(new Set())
const isLinking = ref(false)

const projectTagFilter = ref<string[]>([])

const pageTagFilter = ref<string[]>([])

const { data: pages, status: pagesStatus } = await useFetch<Page[]>(
  () => selectedProjectId.value ? `/api/projects/${selectedProjectId.value}/pages` : '',
  {
    default: () => [],
    immediate: false,
    watch: [selectedProjectId]
  }
)

const projectTags = computed(() => {
  const tagCounts: Map<string, { label: string, count: number }> = new Map()

  for (const project of projects.value ?? []) {
    const rawTags = project.tags || []
    const resolvedTags = project.resolvedTags || []

    for (const tagId of rawTags) {
      if (tagId && typeof tagId === 'string') {
        const resolvedTag = resolvedTags.find(rt => rt.id === tagId)
        const label = resolvedTag?.label || tagId

        const existing = tagCounts.get(tagId)
        if (existing) {
          existing.count++
        } else {
          tagCounts.set(tagId, { label, count: 1 })
        }
      }
    }
  }

  return Array.from(tagCounts.entries())
    .sort((a, b) => a[1].label.localeCompare(b[1].label))
    .map(([value, { label, count }]) => ({
      label: `${label} (${count})`,
      value
    }))
})

const pageTags = computed(() => {
  const tagCounts: Map<string, { label: string, count: number }> = new Map()

  for (const page of pages.value ?? []) {
    const rawTags = page.tags || []
    const resolvedTags = page.resolvedTags || []

    for (const tagId of rawTags) {
      if (tagId && typeof tagId === 'string') {
        const resolvedTag = resolvedTags.find(rt => rt.id === tagId)
        const label = resolvedTag?.label || tagId

        const existing = tagCounts.get(tagId)
        if (existing) {
          existing.count++
        } else {
          tagCounts.set(tagId, { label, count: 1 })
        }
      }
    }
  }

  return Array.from(tagCounts.entries())
    .sort((a, b) => a[1].label.localeCompare(b[1].label))
    .map(([value, { label, count }]) => ({
      label: `${label} (${count})`,
      value
    }))
})

const filteredProjects = computed(() => {
  let result = projects.value ?? []

  if (projectTagFilter.value.length > 0) {
    result = result.filter(p =>
      (p.tags || []).some(tag => projectTagFilter.value.includes(tag))
    )
  }

  return result.slice().sort((a, b) => a.name.localeCompare(b.name))
})

const projectOptions = computed(() => {
  return filteredProjects.value.map(p => ({
    label: `${p.name} (${p.pageCount} pages)`,
    value: p.id
  }))
})

const selectedProject = computed(() => {
  if (!selectedProjectId.value) return null
  return projects.value?.find(p => p.id === selectedProjectId.value) ?? null
})

const availablePages = computed(() => {
  return (pages.value ?? [])
    .filter(p => !props.linkedPageIds.includes(p.id))
    .sort((a, b) => a.name.localeCompare(b.name))
})

const filteredPages = computed(() => {
  let result = availablePages.value

  if (pageTagFilter.value.length > 0) {
    result = result.filter(p =>
      (p.tags || []).some(tag => pageTagFilter.value.includes(tag))
    )
  }

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(p =>
      p.name.toLowerCase().includes(query)
      || (p.description || '').toLowerCase().includes(query)
    )
  }

  return result
})

const activePageFilters = computed(() => {
  const filters: Array<{ key: string, label: string, clear: () => void }> = []

  if (searchQuery.value.trim()) {
    filters.push({
      key: 'search',
      label: `Search: ${searchQuery.value}`,
      clear: () => { searchQuery.value = '' }
    })
  }

  for (const tagId of pageTagFilter.value) {
    const tagLabel = pageTags.value.find(tag => tag.value === tagId)?.label ?? tagId
    filters.push({
      key: `tag:${tagId}`,
      label: `Tag: ${tagLabel}`,
      clear: () => { pageTagFilter.value = pageTagFilter.value.filter(value => value !== tagId) }
    })
  }

  return filters
})

function clearPageFilters() {
  searchQuery.value = ''
  pageTagFilter.value = []
}

const allSelected = computed(() =>
  filteredPages.value.length > 0 && filteredPages.value.every(p => selectedPageIds.value.has(p.id))
)

const someSelected = computed(() =>
  selectedPageIds.value.size > 0 && !allSelected.value
)

function toggleSelectAll() {
  if (allSelected.value) {
    selectedPageIds.value.clear()
  } else {
    filteredPages.value.forEach(p => selectedPageIds.value.add(p.id))
  }
}

function togglePageSelection(pageId: string) {
  if (selectedPageIds.value.has(pageId)) {
    selectedPageIds.value.delete(pageId)
  } else {
    selectedPageIds.value.add(pageId)
  }
}

function isPageSelected(pageId: string) {
  return selectedPageIds.value.has(pageId)
}

watch(selectedProjectId, () => {
  selectedPageIds.value.clear()
  searchQuery.value = ''
  pageTagFilter.value = []
  linkAllPages.value = true
})

const linkSummary = computed(() => {
  if (!selectedProjectId.value) return null

  if (linkAllPages.value) {
    return {
      pagesCount: availablePages.value.length,
      pages: availablePages.value
    }
  } else {
    return {
      pagesCount: selectedPageIds.value.size,
      pages: availablePages.value.filter(p => selectedPageIds.value.has(p.id))
    }
  }
})

async function linkItems() {
  if (!selectedProjectId.value || !selectedProject.value) return
  if (!linkSummary.value) return

  isLinking.value = true
  const linkedPages: { pageId: string, pageName: string, projectId: string, projectName: string }[] = []

  try {
    const pageIdsToLink = linkAllPages.value
      ? availablePages.value.map(p => p.id)
      : Array.from(selectedPageIds.value)

    if (pageIdsToLink.length > 0) {
      await $fetch(`/api/tasks/${props.taskId}/links/pages`, {
        method: 'POST',
        body: { pageIds: pageIdsToLink }
      })

      const allPages = pages.value ?? []
      for (const pageId of pageIdsToLink) {
        const page = allPages.find(p => p.id === pageId)
        if (page) {
          linkedPages.push({
            pageId: page.id,
            pageName: page.name,
            projectId: selectedProjectId.value,
            projectName: selectedProject.value.name
          })
        }
      }
    }

    const projectName = selectedProject.value.name
    const message = `Linked ${linkedPages.length} page${linkedPages.length !== 1 ? 's' : ''} from ${projectName}`

    toast.add({ title: message, color: 'success' })

    await props.onLinked?.(linkedPages)

    emit('close')
  } catch (err: unknown) {
    const error = err as { data?: { message?: string } }
    toast.add({
      title: 'Failed to link pages',
      description: error.data?.message,
      color: 'error'
    })
  } finally {
    isLinking.value = false
  }
}

const columns: TableColumn<Page>[] = [
  {
    id: 'select',
    header: () => h(UCheckbox, {
      'modelValue': allSelected.value,
      'indeterminate': someSelected.value,
      'onUpdate:modelValue': () => toggleSelectAll()
    }),
    cell: ({ row }) => h(UCheckbox, {
      'modelValue': isPageSelected(row.original.id),
      'onUpdate:modelValue': () => togglePageSelection(row.original.id),
      'onClick': (e: Event) => e.stopPropagation()
    })
  },
  {
    accessorKey: 'name',
    header: 'Page Name',
    cell: ({ row }) => h('div', { class: 'min-w-0' }, [
      h('p', { class: 'font-medium truncate' }, row.original.name),
      row.original.description
        ? h('p', { class: 'text-sm text-muted truncate' }, row.original.description)
        : null
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
  <UiResponsiveSlideover
    :close="{ onClick: () => emit('close') }"
  >
    <template #header>
      <UiSlideoverHeader title="Link Pages" icon="i-lucide-link" />
    </template>

    <template #body>
      <div class="space-y-6">
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

        <UFormField label="Select Project" required>
          <USelect
            v-model="selectedProjectId"
            :items="projectOptions"
            placeholder="Choose a project..."
            :loading="projectsStatus === 'pending'"
            value-key="value"
          />
          <p v-if="filteredProjects.length === 0 && projectTagFilter.length > 0" class="text-xs text-warning mt-1">
            No projects match the selected tags
          </p>
        </UFormField>

        <template v-if="selectedProjectId">
          <div class="space-y-3">
            <label class="text-sm font-medium">Which pages to link?</label>
            <div class="space-y-2">
              <label class="flex items-start gap-3 p-3 border border-default rounded-sm cursor-pointer hover:bg-elevated/30 transition-colors" :class="{ 'border-primary bg-primary/5': linkAllPages }">
                <input
                  v-model="linkAllPages"
                  type="radio"
                  :value="true"
                  class="mt-0.5"
                >
                <div>
                  <p class="font-medium">All pages</p>
                  <p class="text-sm text-muted">
                    Link all {{ availablePages.length }} available page{{ availablePages.length !== 1 ? 's' : '' }} from this project
                  </p>
                </div>
              </label>

              <label class="flex items-start gap-3 p-3 border border-default rounded-sm cursor-pointer hover:bg-elevated/30 transition-colors" :class="{ 'border-primary bg-primary/5': !linkAllPages }">
                <input
                  v-model="linkAllPages"
                  type="radio"
                  :value="false"
                  class="mt-0.5"
                >
                <div>
                  <p class="font-medium">Select specific pages</p>
                  <p class="text-sm text-muted">Choose which pages to link</p>
                </div>
              </label>
            </div>
          </div>

          <template v-if="!linkAllPages">
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

              <UInput
                v-model="searchQuery"
                placeholder="Search pages..."
                icon="i-lucide-search"
              />

              <AppTableActiveFilters
                :filters="activePageFilters"
                @clear-all="clearPageFilters"
              />

              <div v-if="pagesStatus === 'pending'" class="flex items-center justify-center py-8">
                <UIcon name="i-lucide-loader-2" class="size-6 animate-spin text-muted" />
              </div>

              <div v-else-if="availablePages.length === 0" class="text-center py-6 text-muted">
                <UIcon name="i-lucide-file-x" class="size-10 mx-auto mb-2" />
                <p class="text-sm">
                  All pages from this project are already linked
                </p>
              </div>

              <div v-else-if="filteredPages.length === 0" class="text-center py-6 text-muted">
                <p class="text-sm">
                  No pages match your {{ pageTagFilter.length > 0 && searchQuery ? 'filters' : (pageTagFilter.length > 0 ? 'tag filter' : 'search') }}
                </p>
              </div>

              <div v-else class="max-h-64 overflow-auto border border-default rounded-sm">
                <AppTable
                  table-id="task-link-items"
                  :data="filteredPages"
                  :columns="columns"
                />
              </div>
            </div>
          </template>

          <div v-if="linkSummary && linkSummary.pagesCount > 0" class="p-3 bg-info/10 border border-info/20 rounded-sm">
            <p class="text-sm font-medium text-info">
              Summary
            </p>
            <p class="text-sm text-muted mt-1 flex items-center gap-2">
              <UIcon name="i-lucide-files" class="size-4" />
              {{ linkSummary.pagesCount }} page{{ linkSummary.pagesCount !== 1 ? 's' : '' }} from "{{ selectedProject?.name }}" will be linked
            </p>
          </div>

          <div v-else-if="availablePages.length === 0" class="p-3 bg-warning/10 border border-warning/20 rounded-sm">
            <p class="text-sm text-warning">
              All pages from this project are already linked to this task.
            </p>
          </div>
        </template>

        <div v-else class="text-center py-8 text-muted">
          <UIcon name="i-lucide-folder-search" class="size-12 mx-auto mb-3" />
          <p class="font-medium">
            Select a project
          </p>
          <p class="text-sm mt-1">
            Choose a project to see its pages
          </p>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <span class="text-sm text-muted">
          <template v-if="linkSummary">
            {{ linkSummary.pagesCount }} page{{ linkSummary.pagesCount !== 1 ? 's' : '' }} to link
          </template>
        </span>
        <div class="flex gap-2">
          <UButton
            color="neutral"
            variant="outline"
            :disabled="isLinking"
            @click="emit('close')"
          >
            Cancel
          </UButton>
          <UButton
            :loading="isLinking"
            :disabled="!linkSummary || linkSummary.pagesCount === 0 || isLinking"
            @click="linkItems"
          >
            Link {{ linkSummary?.pagesCount || '' }} Page{{ (linkSummary?.pagesCount ?? 0) !== 1 ? 's' : '' }}
          </UButton>
        </div>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
