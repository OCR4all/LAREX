type GlobalSearchItem = {
  id: string
  label: string
  icon?: string
  suffix?: string
  description?: string
  to?: string
  class?: string
  disabled?: boolean
  onSelect?: () => void
}

type GlobalSearchGroup = {
  id: string
  label: string
  items: GlobalSearchItem[]
  ignoreFilter?: boolean
}

type GlobalSearchCacheEntry<T> = {
  createdAtMs: number
  value: T
}

type ProjectSearchResult = {
  id: string
  name: string
  workspaceName?: string
  description?: string | null
  pageCount?: number
  isStarred?: boolean
}

type LabelSetSearchResult = {
  id: string
  name?: string
  meta?: { name?: string }
  labelCount: number
}

type TagSetSearchResult = {
  id: string
  name?: string
  meta?: { name?: string }
  tagCount: number
}

type CodecSearchResult = {
  id: string
  name: string
  characterCount: number
}

type DictionarySearchResult = {
  id: string
  name: string
  entryCount: number
}

type VirtualKeyboardSearchResult = {
  id: string
  name?: string
  description?: string | null
}

type GlobalSearchRawResults = {
  projects: ProjectSearchResult[]
  labelSets: LabelSetSearchResult[]
  tagSets: TagSetSearchResult[]
  codecs: CodecSearchResult[]
  dictionaries: DictionarySearchResult[]
  virtualKeyboards: VirtualKeyboardSearchResult[]
}

const SEARCH_CACHE_TTL_MS = 15_000

const rawResultsCache = new Map<string, GlobalSearchCacheEntry<GlobalSearchRawResults>>()
const virtualKeyboardListCache = new Map<string, GlobalSearchCacheEntry<VirtualKeyboardSearchResult[]>>()

function isFresh(entry: GlobalSearchCacheEntry<unknown> | undefined) {
  return !!entry && Date.now() - entry.createdAtMs < SEARCH_CACHE_TTL_MS
}

function isAbortError(e: unknown): boolean {
  return typeof e === 'object' && e !== null && 'name' in e && (e as { name?: unknown }).name === 'AbortError'
}

export function useGlobalSearch(options?: { onSelectResult?: () => void }) {
  const workspace = useWorkspaceStore()

  const searchTerm = ref('')
  const isSearching = ref(false)
  const searchError = ref<string | null>(null)
  const hasSearched = ref(false)

  const rawResults = ref<GlobalSearchRawResults>({
    projects: [],
    labelSets: [],
    tagSets: [],
    codecs: [],
    dictionaries: [],
    virtualKeyboards: []
  })

  let activeAbortController: AbortController | null = null
  let activeRequestId = 0

  function clearResults() {
    rawResults.value = {
      projects: [],
      labelSets: [],
      tagSets: [],
      codecs: [],
      dictionaries: [],
      virtualKeyboards: []
    }
  }

  async function getVirtualKeyboards(wsId: string, signal: AbortSignal) {
    const cached = virtualKeyboardListCache.get(wsId)
    if (isFresh(cached)) return cached!.value

    const list = await $fetch<VirtualKeyboardSearchResult[]>(`/api/workspaces/${wsId}/virtual-keyboards`, { signal }).catch(() => [])
    virtualKeyboardListCache.set(wsId, { createdAtMs: Date.now(), value: list })
    return list
  }

  async function runSearch(query: string) {
    const q = query.trim()
    const wsId = typeof workspace.selectedWorkspaceId === 'string' ? workspace.selectedWorkspaceId : null
    const cacheKey = `${wsId ?? 'no-workspace'}::${q.toLowerCase()}`

    const cached = rawResultsCache.get(cacheKey)
    if (isFresh(cached)) {
      rawResults.value = cached!.value
      searchError.value = null
      isSearching.value = false
      hasSearched.value = true
      return
    }

    if (activeAbortController) {
      activeAbortController.abort()
    }

    const requestId = ++activeRequestId
    const abortController = new AbortController()
    activeAbortController = abortController

    isSearching.value = true
    searchError.value = null
    hasSearched.value = true

    try {
      const [projectRes, labelSetRes, tagSetRes, codecRes, dictionaryRes, vkRes] = await Promise.all([
        $fetch<{ projects: ProjectSearchResult[] }>(`/api/search?q=${encodeURIComponent(q)}&limit=10`, { signal: abortController.signal }).catch(() => null),
        wsId ? $fetch<LabelSetSearchResult[]>(`/api/workspaces/${wsId}/label-sets?search=${encodeURIComponent(q)}`, { signal: abortController.signal }).catch(() => []) : Promise.resolve([]),
        wsId ? $fetch<TagSetSearchResult[]>(`/api/workspaces/${wsId}/tag-sets?search=${encodeURIComponent(q)}`, { signal: abortController.signal }).catch(() => []) : Promise.resolve([]),
        wsId ? $fetch<CodecSearchResult[]>(`/api/workspaces/${wsId}/codecs?search=${encodeURIComponent(q)}`, { signal: abortController.signal }).catch(() => []) : Promise.resolve([]),
        wsId ? $fetch<DictionarySearchResult[]>(`/api/workspaces/${wsId}/dictionaries?search=${encodeURIComponent(q)}`, { signal: abortController.signal }).catch(() => []) : Promise.resolve([]),
        wsId ? getVirtualKeyboards(wsId, abortController.signal) : Promise.resolve([])
      ])

      if (requestId !== activeRequestId) return

      const lowerQ = q.toLowerCase()
      const normalized: GlobalSearchRawResults = {
        projects: projectRes?.projects || [],
        labelSets: labelSetRes || [],
        tagSets: tagSetRes || [],
        codecs: codecRes || [],
        dictionaries: dictionaryRes || [],
        virtualKeyboards: (vkRes || []).filter(vk =>
          vk.name?.toLowerCase().includes(lowerQ) || vk.description?.toLowerCase().includes(lowerQ)
        )
      }

      rawResults.value = normalized
      rawResultsCache.set(cacheKey, { createdAtMs: Date.now(), value: normalized })
    } catch (e: unknown) {
      if (requestId !== activeRequestId) return
      if (isAbortError(e)) return
      clearResults()
      searchError.value = 'Search failed'
    } finally {
      if (requestId === activeRequestId) {
        isSearching.value = false
      }
    }
  }

  const debouncedRunSearch = useDebounceFn((q: string) => {
    void runSearch(q)
  }, 300)

  watch(searchTerm, (newQuery) => {
    const q = newQuery?.trim() || ''
    if (q.length < 2) {
      if (activeAbortController) activeAbortController.abort()
      isSearching.value = false
      searchError.value = null
      hasSearched.value = false
      clearResults()
      return
    }

    debouncedRunSearch(q)
  })

  function wrapSelect(handler?: () => void) {
    return () => {
      handler?.()
      options?.onSelectResult?.()
    }
  }

  const resultGroups = computed<GlobalSearchGroup[]>(() => {
    const q = searchTerm.value.trim()
    if (q.length < 2) return []

    if (searchError.value) {
      return [{
        id: 'search-error',
        label: 'Search',
        ignoreFilter: true,
        items: [{
          id: 'search-error-item',
          label: searchError.value,
          icon: 'i-lucide-alert-triangle',
          disabled: true
        }]
      }]
    }

    const projects = rawResults.value.projects.map(project => ({
      id: `project-${project.id}`,
      label: project.name,
      suffix: project.workspaceName,
      description: project.description || `${project.pageCount ?? 0} pages`,
      icon: 'i-lucide-star',
      to: `/project/${project.id}`,
      class: project.isStarred ? 'star-filled' : 'star-outline',
      onSelect: wrapSelect()
    }))

    const labelSets = rawResults.value.labelSets.map(ls => ({
      id: `label-set-${ls.id}`,
      label: ls.meta?.name || ls.name || 'Untitled label set',
      suffix: `${ls.labelCount} labels`,
      icon: 'i-lucide-tags',
      to: `/labels/${ls.id}`,
      onSelect: wrapSelect()
    }))

    const tagSets = rawResults.value.tagSets.map(ts => ({
      id: `tag-set-${ts.id}`,
      label: ts.meta?.name || ts.name || 'Untitled tag set',
      suffix: `${ts.tagCount} tags`,
      icon: 'i-lucide-network',
      to: `/tag-sets/${ts.id}`,
      onSelect: wrapSelect()
    }))

    const codecs = rawResults.value.codecs.map(c => ({
      id: `codec-${c.id}`,
      label: c.name,
      suffix: `${c.characterCount} characters`,
      icon: 'i-lucide-case-lower',
      to: `/codecs/${c.id}`,
      onSelect: wrapSelect()
    }))

    const dictionaries = rawResults.value.dictionaries.map(dictionary => ({
      id: `dictionary-${dictionary.id}`,
      label: dictionary.name,
      suffix: `${dictionary.entryCount} forms`,
      icon: 'i-lucide-book-copy',
      to: `/dictionaries/${dictionary.id}`,
      onSelect: wrapSelect()
    }))

    const virtualKeyboards = rawResults.value.virtualKeyboards.map(vk => ({
      id: `vk-${vk.id}`,
      label: vk.name || 'Untitled keyboard',
      suffix: vk.description || 'Virtual keyboard',
      icon: 'i-lucide-keyboard',
      to: `/virtual-keyboard/${vk.id}`,
      onSelect: wrapSelect()
    }))

    const groups: GlobalSearchGroup[] = []
    if (projects.length > 0) groups.push({ id: 'projects', label: `Projects (${projects.length})`, items: projects, ignoreFilter: true })
    if (labelSets.length > 0) groups.push({ id: 'label-sets', label: `Label Sets (${labelSets.length})`, items: labelSets, ignoreFilter: true })
    if (tagSets.length > 0) groups.push({ id: 'tag-sets', label: `Tag Sets (${tagSets.length})`, items: tagSets, ignoreFilter: true })
    if (codecs.length > 0) groups.push({ id: 'codecs', label: `Codecs (${codecs.length})`, items: codecs, ignoreFilter: true })
    if (dictionaries.length > 0) groups.push({ id: 'dictionaries', label: `Dictionaries (${dictionaries.length})`, items: dictionaries, ignoreFilter: true })
    if (virtualKeyboards.length > 0) groups.push({ id: 'virtual-keyboards', label: `Virtual Keyboards (${virtualKeyboards.length})`, items: virtualKeyboards, ignoreFilter: true })

    if (groups.length === 0 && hasSearched.value && !isSearching.value) {
      return [{
        id: 'search-empty',
        label: 'Search',
        ignoreFilter: true,
        items: [{
          id: 'search-empty-item',
          label: `No results for “${q}”`,
          icon: 'i-lucide-search-x',
          disabled: true
        }]
      }]
    }

    return groups
  })

  return {
    searchTerm,
    isSearching,
    resultGroups
  }
}
