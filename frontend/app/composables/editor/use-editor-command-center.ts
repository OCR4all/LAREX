import { computed, ref, watch } from 'vue'
import { useGlobalSearch } from '@/composables/use-global-search'

type DashboardSearchItem = {
  id: string
  label: string
  icon?: string
  suffix?: string
  description?: string
  to?: string
  class?: string
  disabled?: boolean
  kbds?: string[]
  onSelect?: () => void | Promise<void>
}

type DashboardSearchGroup = {
  id: string
  label: string
  items: DashboardSearchItem[]
  ignoreFilter?: boolean
}

type WorkspaceProjectResponse = {
  id: string
  name: string
  pageCount?: number
}

type ProjectPageResponse = {
  id: string
  name: string
}

export type EditorWorkspaceProjectIndexItem = {
  id: string
  name: string
  pageCount: number
}

export type EditorWorkspacePageIndexItem = {
  id: string
  name: string
  projectId: string
  projectName: string
}

type EditorWorkspaceIndex = {
  projects: EditorWorkspaceProjectIndexItem[]
  pages: EditorWorkspacePageIndexItem[]
}

type OpenSelection = {
  projectId: string
  projectName: string
  pageIds: string[] | null
}

type EditorCommandCenterOptions = {
  openProjectModal: () => Promise<void>
  openProjectSelection: (selection: OpenSelection, source: 'project-search' | 'page-search') => Promise<void>
  onNavigateAway?: () => void
}

const MAX_PROJECT_RESULTS = 20
const MAX_PAGE_RESULTS = 20
const PROJECT_PAGE_FETCH_CONCURRENCY = 6

const workspaceIndexCache = new Map<string, EditorWorkspaceIndex>()
const workspaceIndexPromises = new Map<string, Promise<EditorWorkspaceIndex>>()

function normalizeQuery(value: string) {
  return value.trim().toLowerCase()
}

function searchMatch(haystack: string, query: string) {
  return haystack.toLowerCase().includes(query)
}

export function useEditorCommandCenter(options: EditorCommandCenterOptions) {
  const workspaceStore = useWorkspaceStore()
  const { startCurrentPageTour } = useOnboarding()

  const open = ref(false)
  const searchTerm = ref('')
  const indexLoading = ref(false)

  const indexedProjects = ref<EditorWorkspaceProjectIndexItem[]>([])
  const indexedPages = ref<EditorWorkspacePageIndexItem[]>([])

  const workspaceId = computed<string | null>(() => {
    const id = workspaceStore.selectedWorkspaceId
    return typeof id === 'string' && id.length > 0 ? id : null
  })

  const {
    searchTerm: globalSearchTerm,
    isSearching,
    resultGroups
  } = useGlobalSearch({
    onSelectResult: () => {
      closeCommandCenter()
    }
  })

  const normalizedSearchTerm = computed(() => normalizeQuery(searchTerm.value))

  async function navigateToDashboard(path: string) {
    options.onNavigateAway?.()
    closeCommandCenter()
    await navigateTo(path)
  }

  function closeCommandCenter() {
    open.value = false
  }

  function openCommandCenter() {
    open.value = true
  }

  async function handleOpenProjectModal() {
    await options.openProjectModal()
    closeCommandCenter()
  }

  async function handleOpenSelection(selection: OpenSelection, source: 'project-search' | 'page-search') {
    await options.openProjectSelection(selection, source)
    closeCommandCenter()
  }

  async function fetchWorkspaceIndex(targetWorkspaceId: string): Promise<EditorWorkspaceIndex> {
    const cached = workspaceIndexCache.get(targetWorkspaceId)
    if (cached) return cached

    const pending = workspaceIndexPromises.get(targetWorkspaceId)
    if (pending) return pending

    const promise = (async () => {
      const projectsResponse = await $fetch<WorkspaceProjectResponse[]>(`/api/workspaces/${targetWorkspaceId}/projects`)
      const projects = (projectsResponse ?? []).map(project => ({
        id: project.id,
        name: project.name,
        pageCount: Number(project.pageCount ?? 0)
      }))

      const pages: EditorWorkspacePageIndexItem[] = []
      let cursor = 0

      const workers = Array.from({ length: Math.min(PROJECT_PAGE_FETCH_CONCURRENCY, Math.max(projects.length, 1)) }, () => (async () => {
        while (cursor < projects.length) {
          const index = cursor
          cursor += 1
          const project = projects[index]
          if (!project) continue

          try {
            const projectPages = await $fetch<ProjectPageResponse[]>(`/api/projects/${project.id}/pages`)
            for (const page of projectPages ?? []) {
              pages.push({
                id: page.id,
                name: page.name,
                projectId: project.id,
                projectName: project.name
              })
            }
          } catch {
            continue
          }
        }
      })())

      await Promise.all(workers)

      const result: EditorWorkspaceIndex = {
        projects,
        pages
      }

      workspaceIndexCache.set(targetWorkspaceId, result)
      return result
    })()
      .finally(() => {
        workspaceIndexPromises.delete(targetWorkspaceId)
      })

    workspaceIndexPromises.set(targetWorkspaceId, promise)
    return promise
  }

  async function ensureWorkspaceIndex() {
    const id = workspaceId.value
    if (!id) {
      indexedProjects.value = []
      indexedPages.value = []
      return
    }

    indexLoading.value = true
    try {
      const index = await fetchWorkspaceIndex(id)
      if (workspaceId.value !== id) return

      indexedProjects.value = index.projects
      indexedPages.value = index.pages
    } catch {
      indexedProjects.value = []
      indexedPages.value = []
    } finally {
      if (workspaceId.value === id) {
        indexLoading.value = false
      }
    }
  }

  watch(searchTerm, (value) => {
    globalSearchTerm.value = value
  })

  watch(workspaceId, () => {
    indexedProjects.value = []
    indexedPages.value = []
  })

  watch(
    () => [open.value, workspaceId.value, normalizedSearchTerm.value] as const,
    ([isOpen, id, query]) => {
      if (!isOpen || !id || query.length < 2) return
      void ensureWorkspaceIndex()
    },
    { immediate: true }
  )

  defineShortcuts({
    meta_k: {
      usingInput: true,
      handler: () => {
        openCommandCenter()
      }
    },
    ctrl_k: {
      usingInput: true,
      handler: () => {
        openCommandCenter()
      }
    }
  })

  const editorActionGroup = computed<DashboardSearchGroup>(() => ({
    id: 'editor-actions',
    label: 'Editor Actions',
    items: [{
      id: 'editor-open-project-pages',
      label: 'Open Projects & Pages',
      icon: 'i-lucide-folder-search',
      suffix: 'Open existing projects/pages in this editor session',
      onSelect: () => {
        void handleOpenProjectModal()
      }
    }, {
      id: 'editor-open-dictionaries',
      label: 'Open Dictionaries',
      icon: 'i-lucide-book-copy',
      suffix: 'Manage controlled dictionaries',
      onSelect: () => {
        void navigateToDashboard('/dictionaries')
      }
    }, {
      id: 'editor-start-current-tour',
      label: 'Start Current Page Tour',
      icon: 'i-lucide-compass',
      suffix: 'Interactive walkthrough for this page',
      onSelect: () => {
        closeCommandCenter()
        void startCurrentPageTour()
      }
    }],
    ignoreFilter: true
  }))

  const dashboardNavigationGroup = computed<DashboardSearchGroup>(() => ({
    id: 'dashboard-navigation',
    label: 'Navigation',
    items: [{
      id: 'go-projects',
      label: 'Go to Projects',
      icon: 'i-lucide-library',
      suffix: 'Browse your projects',
      kbds: ['G', 'H'],
      to: '/',
      onSelect: () => {
        void navigateToDashboard('/')
      }
    }, {
      id: 'go-tasks',
      label: 'Go to Tasks',
      icon: 'i-lucide-clipboard-list',
      suffix: 'View and manage tasks',
      kbds: ['G', 'T'],
      to: '/tasks',
      onSelect: () => {
        void navigateToDashboard('/tasks')
      }
    }, {
      id: 'go-labels',
      label: 'Go to Labels',
      icon: 'i-lucide-tags',
      suffix: 'Manage label sets',
      kbds: ['G', 'L'],
      to: '/labels',
      onSelect: () => {
        void navigateToDashboard('/labels')
      }
    }, {
      id: 'go-tags',
      label: 'Go to Tags',
      icon: 'i-lucide-network',
      suffix: 'Manage tag sets',
      to: '/tag-sets',
      onSelect: () => {
        void navigateToDashboard('/tag-sets')
      }
    }, {
      id: 'go-virtual-keyboards',
      label: 'Go to Virtual Keyboards',
      icon: 'i-lucide-keyboard',
      suffix: 'Manage virtual keyboards',
      to: '/virtual-keyboard',
      onSelect: () => {
        void navigateToDashboard('/virtual-keyboard')
      }
    }, {
      id: 'go-dictionaries',
      label: 'Go to Dictionaries',
      icon: 'i-lucide-book-copy',
      suffix: 'Manage controlled dictionaries',
      to: '/dictionaries',
      onSelect: () => {
        void navigateToDashboard('/dictionaries')
      }
    }, {
      id: 'go-codecs',
      label: 'Go to Codecs',
      icon: 'i-lucide-case-lower',
      suffix: 'Manage codecs',
      to: '/codecs',
      onSelect: () => {
        void navigateToDashboard('/codecs')
      }
    }, {
      id: 'go-settings',
      label: 'Go to Settings',
      icon: 'i-lucide-settings',
      suffix: 'Profile and preferences',
      kbds: ['G', 'S'],
      to: '/settings',
      onSelect: () => {
        void navigateToDashboard('/settings')
      }
    }, {
      id: 'go-workspace-settings',
      label: 'Go to Workspace Settings',
      icon: 'i-lucide-layers',
      suffix: 'Workspace configuration',
      to: '/workspace/settings',
      onSelect: () => {
        void navigateToDashboard('/workspace/settings')
      }
    }, {
      id: 'go-members',
      label: 'Go to Members',
      icon: 'i-lucide-users',
      suffix: 'Manage workspace members',
      to: '/workspace/settings/members',
      onSelect: () => {
        void navigateToDashboard('/workspace/settings/members')
      }
    }],
    ignoreFilter: true
  }))

  const openProjectsGroup = computed<DashboardSearchGroup | null>(() => {
    const q = normalizedSearchTerm.value
    if (q.length < 2) return null

    const items = indexedProjects.value
      .filter(project => searchMatch(project.name, q))
      .slice(0, MAX_PROJECT_RESULTS)
      .map(project => ({
        id: `editor-project-${project.id}`,
        label: project.name,
        icon: 'i-lucide-folder',
        suffix: `${project.pageCount} pages`,
        onSelect: () => {
          void handleOpenSelection({
            projectId: project.id,
            projectName: project.name,
            pageIds: null
          }, 'project-search')
        }
      }))

    if (items.length === 0) return null

    return {
      id: 'editor-open-projects',
      label: `Open Projects (${items.length})`,
      items,
      ignoreFilter: true
    }
  })

  const openPagesGroup = computed<DashboardSearchGroup | null>(() => {
    const q = normalizedSearchTerm.value
    if (q.length < 2) return null

    const items = indexedPages.value
      .filter(page => searchMatch(page.name, q) || searchMatch(page.projectName, q))
      .slice(0, MAX_PAGE_RESULTS)
      .map(page => ({
        id: `editor-page-${page.projectId}-${page.id}`,
        label: page.name,
        icon: 'i-lucide-file-image',
        suffix: page.projectName,
        onSelect: () => {
          void handleOpenSelection({
            projectId: page.projectId,
            projectName: page.projectName,
            pageIds: [page.id]
          }, 'page-search')
        }
      }))

    if (items.length === 0) return null

    return {
      id: 'editor-open-pages',
      label: `Open Pages (${items.length})`,
      items,
      ignoreFilter: true
    }
  })

  const groups = computed<DashboardSearchGroup[]>(() => {
    const baseGroups: DashboardSearchGroup[] = [
      editorActionGroup.value,
      dashboardNavigationGroup.value
    ]

    if (normalizedSearchTerm.value.length < 2) {
      return baseGroups
    }

    const searchGroups: DashboardSearchGroup[] = []
    if (openProjectsGroup.value) searchGroups.push(openProjectsGroup.value)
    if (openPagesGroup.value) searchGroups.push(openPagesGroup.value)

    return [...searchGroups, ...resultGroups.value as DashboardSearchGroup[], ...baseGroups]
  })

  const isLoading = computed(() => {
    const shouldLoadIndex = open.value && normalizedSearchTerm.value.length >= 2 && workspaceId.value !== null
    return isSearching.value || (shouldLoadIndex && indexLoading.value)
  })

  return {
    open,
    searchTerm,
    isLoading,
    groups,
    openCommandCenter,
    closeCommandCenter
  }
}
