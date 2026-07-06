<script setup lang="ts">
import type { DropdownMenuItem, TableColumn } from '@nuxt/ui'
import type { Row } from '@tanstack/vue-table'
import {
  LazyCodecSlideoverAction,
  LazyLibrarySlideoverCreate,
  LazyShareSlideover,
  LazyProjectSlideoverEdit,
  LazyUiDeleteSlideover,
  NuxtLink,
  UAvatar,
  UBadge,
  UButton,
  UDropdownMenu,
  UIcon,
  UPopover
} from '#components'
import type { CodecProjectScope, GenerateCodecFromSourcesResponse, ValidateCodecAgainstSourcesResponse } from '@/types/codec'
import { DEFAULT_PROJECT_CAPABILITIES } from '@/types/capabilities'
import UiColorTag from '@/components/ui/color-tag.vue'
import { createSkeletonPageData, type PageResponse } from '@/services/editor/project-loader'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { naturalSortBy } from '@/utils/natural-sort'

const { selectedWorkspace } = await useWorkspaceBootstrap()
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const { allow, compactGroups } = useActionVisibility()
const canCreateProjects = computed(() => allow(workspaceCapabilities.value.canManageProjects))

const { maybeAutoStartDashboardTour } = useOnboarding()
onMounted(() => {
  maybeAutoStartDashboardTour()
})

const projectsKey = computed(() => {
  if (!selectedWorkspace.value) return globalKey('pending', 'projects', 'list')
  return wsKey(selectedWorkspace.value, 'projects', 'list')
})

const overlay = useOverlay()
const toast = useToast()
const { uploadFormDataWithProgress } = useTrackedUpload()
const collaborationPageSummary = useCollaborationPageSummary()
const editorStore = useEditorStore()
const sessionStore = useEditorSessionStore()

const projectSlideoverCreate = overlay.create(LazyLibrarySlideoverCreate)
const codecActionSlideover = overlay.create(LazyCodecSlideoverAction)

const emptyActions = computed(() => {
  const actions: Array<{ icon: string, label: string, color?: string, variant: 'solid' | 'subtle', onClick: () => void }> = []
  if (canCreateProjects.value) {
    actions.push({
      icon: 'i-lucide-package-plus',
      label: 'Create new',
      variant: 'solid',
      onClick: () => projectSlideoverCreate.open()
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
const importLegacyOcr4allInput = ref<HTMLInputElement | null>(null)

type ResolvedTag = {
  id: string
  label: string
  color: string | null
}

const DEFAULT_CUSTOM_TAG_COLOR = '#2563eb'
const DEFAULT_PROJECTS_VISIBLE_COLUMN_IDS = ['name', 'description', 'tags', 'pageCount', 'updated']

type ProjectListItem = {
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
} = await useFetch<ProjectListItem[]>(() => `/api/workspaces/${selectedWorkspace.value}/projects`,
  {
    key: projectsKey,
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
  activeFilters,
  setColumnFilter,
  clearColumnFilter,
  resetAllFilters,
  filteredAndSortedData,
  uniqueTags,
  selectedTags,
  tagOperatorOptions,
  page,
  itemsPerPage,
  totalItems,
  totalPages,
  paginatedData
} = useResourceListPage({
  data,
  defaultSort: { column: 'created', direction: 'desc' },
  tableId: 'dashboard-projects-v2',
  getTags: (project) => {
    const rawTags = project.tags ?? []
    const resolvedTags = project.resolvedTags ?? []
    return rawTags.map(tagId => ({
      value: tagId,
      label: resolvedTags.find(tag => tag.id === tagId)?.label || tagId
    }))
  }
})

function clearProjectFilters() {
  resetAllFilters()
  router.replace({ query: {} })
}

const selectedProjectIds = ref<Set<string>>(new Set())
const hasSelection = computed(() => selectedProjectIds.value.size > 0)
const deletingProjectIds = ref<Set<string>>(new Set())
const isOpeningSelectedProjectsInEditor = ref(false)
const selectedProjects = computed(() => (data.value ?? []).filter(project => selectedProjectIds.value.has(project.id)))
const canDeleteSelectedProjects = computed(() =>
  selectedProjects.value.length > 0
  && selectedProjects.value.every((project) => {
    const capabilities = getProjectCapabilities(project)
    return allow(capabilities.canDelete) && !project.locked && !deletingProjectIds.value.has(project.id)
  })
)
const canOpenSelectedProjectsInEditor = computed(() =>
  !isOpeningSelectedProjectsInEditor.value
  && hasSelection.value
  && selectedProjects.value.length === selectedProjectIds.value.size
  && selectedProjects.value.every(project => !project.locked && project.pageCount > 0)
)

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

function clearSelection() {
  selectedProjectIds.value = new Set()
}

watch(data, (projects) => {
  const validIds = new Set((projects ?? []).map(project => project.id))
  const filtered = Array.from(selectedProjectIds.value).filter(id => validIds.has(id))
  selectedProjectIds.value = new Set(filtered)

  for (const project of projects ?? []) {
    void collaborationPageSummary.ensureProjectSummary(project.id)
  }
}, { deep: true, immediate: true })

onMounted(() => {
  if (isStarredFilter.value) {
    setColumnFilter('isStarred', true)
  }
})

function renderProjectEditorsCell(project: ProjectListItem) {
  const editors = collaborationPageSummary.getProjectEditors(project.id)
  if (editors.length === 0) {
    return null
  }

  const visibleEditors = editors.slice(0, 4)
  const hiddenCount = Math.max(0, editors.length - visibleEditors.length)

  return h(UPopover, {
    mode: 'hover',
    content: { side: 'top' }
  }, {
    default: () => h('div', { class: 'flex items-center' }, [
      ...visibleEditors.map((entry, index) => h(UAvatar, {
        key: entry.editor.user.id,
        src: resolveManagedProfileAvatarSrc(entry.editor.user.avatar),
        alt: entry.editor.user.displayName,
        text: getAvatarInitials({
          name: entry.editor.user.displayName,
          username: entry.editor.user.username
        }),
        size: 'sm',
        class: `${index > 0 ? '-ml-2' : ''} ring-2 ${entry.isLive ? 'ring-emerald-400/90' : 'ring-neutral-400/90'}`
      })),
      hiddenCount > 0
        ? h('span', {
            class: 'ml-2 inline-flex min-w-5 items-center justify-center rounded-full bg-neutral-100 px-1.5 py-0.5 text-[10px] font-medium text-neutral-700 dark:bg-neutral-800 dark:text-neutral-200'
          }, `+${hiddenCount}`)
        : null
    ]),
    content: () => h('div', { class: 'p-3 w-64 space-y-2' }, [
      h('p', { class: 'text-xs font-medium text-highlighted' }, 'Active editors'),
      ...editors.map(entry => h('div', {
        key: entry.editor.user.id,
        class: 'flex items-start gap-2'
      }, [
        h(UAvatar, {
          src: resolveManagedProfileAvatarSrc(entry.editor.user.avatar),
          alt: entry.editor.user.displayName,
          text: getAvatarInitials({
            name: entry.editor.user.displayName,
            username: entry.editor.user.username
          }),
          size: 'xs',
          class: `mt-0.5 ring-2 ${entry.isLive ? 'ring-emerald-400/90' : 'ring-neutral-400/90'}`
        }),
        h('div', { class: 'min-w-0' }, [
          h('p', { class: 'text-xs font-medium text-highlighted truncate' }, entry.editor.user.displayName),
          h('p', { class: 'text-xs text-muted truncate' }, `${entry.isLive ? 'Live' : 'Idle'} on ${entry.pageIds.length} page${entry.pageIds.length === 1 ? '' : 's'}`)
        ])
      ]))
    ])
  })
}

const columns: TableColumn<ProjectListItem>[] = [
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
    header: createSortableHeader('Name', 'name', sort, UButton),
    cell: ({ row }) => h('div', { class: 'flex min-w-0 items-center gap-2' }, [
      row.original.locked ? h('span', { class: 'text-warning', title: row.original.lockedReason || 'Locked' }, h(UIcon, { name: 'i-lucide-lock', class: 'w-4 h-4' })) : null,
      h(NuxtLink, { to: `/project/${row.original.id}`, class: 'min-w-0 truncate font-medium hover:underline text-primary' }, () => row.getValue('name')),
      renderProjectEditorsCell(row.original)
    ])
  },
  {
    accessorKey: 'description',
    header: createSortableHeader('Description', 'description', sort, UButton),
    cell: ({ row }) => renderTruncatedText(row.getValue('description') as string)
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
    header: createSortableHeader('Pages', 'pageCount', sort, UButton, { align: 'end' }),
    cell: ({ row }) => h('div', { class: 'text-right font-medium' }, row.getValue('pageCount'))
  },
  {
    accessorKey: 'storageUsedBytes',
    header: createSortableHeader('Storage', 'storageUsedBytes', sort, UButton, { align: 'end' }),
    cell: ({ row }) => h('div', { class: 'text-right text-sm text-muted' }, row.original.storageUsedFormatted || '0 B')
  },
  {
    accessorKey: 'created',
    header: createSortableHeader('Created', 'created', sort, UButton)
  },
  {
    accessorKey: 'updated',
    header: createSortableHeader('Updated', 'updated', sort, UButton)
  },
  {
    id: 'actions',
    cell: ({ row }) => renderDropdownActionsCell(getRowItems(row), { UButton, UDropdownMenu })
  }
]

function handleRowClick(row: Row<ProjectListItem>) {
  navigateTo(`/project/${row.original.id}`)
}

function getProjectCapabilities(project: ProjectListItem) {
  return {
    ...DEFAULT_PROJECT_CAPABILITIES,
    ...(project.capabilities ?? {})
  }
}

function toEditableProject(project: ProjectListItem) {
  return {
    ...project,
    tagSetId: project.tagSetId ?? undefined
  }
}

async function handleDeleteProject(project: ProjectListItem): Promise<boolean> {
  const capabilities = getProjectCapabilities(project)
  if (!allow(capabilities.canDelete)) return false
  if (deletingProjectIds.value.has(project.id)) return false

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
  if (!confirmed) return false

  const projects = data.value ?? []
  const removedIndex = projects.findIndex(item => item.id === project.id)
  if (removedIndex === -1) return false

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
  if (!removedProject) return false
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
    void refreshNuxtData(projectsKey.value)
    return true
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
    return false
  } finally {
    toast.remove(progressToast.id)
    const nextDeleting = new Set(deletingProjectIds.value)
    nextDeleting.delete(project.id)
    deletingProjectIds.value = nextDeleting
  }
}

async function handleDeleteSelectedProjects() {
  if (!selectedWorkspace.value || !canDeleteSelectedProjects.value) return

  const projectsToDelete = [...selectedProjects.value]
  const ids = projectsToDelete.map(project => project.id)
  const count = projectsToDelete.length
  const instance = deleteSlideover.open({
    name: `${count} project${count === 1 ? '' : 's'}`,
    entityType: 'Project',
    items: projectsToDelete.map(project => ({ id: project.id, label: project.name })),
    warningDetails: [
      'All associated images and XML files',
      'All project history and annotations'
    ]
  })
  const confirmed = await instance.result
  if (!confirmed) return

  deletingProjectIds.value = new Set([...deletingProjectIds.value, ...ids])

  try {
    const response = await $fetch<{ successCount: number, failedCount: number }>(
      `/api/workspaces/${selectedWorkspace.value}/projects/bulk`,
      {
        method: 'DELETE',
        body: { ids }
      }
    )

    if (response.successCount > 0) {
      toast.add({
        title: response.successCount === 1 ? 'Project deleted' : 'Projects deleted',
        description: `${response.successCount} item${response.successCount === 1 ? '' : 's'} removed.`,
        color: 'success'
      })
    }

    if (response.failedCount > 0) {
      toast.add({
        title: 'Some deletions failed',
        description: `${response.failedCount} project${response.failedCount === 1 ? '' : 's'} could not be deleted.`,
        color: 'warning'
      })
    }

    clearSelection()
    await refreshNuxtData(projectsKey.value)
  } catch (error: unknown) {
    toast.add({
      title: 'Delete failed',
      description: extractApiErrorMessage(error, 'Failed to delete selected projects'),
      color: 'error'
    })
  } finally {
    const nextDeleting = new Set(deletingProjectIds.value)
    ids.forEach(id => nextDeleting.delete(id))
    deletingProjectIds.value = nextDeleting
  }
}

async function handleOpenSelectedProjectsInEditor() {
  if (!selectedWorkspace.value || !canOpenSelectedProjectsInEditor.value) return

  const projectsToOpen = [...selectedProjects.value]
  isOpeningSelectedProjectsInEditor.value = true

  try {
    const results = await Promise.allSettled(projectsToOpen.map(async (project) => {
      const pages = await $fetch<PageResponse[]>(`/api/projects/${project.id}/pages`)
      return {
        project,
        pages: naturalSortBy(createSkeletonPageData(pages, {
          projectId: project.id,
          projectName: project.name
        }), 'label')
      }
    }))

    const openableProjects = results.flatMap((result) => {
      if (result.status !== 'fulfilled') return []
      return result.value.pages.length > 0 ? [result.value] : []
    })

    if (openableProjects.length === 0) {
      toast.add({
        title: 'No projects opened',
        description: 'No selected project pages could be loaded.',
        color: 'warning'
      })
      return
    }

    editorStore.resetEditorState()
    sessionStore.clearSession({ preserveTextViewSettings: true })
    sessionStore.initWorkspaceSession(selectedWorkspace.value)

    const firstProject = openableProjects[0]
    const firstPage = firstProject?.pages[0]
    let openedPageCount = 0

    for (const { project, pages } of openableProjects) {
      sessionStore.addOpenedProject(project.id)
      editorStore.setProjectPages(project.id, pages, { replaceProject: true })
      const firstProjectPage = pages[0]
      if (firstProjectPage) {
        sessionStore.setActivePage(project.id, firstProjectPage.id)
      }
      openedPageCount += pages.length
    }

    if (firstProject && firstPage) {
      sessionStore.setActiveProject(firstProject.project.id)
      sessionStore.setActivePage(firstProject.project.id, firstPage.id)
    }

    const failedCount = results.filter(result => result.status === 'rejected').length
    const skippedEmptyCount = results.filter(result => result.status === 'fulfilled' && result.value.pages.length === 0).length

    if (failedCount > 0 || skippedEmptyCount > 0) {
      toast.add({
        title: 'Some projects were skipped',
        description: `${openableProjects.length} project${openableProjects.length === 1 ? '' : 's'} opened with ${openedPageCount} page${openedPageCount === 1 ? '' : 's'}.`,
        color: 'warning'
      })
    }

    await navigateTo('/editor')
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to open editor',
      description: extractApiErrorMessage(error, 'Could not prepare selected projects for the editor.'),
      color: 'error'
    })
  } finally {
    isOpeningSelectedProjectsInEditor.value = false
  }
}

async function openEditProjectSlideover(project: ProjectListItem) {
  const capabilities = getProjectCapabilities(project)
  if (!allow(capabilities.canEdit)) return

  const instance = editSlideover.open({ project: toEditableProject(project) })
  const updated = await instance.result
  if (!updated) return

  await refreshNuxtData(projectsKey.value)
}

async function openShareSlideover(project: ProjectListItem) {
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

  await refreshNuxtData(projectsKey.value)
}

function getRowItems(row: Row<ProjectListItem>) {
  const capabilities = getProjectCapabilities(row.original)
  const groups: DropdownMenuItem[][] = [[
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

  const mutationActions: DropdownMenuItem[] = []
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

const contextMenuRow = ref<Row<ProjectListItem> | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuRow.value) return []
  return getRowItems(contextMenuRow.value)
})

function handleRowContextMenu(_event: Event, row: { original: Record<string, unknown> }) {
  contextMenuRow.value = row as Row<ProjectListItem>
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

function triggerLegacyOcr4allImport() {
  importLegacyOcr4allInput.value?.click()
}

const projectsActionItems = computed<DropdownMenuItem[][]>(() => [[
  {
    label: 'Import Package',
    icon: 'i-lucide-file-up',
    disabled: !selectedWorkspace.value,
    onSelect: triggerProjectPackageImport
  },
  {
    label: 'Import OCR4all project',
    icon: 'i-lucide-folder-up',
    disabled: !selectedWorkspace.value,
    onSelect: triggerLegacyOcr4allImport
  }
]])

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

    const result = await uploadFormDataWithProgress<{ projectName?: string }>({
      title: 'Importing project package',
      workspaceId: selectedWorkspace.value,
      files: [{ file }],
      url: `/api/upload-proxy/workspaces/${selectedWorkspace.value}/projects/import-package`,
      formData
    })
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

async function handleLegacyOcr4allImport(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length === 0 || !selectedWorkspace.value) {
    input.value = ''
    return
  }

  try {
    const formData = new FormData()
    const firstRelativePath = files.find(file => file.webkitRelativePath)?.webkitRelativePath ?? files[0]?.name ?? ''
    const projectName = firstRelativePath.split('/').filter(Boolean)[0]

    if (projectName) {
      formData.append('projectName', projectName)
    }

    for (const file of files) {
      const relativePath = file.webkitRelativePath || file.name
      formData.append('files', file, relativePath)
      formData.append('paths', relativePath)
    }

    const result = await uploadFormDataWithProgress<{ projectName?: string, pageCount?: number }>({
      title: 'Importing OCR4all project',
      workspaceId: selectedWorkspace.value,
      files: files.map(file => ({
        file,
        fileName: file.webkitRelativePath || file.name
      })),
      url: `/api/upload-proxy/workspaces/${selectedWorkspace.value}/projects/import-legacy-ocr4all`,
      formData
    })
    toast.add({
      title: 'OCR4all project imported',
      description: result.projectName
        ? `Created "${result.projectName}"${result.pageCount ? ` with ${result.pageCount} page${result.pageCount === 1 ? '' : 's'}` : ''}`
        : undefined,
      color: 'success',
      icon: 'i-lucide-folder-up'
    })

    await refresh()
  } catch (error: unknown) {
    const message = extractApiErrorMessage(error, 'Failed to import OCR4all project')
    toast.add({
      title: 'OCR4all import failed',
      description: message,
      color: 'error'
    })
  } finally {
    input.value = ''
  }
}
</script>

<template>
  <UDashboardPanel id="projects">
    <template #header>
      <UDashboardNavbar title="Projects">
        <template #right>
          <input
            ref="importProjectPackageInput"
            type="file"
            class="hidden"
            accept=".zip,.larex-project.zip,application/zip,application/octet-stream"
            @change="handleProjectPackageImport"
          >
          <input
            ref="importLegacyOcr4allInput"
            type="file"
            class="hidden"
            multiple
            webkitdirectory
            directory
            @change="handleLegacyOcr4allImport"
          >
          <UFieldGroup>
            <UButton
              v-if="canCreateProjects"
              label="New Project"
              color="neutral"
              variant="outline"
              icon="i-lucide-package-plus"
              @click="projectSlideoverCreate.open()"
            />
            <UDropdownMenu :items="projectsActionItems" :content="{ align: 'end' }">
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
            :color="columnFilters['isStarred'] ? 'warning' : 'neutral'"
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
          <AppTableClearFiltersButton
            :active="activeFilters.length > 0"
            @clear="clearProjectFilters"
          />
        </template>
        <template #right>
          <AppTableColumnsDropdown
            table-id="dashboard-projects-v2"
            :columns="columns"
            :default-visible-column-ids="DEFAULT_PROJECTS_VISIBLE_COLUMN_IDS"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="error" class="py-8 text-center">
        <div class="flex items-center justify-center gap-2 text-error">
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

      <template v-else>
        <UEmpty
          v-if="data && data.length === 0"
          variant="naked"
          icon="i-lucide-book"
          title="No projects found"
          description="It looks like you haven't added any projects. Create one to get started."
          :actions="emptyActions as any"
        />

        <div v-else-if="data">
          <UEmpty
            v-if="filteredAndSortedData.length === 0 && activeFilters.length > 0"
            variant="naked"
            icon="i-lucide-search-x"
            title="No projects match your filters"
            description="Try adjusting or clearing your filters to see more results."
            :actions="[{
              icon: 'i-lucide-x',
              label: 'Clear filters',
              color: 'neutral',
              variant: 'subtle',
              onClick: clearProjectFilters
            }]"
          />

          <UContextMenu v-else :items="contextMenuItems as any">
            <AppTable
              table-id="dashboard-projects-v2"
              :data="paginatedData"
              :columns="columns"
              :default-visible-column-ids="DEFAULT_PROJECTS_VISIBLE_COLUMN_IDS"
              class="flex-1"
              @row-click="handleRowClick"
              @contextmenu="handleRowContextMenu"
            />
          </UContextMenu>

          <div v-if="totalItems > 0" class="flex justify-between items-center p-4 border-t border-neutral-200 dark:border-neutral-800">
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
                :disabled="totalPages <= 1"
                show-edges
                :sibling-count="1"
              />
            </div>
          </div>
        </div>

        <UiFloatingSelectionMenu
          :selected-count="selectedProjectIds.size"
          @clear="clearSelection"
        >
          <UButton
            v-if="hasSelection"
            icon="i-lucide-pencil"
            color="neutral"
            variant="ghost"
            size="sm"
            class="text-neutral-50 hover:bg-white/10"
            :loading="isOpeningSelectedProjectsInEditor"
            :disabled="!canOpenSelectedProjectsInEditor"
            @click="handleOpenSelectedProjectsInEditor"
          >
            Open in Editor
          </UButton>
          <UButton
            icon="i-lucide-wand-sparkles"
            color="neutral"
            variant="ghost"
            size="sm"
            class="text-neutral-50 hover:bg-white/10"
            @click="openCodecGenerateSlideover"
          >
            Generate Codec
          </UButton>
          <UButton
            icon="i-lucide-badge-check"
            color="neutral"
            variant="ghost"
            size="sm"
            class="text-neutral-50 hover:bg-white/10"
            @click="openCodecValidateSlideover"
          >
            Validate Codec
          </UButton>
          <UButton
            icon="i-lucide-trash"
            color="error"
            variant="ghost"
            size="sm"
            class="hover:bg-white/10"
            :disabled="!canDeleteSelectedProjects"
            @click="handleDeleteSelectedProjects"
          >
            Delete
          </UButton>
        </UiFloatingSelectionMenu>
      </template>
    </template>
  </UDashboardPanel>
</template>
