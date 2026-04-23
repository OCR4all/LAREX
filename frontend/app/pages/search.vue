<script setup lang="ts">
import { useVirtualizer, type VirtualItem } from '@tanstack/vue-virtual'
import type { WorkspaceTextSearchClusterGroup, WorkspaceTextSearchHit, WorkspaceTextSearchResponse } from '@/types/search'

type SearchListRow = WorkspaceTextSearchHit | WorkspaceTextSearchClusterGroup

const PAGE_SIZE = 20
const OVERSCAN = 8

const route = useRoute()
const router = useRouter()
const { selectedWorkspace } = await useWorkspaceBootstrap()

const viewTabItems = [
  { label: 'Hits', value: 'hits', icon: 'i-lucide-search' },
  { label: 'Clustered', value: 'clustered', icon: 'i-lucide-folders' }
]

const matchTabItems = [
  { label: 'Auto', value: 'auto', icon: 'i-lucide-wand-sparkles' },
  { label: 'Exact', value: 'exact', icon: 'i-lucide-align-justify' },
  { label: 'Fuzzy', value: 'fuzzy', icon: 'i-lucide-scan-search' }
]

function getStringQueryValue(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim().length > 0 ? value : fallback
}

function getHitKey(hit: WorkspaceTextSearchHit) {
  return `${hit.projectId}:${hit.pageId}:${hit.textLineId || hit.regionId || 'page'}`
}

function isClusterRow(row: SearchListRow): row is WorkspaceTextSearchClusterGroup {
  return 'pages' in row
}

const searchInput = ref(getStringQueryValue(route.query.q, ''))

watch(() => getStringQueryValue(route.query.q, ''), (value) => {
  if (value !== searchInput.value) {
    searchInput.value = value
  }
})

const viewMode = computed<'hits' | 'clustered'>(() => {
  const value = getStringQueryValue(route.query.view, 'hits')
  return value === 'clustered' || value === 'projects' ? 'clustered' : 'hits'
})

const matchMode = computed<'auto' | 'exact' | 'fuzzy'>(() => {
  const value = getStringQueryValue(route.query.match, 'auto')
  return value === 'exact' || value === 'fuzzy' ? value : 'auto'
})

const searchQuery = computed(() => searchInput.value.trim())
const workspaceId = computed(() => selectedWorkspace.value ?? null)
const canSearch = computed(() => Boolean(workspaceId.value) && searchQuery.value.length >= 2)

const loadedHits = ref<WorkspaceTextSearchHit[]>([])
const totalHits = ref(0)
const totalProjectCount = ref(0)
const fuzzyExpanded = ref(false)
const suggestedQuery = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const hasLoaded = ref(false)
const isLoadingInitial = ref(false)
const isLoadingMore = ref(false)
const hasReachedEnd = ref(false)

let activeLoadToken = 0

async function setView(value: 'hits' | 'clustered') {
  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      view: value === 'hits' ? undefined : value
    }
  })
}

async function setMatch(value: 'auto' | 'exact' | 'fuzzy') {
  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      match: value === 'auto' ? undefined : value
    }
  })
}

const syncQueryInput = useDebounceFn(async () => {
  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      q: searchInput.value.trim() || undefined
    }
  })
}, 250)

watch(searchInput, () => {
  void syncQueryInput()
})

function resetResults() {
  loadedHits.value = []
  totalHits.value = 0
  totalProjectCount.value = 0
  fuzzyExpanded.value = false
  suggestedQuery.value = null
  errorMessage.value = null
  hasLoaded.value = false
  isLoadingInitial.value = false
  isLoadingMore.value = false
  hasReachedEnd.value = false
}

async function fetchSearchPage(append: boolean) {
  if (!canSearch.value || !workspaceId.value) {
    resetResults()
    hasLoaded.value = true
    return
  }

  if (append) {
    if (isLoadingMore.value || isLoadingInitial.value) return
    if (loadedHits.value.length >= totalHits.value && hasLoaded.value) return
  } else if (isLoadingInitial.value) {
    return
  }

  const token = activeLoadToken
  const nextOffset = append ? loadedHits.value.length : 0

  if (append) {
    isLoadingMore.value = true
  } else {
    isLoadingInitial.value = true
    errorMessage.value = null
  }

  try {
    const response = await $fetch<WorkspaceTextSearchResponse>(
      `/api/search/workspace/${workspaceId.value}/text`,
      {
        query: {
          q: searchQuery.value,
          limit: PAGE_SIZE,
          offset: nextOffset,
          view: 'hits',
          match: matchMode.value
        }
      }
    )

    if (token !== activeLoadToken) return

    const incomingHits = response.hits ?? []
    if (append) {
      const existingKeys = new Set(loadedHits.value.map(getHitKey))
      const uniqueIncomingHits = incomingHits.filter(hit => !existingKeys.has(getHitKey(hit)))
      loadedHits.value = [
        ...loadedHits.value,
        ...uniqueIncomingHits
      ]
      hasReachedEnd.value = incomingHits.length < PAGE_SIZE
        || uniqueIncomingHits.length === 0
    } else {
      loadedHits.value = incomingHits
      hasReachedEnd.value = incomingHits.length < PAGE_SIZE
    }

    totalHits.value = response.totalHits ?? loadedHits.value.length
    totalProjectCount.value = response.totalProjectCount ?? 0
    fuzzyExpanded.value = response.fuzzyExpanded ?? false
    suggestedQuery.value = response.suggestedQuery ?? null
    if (loadedHits.value.length >= totalHits.value) {
      hasReachedEnd.value = true
    }
    hasLoaded.value = true
  } catch (error: any) {
    if (token !== activeLoadToken) return
    if (!append) {
      loadedHits.value = []
      totalHits.value = 0
      totalProjectCount.value = 0
      fuzzyExpanded.value = false
      suggestedQuery.value = null
      hasLoaded.value = true
    }
    errorMessage.value = error?.data?.message || error?.message || 'Search failed.'
  } finally {
    if (token === activeLoadToken) {
      isLoadingInitial.value = false
      isLoadingMore.value = false
    }
  }
}

function reloadSearch() {
  activeLoadToken += 1
  resetResults()
  if (!canSearch.value) {
    hasLoaded.value = true
    return
  }
  void fetchSearchPage(false)
}

watch([workspaceId, searchQuery, matchMode], () => {
  reloadSearch()
}, { immediate: true })

const clusteredGroups = computed<WorkspaceTextSearchClusterGroup[]>(() => {
  const projectMap = new Map<string, WorkspaceTextSearchClusterGroup>()

  for (const hit of loadedHits.value) {
    const projectKey = hit.projectId
    let projectGroup = projectMap.get(projectKey)
    if (!projectGroup) {
      projectGroup = {
        workspaceId: hit.workspaceId,
        projectId: hit.projectId,
        projectName: hit.projectName,
        hitCount: 0,
        topScore: hit.score,
        pages: []
      }
      projectMap.set(projectKey, projectGroup)
    }

    projectGroup.hitCount += 1
    projectGroup.topScore = Math.max(projectGroup.topScore, hit.score)

    let pageGroup = projectGroup.pages.find(page => page.pageId === hit.pageId)
    if (!pageGroup) {
      pageGroup = {
        pageId: hit.pageId,
        pageName: hit.pageName,
        hitCount: 0,
        topScore: hit.score,
        hits: []
      }
      projectGroup.pages.push(pageGroup)
    }

    pageGroup.hitCount += 1
    pageGroup.topScore = Math.max(pageGroup.topScore, hit.score)
    pageGroup.hits.push(hit)
  }

  return Array.from(projectMap.values()).map(project => ({
    ...project,
    pages: project.pages
      .map(page => ({
        ...page,
        hits: [...page.hits].sort((a, b) => b.score - a.score)
      }))
      .sort((a, b) => b.topScore - a.topScore || a.pageName.localeCompare(b.pageName))
  }))
})

const displayRows = computed<SearchListRow[]>(() => viewMode.value === 'hits' ? loadedHits.value : clusteredGroups.value)
const hasMore = computed(() => !hasReachedEnd.value && loadedHits.value.length < totalHits.value)
const loadedPageCount = computed(() => new Set(loadedHits.value.map(hit => hit.pageId)).size)
const totalSizeLabel = computed(() => {
  if (viewMode.value === 'clustered') {
    return `${clusteredGroups.value.length} project clusters from ${loadedHits.value.length} loaded hits`
  }
  return `${loadedHits.value.length} of ${totalHits.value} hits loaded`
})

const scrollerRef = ref<HTMLElement | null>(null)

const rowVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: displayRows.value.length,
  getScrollElement: () => scrollerRef.value,
  estimateSize: () => viewMode.value === 'hits' ? 280 : 420,
  overscan: OVERSCAN,
  getItemKey: (index) => {
    const row = displayRows.value[index]
    if (!row) return `loader-${index}`
    return isClusterRow(row)
      ? `cluster-${row.projectId}`
      : `hit-${getHitKey(row)}`
  }
})))

const virtualRows = computed<Array<{ item: VirtualItem, row?: SearchListRow }>>(() =>
  rowVirtualizer.value.getVirtualItems().map(item => ({
    item,
    row: displayRows.value[item.index]
  }))
)

const totalVirtualSize = computed(() => rowVirtualizer.value.getTotalSize())

function measureElement(el: Element | null) {
  if (el instanceof HTMLElement) {
    rowVirtualizer.value.measureElement(el)
  }
}

const measureVirtualRow: VNodeRef = (el) => {
  measureElement(el instanceof Element ? el : null)
}

let measureFrame: number | null = null
function scheduleMeasure() {
  if (measureFrame !== null) {
    cancelAnimationFrame(measureFrame)
  }
  measureFrame = requestAnimationFrame(() => {
    measureFrame = null
    rowVirtualizer.value.measure()
  })
}

watch([displayRows, viewMode], async () => {
  await nextTick()
  scheduleMeasure()
})

function handleScroll() {
  const scroller = scrollerRef.value
  if (!scroller || !hasMore.value || isLoadingInitial.value || isLoadingMore.value) return

  const remaining = scroller.scrollHeight - (scroller.scrollTop + scroller.clientHeight)
  if (remaining <= 600) {
    void fetchSearchPage(true)
  }
}
</script>

<template>
  <UDashboardPanel id="workspace-search">
    <template #header>
      <UDashboardNavbar title="Workspace Search">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <div class="flex w-full max-w-4xl flex-col gap-2 sm:flex-row sm:items-center">
            <UInput
              v-model="searchInput"
              icon="i-lucide-search"
              placeholder="Search transcriptions across this workspace..."
              size="lg"
              class="w-full sm:flex-1"
            />

            <div class="flex min-w-36 items-center gap-2 text-sm text-muted sm:justify-start">
              <UIcon
                name="i-lucide-loader-circle"
                class="size-4"
                :class="isLoadingInitial || isLoadingMore ? 'animate-spin opacity-100' : 'opacity-0'"
              />
              <span class="whitespace-nowrap">
                {{ isLoadingInitial ? 'Searching…' : isLoadingMore ? 'Loading more…' : 'Ready' }}
              </span>
            </div>
          </div>
        </template>
        <template #right>
          <div class="flex flex-wrap items-center justify-end gap-2">
            <UBadge color="neutral" variant="subtle">
              {{ totalHits }} hits
            </UBadge>
            <UBadge color="neutral" variant="subtle">
              {{ totalProjectCount }} projects
            </UBadge>
            <UBadge color="neutral" variant="subtle">
              {{ loadedPageCount }} pages
            </UBadge>
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="flex h-full min-h-0 flex-col gap-4 p-4 md:p-6">
        <UAlert
          v-if="fuzzyExpanded"
          color="warning"
          variant="subtle"
          icon="i-lucide-wand-sparkles"
          :title="suggestedQuery ? `Fuzzy fallback used: ${suggestedQuery}` : 'Fuzzy fallback used'"
          description="The query was expanded with similar terms from indexed workspace text."
        />

        <UAlert
          v-if="errorMessage"
          color="error"
          variant="subtle"
          icon="i-lucide-alert-circle"
          :title="errorMessage"
        />

        <UEmpty
          v-if="!workspaceId"
          icon="i-lucide-layers"
          title="No workspace selected"
          description="Select a workspace to search its transcription text."
        />

        <UEmpty
          v-else-if="searchQuery.length < 2"
          icon="i-lucide-search"
          title="Enter at least two characters"
          description="Workspace text hits will appear here once the query is long enough."
        />

        <div
          v-else
          class="flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-default bg-default/80"
        >
          <div class="flex flex-col gap-4 border-b border-default px-4 py-4 lg:flex-row lg:items-start lg:justify-between">
            <div class="space-y-1">
              <div class="text-sm font-medium text-highlighted">
                {{ viewMode === 'clustered' ? 'Clustered Results' : 'Ranked Hits' }}
              </div>
              <div class="text-sm text-muted">
                {{ totalSizeLabel }}
              </div>
            </div>

            <div class="flex flex-col items-stretch gap-2 sm:flex-row sm:items-center lg:justify-end">
              <UTabs
                :model-value="viewMode"
                :items="viewTabItems"
                :content="false"
                size="sm"
                color="neutral"
                :ui="{ trigger: 'min-w-28 justify-center' }"
                @update:model-value="setView($event as 'hits' | 'clustered')"
              />

              <UTabs
                :model-value="matchMode"
                :items="matchTabItems"
                :content="false"
                size="sm"
                color="neutral"
                :ui="{ trigger: 'min-w-24 justify-center' }"
                @update:model-value="setMatch($event as 'auto' | 'exact' | 'fuzzy')"
              />
            </div>
          </div>

          <div
            ref="scrollerRef"
            class="min-h-0 flex-1 overflow-y-auto"
            @scroll.passive="handleScroll"
          >
            <div
              v-if="isLoadingInitial && displayRows.length === 0"
              class="divide-y divide-default/70"
            >
              <div
                v-for="index in 6"
                :key="index"
                class="px-5 py-5"
              >
                <div class="grid gap-5 xl:grid-cols-[460px_minmax(0,1fr)]">
                  <USkeleton class="h-60 w-full rounded-xl" />
                  <div class="space-y-3">
                    <div class="flex gap-2">
                      <USkeleton class="h-5 w-20" />
                      <USkeleton class="h-5 w-40" />
                    </div>
                    <USkeleton class="h-4 w-full" />
                    <USkeleton class="h-4 w-11/12" />
                    <USkeleton class="h-4 w-8/12" />
                    <div class="flex gap-2">
                      <USkeleton class="h-9 w-32" />
                      <USkeleton class="h-9 w-28" />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <UEmpty
              v-else-if="hasLoaded && displayRows.length === 0"
              class="py-12"
              icon="i-lucide-search-x"
              title="No matches found"
              description="Try a broader query or switch to fuzzy search."
            />

            <div
              v-else
              class="relative w-full"
              :style="{ height: `${totalVirtualSize}px` }"
            >
              <div
                v-for="{ item, row } in virtualRows"
                :key="String(item.key)"
                :ref="measureVirtualRow"
                class="absolute left-0 top-0 w-full px-4"
                :style="{ transform: `translateY(${item.start}px)` }"
              >
                <SearchTextClusterCard
                  v-if="row && isClusterRow(row)"
                  :group="row"
                  :text-filter="searchQuery"
                  @layout-change="scheduleMeasure"
                />

                <SearchTextHitCard
                  v-else-if="row"
                  :hit="row"
                  :text-filter="searchQuery"
                  @layout-change="scheduleMeasure"
                />
              </div>
            </div>
          </div>

          <div
            v-if="hasMore || isLoadingMore"
            class="border-t border-default/70 px-4 py-4"
          >
            <div v-if="isLoadingMore" class="flex items-center justify-center gap-2 text-sm text-muted">
              <UIcon name="i-lucide-loader-circle" class="size-4 animate-spin" />
              <span>Loading more results…</span>
            </div>

            <div v-else class="flex justify-center">
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-chevron-down"
                @click="fetchSearchPage(true)"
              >
                Load more results
              </UButton>
            </div>
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
