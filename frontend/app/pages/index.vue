<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import { LazyCodecSlideoverAction, LazyLibrarySlideoverCreate, LazyShareSlideover, LazyProjectSlideoverEdit, LazyUiDeleteSlideover } from '#components'
import type { CodecProjectScope, GenerateCodecFromSourcesResponse, ValidateCodecAgainstSourcesResponse } from '@/types/codec'
import { DEFAULT_PROJECT_CAPABILITIES } from '@/types/capabilities'
import { extractApiErrorMessage, extractApiMessageFromPayload } from '@/utils/api-error'
import { globalKey, wsKey } from '@/utils/fetch-keys'
import UiColorTag from '@/components/ui/color-tag.vue'

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UPopover = resolveComponent('UPopover')
const UDropdownMenu = resolveComponent('UDropdownMenu')
const NuxtTime = resolveComponent('NuxtTime')
const NuxtLink = resolveComponent('NuxtLink')

const workspace = useWorkspaceStore()
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId)
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const { allow, compactGroups } = useActionVisibility()
const canCreateProjects = computed(() => allow(workspaceCapabilities.value.canManageProjects))

const { maybeAutoStartDashboardTour } = useOnboarding()
onMounted(() => {
  maybeAutoStartDashboardTour()
})

const libraryKey = computed(() => {
  if (!selectedWorkspace.value) return globalKey('pending', 'projects', 'list')
  return wsKey(selectedWorkspace.value, 'projects', 'list')
})

const overlay = useOverlay()
const toast = useToast()

const librarySlideoverCreate = overlay.create(LazyLibrarySlideoverCreate)
const codecActionSlideover = overlay.create(LazyCodecSlideoverAction)

const emptyActions = computed(() => {
  const actions: Array<{ icon: string, label: string, color?: string, variant: 'solid' | 'subtle', onClick: () => void }> = []
  if (canCreateProjects.value) {
    actions.push({
      icon: 'i-lucide-package-plus',
      label: 'Create new',
      variant: 'solid',
      onClick: () => librarySlideoverCreate.open()
    })
  }
  actions.push({
    icon: 'i-lucide-refresh-cw',
    label: 'Refresh',
    color: 'neutral',
    variant: 'subtle',
    onClick: () => refresh()
  })
  return actions
})

const editSlideover = overlay.create(LazyProjectSlideoverEdit)

const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

const shareSlideover = overlay.create(LazyShareSlideover)
const importProjectPackageInput = ref<HTMLInputElement | null>(null)

type ResolvedTag = {
  id: string
  label: string
  color: string | null
}

const DEFAULT_CUSTOM_TAG_COLOR = '#2563eb'

type LibraryProject = {
  id: string
  name: string
  description: string
  tags: string[]
  resolvedTags: ResolvedTag[] | null
  created: string
  updated: string
  pageCount: number
  isStarred: boolean
  storageUsedBytes: number
  storageUsedFormatted: string
  locked: boolean
  lockedReason: string | null
  tagSetId?: string | null
  capabilities?: {
    canEdit: boolean
    canShare: boolean
    canDelete: boolean
    canDeletePages: boolean
    canUpload: boolean
    canExportPackage: boolean
  }
}

const {
  data,
  error,
  status,
  refresh
} = await useFetch<LibraryProject[]>(() => `/api/workspaces/${selectedWorkspace.value}/projects`,
  {
    key: libraryKey,
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

watch(error, (err) => {
  if (err) {
    console.error('Error loading projects:', err)
  }
})

const route = useRoute()
const router = useRouter()

const isStarredFilter = computed(() => {
  return route.query.starred === 'true'
})

const {
  sort,
  globalFilter,
  columnFilters,
  tagFilterOperator,
  filteredAndSortedData,
  activeFilters,
  setColumnFilter,
  clearColumnFilter,
  resetAllFilters
} = useTableFilters(data, { column: 'created', direction: 'desc' })

const page = ref(1)
const itemsPerPage = ref(10)

const totalItems = computed(() => filteredAndSortedData.value.length)
const totalPages = computed(() => Math.ceil(totalItems.value / itemsPerPage.value))

const paginatedData = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  const end = start + itemsPerPage.value
  return filteredAndSortedData.value.slice(start, end)
})

const selectedProjectIds = ref<Set<string>>(new Set())
const hasSelection = computed(() => selectedProjectIds.value.size > 0)
const deletingProjectIds = ref<Set<string>>(new Set())

const allFilteredSelected = computed(() => {
  if (filteredAndSortedData.value.length === 0) return false
  return filteredAndSortedData.value.every(project => selectedProjectIds.value.has(project.id))
})

function toggleProjectSelection(projectId: string) {
  const next = new Set(selectedProjectIds.value)
  if (next.has(projectId)) {
    next.delete(projectId)
  } else {
    next.add(projectId)
  }
  selectedProjectIds.value = next
}

function toggleAllFilteredSelection() {
  if (allFilteredSelected.value) {
    selectedProjectIds.value = new Set()
    return
  }

  selectedProjectIds.value = new Set(filteredAndSortedData.value.map(project => project.id))
}

watch(data, (projects) => {
  const validIds = new Set((projects ?? []).map(project => project.id))
  const filtered = Array.from(selectedProjectIds.value).filter(id => validIds.has(id))
  selectedProjectIds.value = new Set(filtered)
}, { deep: true })

watch([globalFilter, columnFilters], () => {
  page.value = 1
}, { deep: true })

onMounted(() => {
  if (isStarredFilter.value) {
    setColumnFilter('isStarred', true)
  }
})

const uniqueTags = computed(() => {
  const tagCounts = new Map<string, { label: string, count: number }>()

  data.value.forEach((project) => {
    const rawTags = project.tags || []
    const resolvedTags = project.resolvedTags || []

    rawTags.forEach((tagId) => {
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
    })
  })

  return Array.from(tagCounts.entries())
    .sort((a, b) => a[1].label.localeCompare(b[1].label))
    .map(([value, { label, count }]) => ({
      label,
      value,
      count
    }))
})

const selectedTags = computed({
  get: () => {
    const tags = columnFilters.value['tags']
    if (Array.isArray(tags)) return tags
    return []
  },
  set: (value: string[]) => {
    if (value.length === 0) {
      clearColumnFilter('tags')
    } else {
      setColumnFilter('tags', value)
    }
  }
})

const tagOperatorOptions = [
  { label: 'Match any (OR)', value: 'or' },
  { label: 'Match all (AND)', value: 'and' }
]

const columns: TableColumn<any>[] = [
  {
    id: 'select',
    header: () => h('input', {
      type: 'checkbox',
      checked: allFilteredSelected.value,
      indeterminate: hasSelection.value && !allFilteredSelected.value,
      onChange: toggleAllFilteredSelection,
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    }),
    cell: ({ row }) => h('input', {
      type: 'checkbox',
      checked: selectedProjectIds.value.has(row.original.id),
      onChange: () => toggleProjectSelection(row.original.id),
      onClick: (e: Event) => e.stopPropagation(),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: () => {
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', 'Name'),
        h(UButton, {
          icon: sort.value.column === 'name'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'name' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'name') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'name', direction: 'asc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => h('div', { class: 'flex items-center gap-2' }, [
      row.original.locked ? h('span', { class: 'text-amber-500', title: row.original.lockedReason || 'Locked' }, h(resolveComponent('UIcon'), { name: 'i-lucide-lock', class: 'w-4 h-4' })) : null,
      h(NuxtLink, { to: `/project/${row.original.id}`, class: 'font-medium hover:underline text-primary' }, () => row.getValue('name'))
    ])
  },
  {
    accessorKey: 'description',
    header: () => {
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', 'Description'),
        h(UButton, {
          icon: sort.value.column === 'description'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'description' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'description') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'description', direction: 'asc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => {
      const description = row.getValue('description') as string
      if (!description) return h('div', { class: 'text-neutral-400 dark:text-neutral-500 text-sm' }, '—')

      return h('div', {
        class: 'text-neutral-700 dark:text-neutral-400 max-w-32 sm:max-w-48 lg:max-w-64 xl:max-w-80 truncate',
        title: description
      }, description)
    }
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }) => {
      const resolvedTags = row.original.resolvedTags
      const rawTags = row.getValue('tags') as string[]

      if ((!resolvedTags || resolvedTags.length === 0) && (!rawTags || rawTags.length === 0)) {
        return null
      }

      const displayTags: Array<{ label: string, color: string }> = resolvedTags && resolvedTags.length > 0
        ? resolvedTags.map((rt: ResolvedTag) => ({ label: rt.label || rt.id, color: rt.color || DEFAULT_CUSTOM_TAG_COLOR }))
        : rawTags.map(tagId => ({ label: tagId, color: DEFAULT_CUSTOM_TAG_COLOR }))

      const renderTagBadge = (tag: { label: string, color: string }, index: number) => h(
        UiColorTag,
        {
          key: `${tag.label}-${index}`,
          color: tag.color,
          variant: 'subtle',
          size: 'sm'
        },
        () => tag.label
      )

      if (displayTags.length <= 3) {
        return h('div', { class: 'flex flex-wrap gap-1' },
          displayTags.map((tag: { label: string, color: string }, index: number) => renderTagBadge(tag, index))
        )
      }

      const visibleTags = displayTags.slice(0, 2)
      const hiddenTags = displayTags.slice(2)

      return h('div', { class: 'flex flex-wrap items-center gap-1' }, [
        ...visibleTags.map((tag: { label: string, color: string }, index: number) => renderTagBadge(tag, index)),
        h(UPopover, { mode: 'hover' }, {
          default: () => h(UButton, {
            variant: 'soft',
            color: 'primary',
            size: 'sm',
            class: 'h-[22px]'
          }, () => `+${hiddenTags.length}`),

          content: () => h('div', { class: 'p-2 flex flex-col gap-1' },
            hiddenTags.map((tag: { label: string, color: string }, index: number) => renderTagBadge(tag, index))
          )
        })
      ])
    }
  },
  {
    accessorKey: 'pageCount',
    header: () => {
      return h('div', { class: 'flex items-center justify-end gap-2' }, [
        h('span', 'Pages'),
        h(UButton, {
          icon: sort.value.column === 'pageCount'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'pageCount' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'pageCount') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'pageCount', direction: 'asc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => h('div', { class: 'text-right font-medium' }, row.getValue('pageCount'))
  },
  {
    accessorKey: 'storageUsedBytes',
    header: () => {
      return h('div', { class: 'flex items-center justify-end gap-2' }, [
        h('span', 'Storage'),
        h(UButton, {
          icon: sort.value.column === 'storageUsedBytes'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'storageUsedBytes' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'storageUsedBytes') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'storageUsedBytes', direction: 'desc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => h('div', { class: 'text-right text-sm text-muted' }, row.original.storageUsedFormatted || '0 B')
  },
  {
    accessorKey: 'created',
    header: () => {
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', 'Created'),
        h(UButton, {
          icon: sort.value.column === 'created'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'created' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'created') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'created', direction: 'asc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => h(NuxtTime, { datetime: row.getValue('created') })
  },
  {
    accessorKey: 'updated',
    header: () => {
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', 'Updated'),
        h(UButton, {
          icon: sort.value.column === 'updated'
            ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
            : 'i-lucide-arrow-up-down',
          size: 'xs',
          variant: 'ghost',
          color: sort.value.column === 'updated' ? 'primary' : 'neutral',
          onClick: () => {
            if (sort.value.column === 'updated') {
              sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
            } else {
              sort.value = { column: 'updated', direction: 'asc' }
            }
          }
        })
      ])
    },
    cell: ({ row }) => h(NuxtTime, { datetime: row.getValue('updated') })
  },
  {
    id: 'actions',
    cell: ({ row }) => {
      return h(
        'div',
        { class: 'text-right' },
        h(
          UDropdownMenu,
          {
            'content': {
              align: 'end'
            },
            'items': getRowItems(row),
            'aria-label': 'Actions dropdown'
          },
          () =>
            h(UButton, {
              'icon': 'i-lucide-ellipsis-vertical',
              'color': 'neutral',
              'variant': 'ghost',
              'class': 'ml-auto',
              'aria-label': 'Actions dropdown'
            })
        )
      )
    }
  }
]

function handleRowClick(row: Row<LibraryProject>) {
  navigateTo(`/project/${row.original.id}`)
}

function getProjectCapabilities(project: LibraryProject) {
  return {
    ...DEFAULT_PROJECT_CAPABILITIES,
    ...(project.capabilities ?? {})
  }
}

async function handleDeleteProject(project: LibraryProject) {
  const capabilities = getProjectCapabilities(project)
  if (!allow(capabilities.canDelete)) return
  if (deletingProjectIds.value.has(project.id)) return

  const instance = deleteSlideover.open({
    name: project.name,
    entityType: 'Project',
    warningDetails: [
      `${project.pageCount} ${project.pageCount === 1 ? 'page' : 'pages'}`,
      'All associated images and XML files',
      'All project history and annotations'
    ]
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const projects = data.value ?? []
  const removedIndex = projects.findIndex(item => item.id === project.id)
  if (removedIndex === -1) return

  const progressToast = toast.add({
    title: 'Deleting Project',
    description: project.name,
    color: 'neutral',
    icon: 'i-lucide-loader-circle',
    ui: { icon: 'animate-spin' },
    close: false,
    progress: false,
    duration: 0
  })

  const removedProject = projects[removedIndex]
  if (!removedProject) return
  data.value = [
    ...projects.slice(0, removedIndex),
    ...projects.slice(removedIndex + 1)
  ]

  const wasSelected = selectedProjectIds.value.has(project.id)
  if (wasSelected) {
    const next = new Set(selectedProjectIds.value)
    next.delete(project.id)
    selectedProjectIds.value = next
  }

  deletingProjectIds.value = new Set(deletingProjectIds.value).add(project.id)

  try {
    await $fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${project.id}`, { method: 'DELETE' })
    toast.add({
      title: 'Project Deleted',
      description: `"${project.name}" has been permanently deleted`,
      color: 'success',
      icon: 'i-lucide-trash-2'
    })
    void refreshNuxtData(libraryKey.value)
  } catch (error: unknown) {
    const hasProject = (data.value ?? []).some(item => item.id === project.id)
    if (!hasProject) {
      const next = [...(data.value ?? [])]
      next.splice(Math.min(removedIndex, next.length), 0, removedProject)
      data.value = next
    }

    if (wasSelected) {
      const next = new Set(selectedProjectIds.value)
      next.add(project.id)
      selectedProjectIds.value = next
    }

    const message = typeof error === 'object' && error !== null && 'data' in error
      ? (error as { data?: { message?: string } }).data?.message
      : undefined

    toast.add({
      title: 'Delete Failed',
      description: message || 'Failed to delete project',
      color: 'error'
    })
  } finally {
    toast.remove(progressToast.id)
    const nextDeleting = new Set(deletingProjectIds.value)
    nextDeleting.delete(project.id)
    deletingProjectIds.value = nextDeleting
  }
}

async function openEditProjectSlideover(project: LibraryProject) {
  const capabilities = getProjectCapabilities(project)
  if (!allow(capabilities.canEdit)) return

  const instance = editSlideover.open({ project: project as any })
  const updated = await instance.result
  if (!updated) return

  await refreshNuxtData(libraryKey.value)
}

async function openShareSlideover(project: LibraryProject) {
  const capabilities = getProjectCapabilities(project)
  if (!allow(capabilities.canShare)) return

  const instance = shareSlideover.open({
    resourceId: project.id,
    resourceName: project.name,
    resourceType: 'PROJECT',
    currentWorkspaceId: selectedWorkspace.value ?? ''
  })

  const transferred = await instance.result
  if (!transferred) return

  await refreshNuxtData(libraryKey.value)
}

function getRowItems(row: Row<LibraryProject>) {
  const capabilities = getProjectCapabilities(row.original)
  const groups: any[][] = [[
    {
      type: 'label',
      label: 'Actions'
    },
    {
      label: 'Open Project',
      icon: 'i-lucide-folder-open',
      onSelect() {
        navigateTo(`/project/${row.original.id}`)
      }
    }
  ]]

  const mutationActions: any[] = []
  if (allow(capabilities.canEdit) && !row.original.locked) {
    mutationActions.push({
      label: 'Edit',
      icon: 'i-lucide-edit',
      onSelect() {
        void openEditProjectSlideover(row.original)
      }
    })
  }

  if (allow(capabilities.canShare) && !row.original.locked) {
    mutationActions.push({
      label: 'Share',
      icon: 'i-lucide-share',
      onSelect() {
        void openShareSlideover(row.original)
      }
    })
  }

  if (allow(capabilities.canDelete) && !row.original.locked) {
    mutationActions.push({
      label: 'Delete',
      icon: 'i-lucide-trash',
      color: 'error',
      disabled: deletingProjectIds.value.has(row.original.id),
      onSelect() {
        void handleDeleteProject(row.original)
      }
    })
  }

  if (mutationActions.length > 0) {
    groups.push(mutationActions)
  }

  return compactGroups(groups)
}

const contextMenuRow = ref<Row<LibraryProject> | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuRow.value) return []
  return getRowItems(contextMenuRow.value)
})

function handleRowContextMenu(_event: Event, row: { original: Record<string, unknown> }) {
  contextMenuRow.value = row as Row<LibraryProject>
}

const selectedSources = computed<CodecProjectScope[]>(() => {
  return Array.from(selectedProjectIds.value).map(projectId => ({
    projectId,
    pageIds: []
  }))
})

async function openCodecGenerateSlideover() {
  if (!selectedWorkspace.value || !hasSelection.value) return

  const instance = codecActionSlideover.open({
    mode: 'generate',
    workspaceId: selectedWorkspace.value,
    sources: selectedSources.value
  })
  const result = await instance.result as GenerateCodecFromSourcesResponse | null
  if (!result) return

  await refreshNuxtData(wsKey(selectedWorkspace.value, 'codecs', 'list'))
}

async function openCodecValidateSlideover() {
  if (!selectedWorkspace.value || !hasSelection.value) return

  const instance = codecActionSlideover.open({
    mode: 'validate',
    workspaceId: selectedWorkspace.value,
    sources: selectedSources.value
  })
  await instance.result as ValidateCodecAgainstSourcesResponse | null
}

function triggerProjectPackageImport() {
  importProjectPackageInput.value?.click()
}

async function handleProjectPackageImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selectedWorkspace.value) {
    input.value = ''
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', file)

    const response = await fetch(`/api/upload-proxy/workspaces/${selectedWorkspace.value}/projects/import-package`, {
      method: 'POST',
      body: formData
    })

    if (!response.ok) {
      let payload: unknown = null
      try {
        payload = await response.json()
      } catch {
        payload = null
      }
      throw new Error(extractApiMessageFromPayload(payload, `Import failed (${response.status})`))
    }

    const result = await response.json() as { projectName?: string }
    toast.add({
      title: 'Project package imported',
      description: result.projectName ? `Created "${result.projectName}"` : undefined,
      color: 'success',
      icon: 'i-lucide-upload'
    })

    await refresh()
  } catch (error: unknown) {
    const message = extractApiErrorMessage(error, 'Failed to import project package')
    toast.add({
      title: 'Import failed',
      description: message,
      color: 'error'
    })
  } finally {
    input.value = ''
  }
}

const libraryCodecActionItems = computed(() => [[
  {
    label: 'Generate Codec',
    icon: 'i-lucide-wand-sparkles',
    disabled: !hasSelection.value,
    onSelect: () => {
      void openCodecGenerateSlideover()
    }
  },
  {
    label: 'Validate Codec',
    icon: 'i-lucide-badge-check',
    disabled: !hasSelection.value,
    onSelect: () => {
      void openCodecValidateSlideover()
    }
  },
  {
    label: 'Import Project Package',
    icon: 'i-lucide-file-up',
    disabled: !selectedWorkspace.value,
    onSelect: () => {
      triggerProjectPackageImport()
    }
  }
]])
</script>

<template>
  <UDashboardPanel id="library">
    <template #header>
      <UDashboardNavbar title="Library">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <input
            ref="importProjectPackageInput"
            type="file"
            class="hidden"
            accept=".zip,.larex-project.zip,application/zip,application/octet-stream"
            @change="handleProjectPackageImport"
          >
          <UFieldGroup>
            <UButton
              v-if="canCreateProjects"
              label="New Project"
              color="neutral"
              variant="outline"
              icon="i-lucide-package-plus"
              @click="librarySlideoverCreate.open()"
            />
            <UDropdownMenu :items="libraryCodecActionItems" :content="{ align: 'end' }">
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-chevron-down"
              />
            </UDropdownMenu>
          </UFieldGroup>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="globalFilter"
            placeholder="Search projects..."
            icon="i-lucide-search"
            class="w-64"
            size="md"
          >
            <template v-if="globalFilter" #trailing>
              <UButton
                color="neutral"
                variant="link"
                icon="i-lucide-x"
                :padded="false"
                @click="globalFilter = ''"
              />
            </template>
          </UInput>

          <USelectMenu
            v-model="selectedTags"
            :items="uniqueTags"
            value-key="value"
            placeholder="Filter by tag"
            searchable
            multiple
            clear-search-on-close
            searchable-placeholder="Search tags..."
            class="w-48"
          >
            <template #item="{ item }">
              <div class="flex items-center justify-between w-full gap-2">
                <div class="flex items-center gap-2">
                  <UIcon
                    v-if="selectedTags.includes(item.value)"
                    name="i-lucide-check"
                    class="text-primary w-4 h-4"
                  />
                  <span v-else class="w-4 h-4" />
                  <span>{{ item.label }}</span>
                </div>
                <UBadge variant="solid" color="neutral" size="xs">
                  {{ item.count }}
                </UBadge>
              </div>
            </template>
          </USelectMenu>

          <USelectMenu
            v-if="selectedTags.length > 1"
            v-model="tagFilterOperator"
            :items="tagOperatorOptions"
            value-key="value"
            class="w-36"
          >
            <template #leading>
              <UIcon name="i-lucide-git-merge" class="w-4 h-4" />
            </template>
          </USelectMenu>

          <UButton
            :color="columnFilters['isStarred'] ? 'yellow' : 'neutral'"
            :variant="columnFilters['isStarred'] ? 'soft' : 'ghost'"
            size="sm"
            :icon="columnFilters['isStarred'] ? 'i-prime-star-fill' : 'i-prime-star'"
            @click="() => {
              if (columnFilters['isStarred']) {
                clearColumnFilter('isStarred')
                router.replace({ query: { ...route.query, starred: undefined } })
              }
              else {
                setColumnFilter('isStarred', true)
                router.replace({ query: { ...route.query, starred: 'true' } })
              }
            }"
          >
            Starred
          </UButton>

          <UButton
            v-if="activeFilters.length > 0"
            color="neutral"
            variant="ghost"
            size="sm"
            @click="() => {
              resetAllFilters()
              router.replace({ query: {} })
            }"
          >
            Clear Filters
          </UButton>
        </template>
        <template #right>
          <div class="flex items-center gap-2">
            <UBadge
              v-if="hasSelection"
              variant="soft"
              color="primary"
              size="sm"
            >
              {{ selectedProjectIds.size }} selected
            </UBadge>
            <div v-if="activeFilters.length > 0" class="flex items-center gap-2">
              <span class="text-xs text-neutral-500">Active filters:</span>
              <UBadge
                v-for="filter in activeFilters"
                :key="`${filter.type}-${filter.column || 'global'}`"
                variant="soft"
                color="neutral"
                size="sm"
                class="flex items-center gap-1"
              >
                {{ filter.label }}
                <UButton
                  size="2xs"
                  color="neutral"
                  variant="link"
                  icon="i-lucide-x"
                  :padded="false"
                  @click="filter.clear()"
                />
              </UBadge>
            </div>
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="error" class="py-8 text-center">
        <div class="flex items-center justify-center gap-2 text-red-600 dark:text-red-400">
          <UIcon name="i-lucide-alert-circle" />
          <p class="text-sm">
            <strong>Error loading projects:</strong> {{ error.message || error }}
          </p>
        </div>
      </div>

      <div v-else-if="status === 'pending'" class="py-8 text-center">
        <div class="flex items-center justify-center">
          <UIcon name="i-lucide-loader" class="animate-spin text-neutral-500" />
          <span class="ml-2 text-sm text-neutral-600 dark:text-neutral-400">Loading projects...</span>
        </div>
      </div>

      <UEmpty
        v-else-if="data && data.length === 0"
        variant="naked"
        icon="i-lucide-book"
        title="No projects found"
        description="It looks like you haven't added any projects. Create one to get started."
        :actions="emptyActions as any"
      />

      <UEmpty
        v-else-if="data && filteredAndSortedData.length === 0 && activeFilters.length > 0"
        variant="naked"
        icon="i-lucide-search-x"
        title="No projects match your filters"
        description="Try adjusting or clearing your filters to see more results."
        :actions="[{
          icon: 'i-lucide-x',
          label: 'Clear filters',
          color: 'neutral',
          variant: 'subtle',
          onClick: () => { resetAllFilters(); router.replace({ query: {} }) }
        }]"
      />

      <div v-else-if="data">
        <UContextMenu :items="contextMenuItems as any">
          <UTable
            :data="paginatedData"
            :columns="columns"
            class="flex-1"
            :ui="{
              base: 'table-fixed border-separate border-spacing-0',
              thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
              tbody: '[&>tr]:last:[&>td]:border-b-0',
              th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
              td: 'border-b border-default',
              separator: 'h-0'
            }"
            @row-click="handleRowClick"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <div v-if="totalPages > 1" class="flex justify-between items-center p-4 border-t border-neutral-200 dark:border-neutral-800">
          <div class="flex items-center gap-2 text-sm text-neutral-600 dark:text-neutral-400">
            <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} projects</span>
          </div>

          <div class="flex items-center gap-4">
            <USelect
              v-model="itemsPerPage"
              :items="[5, 10, 15, 20, 50, 100]"
              class="w-32"
              size="sm"
            />

            <UPagination
              v-model:page="page"
              :total="totalItems"
              :items-per-page="itemsPerPage"
              show-edges
              :sibling-count="1"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
