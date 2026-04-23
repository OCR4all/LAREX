<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type {
  CodecSummary,
  CodecProjectScope,
  GenerateCodecFromSourcesRequest,
  GenerateCodecFromSourcesResponse,
  ValidateCodecCharacterPageRef,
  ValidateCodecAgainstSourcesRequest,
  ValidateCodecAgainstSourcesResponse
} from '@/types/codec'
import { createSkeletonPageData } from '@/services/editor/project-loader'
import type { PageResponse } from '@/services/editor/project-loader'

type SourceProject = {
  id: string
  name: string
  pageCount?: number
  tags?: string[]
  resolvedTags?: Array<{ id: string, label: string, color?: string | null }> | null
}

type SourcePage = {
  id: string
  name: string
  xmlFileCount?: number
  imageCount?: number
  tags?: string[]
  resolvedTags?: Array<{ id: string, label: string, color?: string | null }> | null
}

type SourceTreeRow = {
  id: string
  rowType: 'project' | 'page'
  projectId: string
  pageId?: string
  name: string
  tags: string[]
  projectName?: string
  pageCount?: number
  children?: SourceTreeRow[]
}

const props = withDefaults(defineProps<{
  mode: 'generate' | 'validate'
  workspaceId: string
  sources: CodecProjectScope[]
  defaultCodecId?: string | null
  allowSourceEditing?: boolean
}>(), {
  defaultCodecId: null,
  allowSourceEditing: false
})

const emit = defineEmits<{
  close: [GenerateCodecFromSourcesResponse | ValidateCodecAgainstSourcesResponse | null]
}>()

const toast = useToast()
const UButton = resolveComponent('UButton')
const editorStore = useEditorStore()

const variantFilterMode = ref<'ALL' | 'SPECIFIC'>('ALL')
const variantUnindexed = ref(false)
const variantIndexInput = ref<number>(0)
const includeWhitespace = ref(false)

const generateMode = ref<'create' | 'append'>('create')
const newCodecName = ref('')
const newCodecDescription = ref('')
const newCodecTags = ref<string[]>([])
const selectedCodecId = ref(props.defaultCodecId ?? '')

const isSubmitting = ref(false)
const generateResult = ref<GenerateCodecFromSourcesResponse | null>(null)
const validateResult = ref<ValidateCodecAgainstSourcesResponse | null>(null)

const selectedSourceProjectIds = ref<Set<string>>(
  new Set(props.sources.map(source => source.projectId))
)
const selectedSourcePageIdsByProjectId = ref<Record<string, string[]>>(
  props.sources.reduce<Record<string, string[]>>((acc, source) => {
    if ((source.pageIds?.length ?? 0) > 0) {
      acc[source.projectId] = [...new Set(source.pageIds ?? [])]
    }
    return acc
  }, {})
)

const isSourceEditable = computed(() => props.allowSourceEditing || props.sources.length === 0)

const { data: sourceProjects, pending: sourceProjectsPending, error: sourceProjectsError } = await useFetch<SourceProject[]>(
  () => `/api/workspaces/${props.workspaceId}/projects`,
  { default: () => [] }
)

const sourcePagesByProjectId = ref<Record<string, SourcePage[]>>({})
const sourcePagesLoadingByProjectId = ref<Record<string, boolean>>({})
const sourcePagesErrorByProjectId = ref<Record<string, string | null>>({})
const sourceTreeExpanded = ref<Record<string, boolean>>({})

const sourceTreeColumns = computed<TableColumn<SourceTreeRow>[]>(() => [
  {
    id: 'select',
    header: '',
    meta: {
      class: {
        th: 'w-10',
        td: 'w-10'
      }
    }
  },
  {
    accessorKey: 'name',
    header: 'Name',
    cell: ({ row }) => {
      return h(
        'div',
        {
          style: {
            paddingLeft: `${row.depth}rem`
          },
          class: 'flex items-center gap-2'
        },
        [
          h(UButton, {
            color: 'neutral',
            variant: 'outline',
            size: 'xs',
            icon: row.getIsExpanded() ? 'i-lucide-minus' : 'i-lucide-plus',
            class: !row.getCanExpand() && 'invisible',
            ui: {
              base: 'p-0 rounded-sm',
              leadingIcon: 'size-4'
            },
            onClick: row.getToggleExpandedHandler()
          }),
          row.getValue('name') as string
        ]
      )
    }
  },
  { id: 'tags', header: 'Tags' }
])

const selectedProjectTagFilters = ref<string[]>([])
const selectedPageTagFilters = ref<string[]>([])

function extractTagLabels(item: { tags?: string[], resolvedTags?: Array<{ label: string }> | null }): string[] {
  const resolvedLabels = (item.resolvedTags ?? [])
    .map(tag => tag.label?.trim())
    .filter((label): label is string => Boolean(label))
  if (resolvedLabels.length > 0) return [...new Set(resolvedLabels)]

  const rawLabels = (item.tags ?? [])
    .map(tag => tag?.trim())
    .filter((label): label is string => Boolean(label))
  return [...new Set(rawLabels)]
}

function matchesAnyTag(selectedTags: string[], candidateTags: string[]): boolean {
  if (selectedTags.length === 0) return true
  const candidateSet = new Set(candidateTags)
  return selectedTags.some(tag => candidateSet.has(tag))
}

const projectTagOptions = computed(() => {
  const counts = new Map<string, number>()
  for (const project of sourceProjects.value ?? []) {
    for (const tag of extractTagLabels(project)) {
      counts.set(tag, (counts.get(tag) ?? 0) + 1)
    }
  }

  return Array.from(counts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([value, count]) => ({
      label: value,
      value,
      count
    }))
})

const pageTagOptions = computed(() => {
  const counts = new Map<string, number>()
  for (const pages of Object.values(sourcePagesByProjectId.value)) {
    for (const page of pages ?? []) {
      for (const tag of extractTagLabels(page)) {
        counts.set(tag, (counts.get(tag) ?? 0) + 1)
      }
    }
  }

  return Array.from(counts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([value, count]) => ({
      label: value,
      value,
      count
    }))
})

const sourceProjectIdsInOrder = computed(() => {
  const known = (sourceProjects.value ?? []).map(project => project.id)
  const missingSelected = Array.from(selectedSourceProjectIds.value).filter(projectId => !known.includes(projectId))
  return [...known, ...missingSelected]
})

const sourceTreeRows = computed<SourceTreeRow[]>(() => {
  const projectsById = new Map((sourceProjects.value ?? []).map(project => [project.id, project] as const))
  const rows: SourceTreeRow[] = []

  for (const projectId of sourceProjectIdsInOrder.value) {
    const project = projectsById.get(projectId)
    const pages = sourcePagesByProjectId.value[projectId] ?? []
    const filteredPages = pages
      .filter(page => matchesAnyTag(selectedPageTagFilters.value, extractTagLabels(page)))
      .map(page => ({
        id: `page:${projectId}:${page.id}`,
        rowType: 'page' as const,
        projectId,
        pageId: page.id,
        name: page.name,
        tags: extractTagLabels(page),
        projectName: project?.name ?? projectId
      }))

    const projectMatches = matchesAnyTag(selectedProjectTagFilters.value, extractTagLabels(project ?? {}))
    const pageFilterActive = selectedPageTagFilters.value.length > 0
    const pagesKnownForProject = Boolean(sourcePagesByProjectId.value[projectId] || sourcePagesLoadingByProjectId.value[projectId])
    const pageFilterMatches = !pageFilterActive || !pagesKnownForProject || filteredPages.length > 0
    if (!projectMatches || !pageFilterMatches) continue

    rows.push({
      id: `project:${projectId}`,
      rowType: 'project',
      projectId,
      name: project?.name ?? projectId,
      tags: extractTagLabels(project ?? {}),
      projectName: project?.name ?? projectId,
      pageCount: project?.pageCount ?? 0,
      children: filteredPages
    })
  }

  return rows
})

function getSourceTreeSubRows(row: SourceTreeRow): SourceTreeRow[] {
  return row.children ?? []
}

function getSourceTreeRowId(row: SourceTreeRow): string {
  return row.id
}

function canExpandSourceTreeRow(row: { original: SourceTreeRow }): boolean {
  return row.original.rowType === 'project' && (row.original.pageCount ?? 0) > 0
}

async function ensureSourceProjectPagesLoaded(projectId: string): Promise<void> {
  if (sourcePagesByProjectId.value[projectId]) return
  if (sourcePagesLoadingByProjectId.value[projectId]) return

  sourcePagesLoadingByProjectId.value = {
    ...sourcePagesLoadingByProjectId.value,
    [projectId]: true
  }
  sourcePagesErrorByProjectId.value = {
    ...sourcePagesErrorByProjectId.value,
    [projectId]: null
  }

  try {
    const pages = await $fetch<SourcePage[]>(`/api/projects/${projectId}/pages`)
    sourcePagesByProjectId.value = {
      ...sourcePagesByProjectId.value,
      [projectId]: pages ?? []
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to load pages'
    sourcePagesErrorByProjectId.value = {
      ...sourcePagesErrorByProjectId.value,
      [projectId]: message
    }
    sourcePagesByProjectId.value = {
      ...sourcePagesByProjectId.value,
      [projectId]: []
    }
  } finally {
    sourcePagesLoadingByProjectId.value = {
      ...sourcePagesLoadingByProjectId.value,
      [projectId]: false
    }
  }
}

function projectRowId(projectId: string): string {
  return `project:${projectId}`
}

watch(sourceTreeExpanded, (expanded) => {
  if (!isSourceEditable.value) return
  for (const [rowId, isExpanded] of Object.entries(expanded ?? {})) {
    if (!isExpanded || !rowId.startsWith('project:')) continue
    const projectId = rowId.replace('project:', '')
    void ensureSourceProjectPagesLoaded(projectId)
  }
}, { deep: true })

watch(selectedSourcePageIdsByProjectId, (byProjectId) => {
  if (!isSourceEditable.value) return
  const nextExpanded = { ...sourceTreeExpanded.value }
  for (const [projectId, selectedPageIds] of Object.entries(byProjectId)) {
    if ((selectedPageIds?.length ?? 0) === 0) continue
    nextExpanded[projectRowId(projectId)] = true
    void ensureSourceProjectPagesLoaded(projectId)
  }
  sourceTreeExpanded.value = nextExpanded
}, { deep: true, immediate: true })

watch(selectedPageTagFilters, (tags) => {
  if (!isSourceEditable.value) return
  if ((tags?.length ?? 0) === 0) return

  const projectIds = (sourceProjects.value ?? []).map(project => project.id)
  for (const projectId of projectIds) {
    void ensureSourceProjectPagesLoaded(projectId)
  }
}, { deep: true })

const effectiveSources = computed<CodecProjectScope[]>(() => {
  if (!isSourceEditable.value) {
    return props.sources
  }

  return sourceProjectIdsInOrder.value
    .filter(projectId => selectedSourceProjectIds.value.has(projectId))
    .map(projectId => ({
      projectId,
      pageIds: [...(selectedSourcePageIdsByProjectId.value[projectId] ?? [])]
    }))
})

const { data: codecs } = await useFetch<CodecSummary[]>(
  () => `/api/workspaces/${props.workspaceId}/codecs`,
  { default: () => [] }
)

const codecOptions = computed(() => {
  return (codecs.value ?? []).map(codec => ({
    label: codec.name,
    value: codec.id
  }))
})

const sourceProjectCount = computed(() => {
  return new Set(effectiveSources.value.map(source => source.projectId)).size
})

const selectedPageCount = computed(() => {
  return Object.values(selectedSourcePageIdsByProjectId.value).reduce((sum, pageIds) => sum + (pageIds?.length ?? 0), 0)
})

const runButtonLabel = computed(() => {
  if (props.mode === 'generate') {
    return generateMode.value === 'create' ? 'Generate Codec' : 'Append to Codec'
  }
  return 'Validate Codec'
})

const normalizedVariantIndex = computed<number | null>(() => {
  if (variantFilterMode.value !== 'SPECIFIC' || variantUnindexed.value) return null
  const parsed = Number(variantIndexInput.value)
  if (!Number.isFinite(parsed) || parsed < 0) return null
  return Math.floor(parsed)
})

const variantSelectionValid = computed(() => {
  if (variantFilterMode.value !== 'SPECIFIC') return true
  if (variantUnindexed.value) return true
  return normalizedVariantIndex.value !== null
})

const canSubmit = computed(() => {
  if (effectiveSources.value.length === 0 || !variantSelectionValid.value) return false

  if (props.mode === 'generate') {
    if (generateMode.value === 'create') {
      return newCodecName.value.trim().length > 0
    }
    return selectedCodecId.value.trim().length > 0
  }

  return selectedCodecId.value.trim().length > 0
})

function selectAllSourceProjects() {
  selectedSourceProjectIds.value = new Set((sourceProjects.value ?? []).map(project => project.id))
  selectedSourcePageIdsByProjectId.value = {}
}

function clearSourceProjects() {
  selectedSourceProjectIds.value = new Set()
  selectedSourcePageIdsByProjectId.value = {}
}

const visibleProjectIds = computed(() => sourceTreeRows.value.map(row => row.projectId))

const selectAllProjectsCheckboxState = computed<boolean | 'indeterminate'>(() => {
  if (visibleProjectIds.value.length === 0) return false
  const selectedCount = visibleProjectIds.value.filter(projectId => selectedSourceProjectIds.value.has(projectId)).length
  if (selectedCount === 0) return false
  if (selectedCount === visibleProjectIds.value.length) return true
  return 'indeterminate'
})

function setVisibleProjectSelection(selected: boolean): void {
  const nextProjects = new Set(selectedSourceProjectIds.value)
  const nextPages = { ...selectedSourcePageIdsByProjectId.value }

  for (const projectId of visibleProjectIds.value) {
    if (selected) {
      nextProjects.add(projectId)
      nextPages[projectId] = []
    } else {
      nextProjects.delete(projectId)
      nextPages[projectId] = []
    }
  }

  selectedSourceProjectIds.value = nextProjects
  selectedSourcePageIdsByProjectId.value = Object.fromEntries(
    Object.entries(nextPages).filter(([, pageIds]) => (pageIds?.length ?? 0) > 0)
  )
}

function getProjectCheckboxState(projectId: string): boolean | 'indeterminate' {
  if (!selectedSourceProjectIds.value.has(projectId)) return false

  const selectedPageIds = getSelectedPageIdsForProject(projectId)
  if (selectedPageIds.length === 0) return true

  const loadedPageCount = sourcePagesByProjectId.value[projectId]?.length ?? 0
  if (loadedPageCount === 0) return 'indeterminate'
  if (selectedPageIds.length >= loadedPageCount) return true
  return 'indeterminate'
}

function setSourceProjectSelection(projectId: string, selected: boolean) {
  const next = new Set(selectedSourceProjectIds.value)
  const nextPages = { ...selectedSourcePageIdsByProjectId.value }

  if (selected) {
    next.add(projectId)
    nextPages[projectId] = []
  } else {
    next.delete(projectId)
    nextPages[projectId] = []
  }

  selectedSourceProjectIds.value = next
  selectedSourcePageIdsByProjectId.value = Object.fromEntries(
    Object.entries(nextPages).filter(([, pageIds]) => (pageIds?.length ?? 0) > 0)
  )
}

function getSelectedPageIdsForProject(projectId: string): string[] {
  return selectedSourcePageIdsByProjectId.value[projectId] ?? []
}

function isSourcePageSelected(projectId: string, pageId: string): boolean {
  if (!selectedSourceProjectIds.value.has(projectId)) return false
  const selectedPageIds = getSelectedPageIdsForProject(projectId)
  if (selectedPageIds.length === 0) return true
  return selectedPageIds.includes(pageId)
}

function setSourcePageSelection(projectId: string, pageId: string, selected: boolean) {
  const selectedProjects = new Set(selectedSourceProjectIds.value)
  selectedProjects.add(projectId)
  selectedSourceProjectIds.value = selectedProjects

  const loadedPageIds = (sourcePagesByProjectId.value[projectId] ?? []).map(page => page.id)
  let current = new Set(getSelectedPageIdsForProject(projectId))

  if (current.size === 0) {
    if (selected) return
    current = new Set(loadedPageIds)
    current.delete(pageId)
  } else {
    if (selected) {
      current.add(pageId)
    } else {
      current.delete(pageId)
    }
  }

  const nextPagesByProject = { ...selectedSourcePageIdsByProjectId.value }
  if (current.size === 0) {
    nextPagesByProject[projectId] = []
  } else if (loadedPageIds.length > 0 && current.size >= loadedPageIds.length) {
    nextPagesByProject[projectId] = []
  } else {
    nextPagesByProject[projectId] = Array.from(current)
  }
  selectedSourcePageIdsByProjectId.value = Object.fromEntries(
    Object.entries(nextPagesByProject).filter(([, pageIds]) => (pageIds?.length ?? 0) > 0)
  )
}

async function loadAllSourceProjectPages(): Promise<void> {
  const projectIds = (sourceProjects.value ?? []).map(project => project.id)
  await Promise.all(projectIds.map(projectId => ensureSourceProjectPagesLoaded(projectId)))
}

const sourceProjectsErrorMessage = computed(() => {
  if (!sourceProjectsError.value) return ''
  const err = sourceProjectsError.value as { message?: string }
  return err.message || 'Failed to load projects'
})

type ValidationMissingCharacterRow = {
  character: string
  unicodeCodepoint: string
  pages: ValidateCodecCharacterPageRef[]
}

const validationMissingCharacterColumns: TableColumn<ValidationMissingCharacterRow>[] = [
  {
    accessorKey: 'character',
    header: 'Missing Character',
    meta: {
      class: {
        th: 'w-32',
        td: 'font-junicode text-base'
      }
    }
  },
  {
    accessorKey: 'unicodeCodepoint',
    header: 'Unicode Codepoint',
    meta: {
      class: {
        th: 'w-40',
        td: 'font-mono text-xs'
      }
    }
  },
  {
    accessorKey: 'pages',
    header: 'Pages'
  }
]

function toUnicodeCodepoint(char: string): string {
  const cp = char?.codePointAt(0)
  if (cp == null) return 'N/A'
  const minWidth = cp > 0xFFFF ? 6 : 4
  return `U+${cp.toString(16).toUpperCase().padStart(minWidth, '0')}`
}

function getMissingCharacterLabel(char: string): string {
  if (char === ' ') return 'SPACE'
  if (char === '\t') return 'TAB'
  if (char === '\n') return 'LF'
  if (char === '\r') return 'CR'
  if (char === '\u00A0') return 'NBSP'
  return ''
}

const validationMissingCharacterRows = computed<ValidationMissingCharacterRow[]>(() => {
  if (!validateResult.value) return []

  const details = validateResult.value.missingCharacterResults ?? []
  if (details.length > 0) {
    return details.map(detail => ({
      character: detail.character,
      unicodeCodepoint: toUnicodeCodepoint(detail.character),
      pages: detail.pages ?? []
    }))
  }

  return (validateResult.value.missingCharacters ?? []).map(character => ({
    character,
    unicodeCodepoint: toUnicodeCodepoint(character),
    pages: []
  }))
})

const loadingMissingPagesProjectId = ref<string | null>(null)

async function openMissingPagesInEditor(projectId: string, pageIds: string[]) {
  if (!projectId || pageIds.length === 0) return

  try {
    loadingMissingPagesProjectId.value = projectId
    const pages = await $fetch<PageResponse[]>(`/api/projects/${projectId}/pages`)
    const selected = pages.filter(page => pageIds.includes(page.id))
    if (selected.length === 0) {
      toast.add({
        title: 'No pages available',
        description: 'Could not load missing pages for editor navigation.',
        color: 'warning'
      })
      return
    }

    const skeletonPages = createSkeletonPageData(selected, { projectId })
    editorStore.setPagesWithSession(skeletonPages, projectId, props.workspaceId)
    await navigateTo('/editor')
  } catch (error: unknown) {
    const description = error instanceof Error ? error.message : 'Failed to open missing pages in editor'
    toast.add({
      title: 'Editor navigation failed',
      description,
      color: 'error'
    })
  } finally {
    loadingMissingPagesProjectId.value = null
  }
}

async function handleSubmit() {
  if (!canSubmit.value) return

  isSubmitting.value = true

  try {
    if (props.mode === 'generate') {
      const payload: GenerateCodecFromSourcesRequest = {
        sources: effectiveSources.value,
        variantScope: 'ALL',
        variantIndex: normalizedVariantIndex.value,
        unindexedOnly: variantFilterMode.value === 'SPECIFIC' ? variantUnindexed.value : false,
        includeWhitespace: includeWhitespace.value,
        targetCodecId: generateMode.value === 'append' ? selectedCodecId.value : null,
        newCodecName: generateMode.value === 'create' ? newCodecName.value.trim() : null,
        newCodecDescription: generateMode.value === 'create' ? (newCodecDescription.value.trim() || null) : null,
        newCodecTags: generateMode.value === 'create' ? newCodecTags.value : []
      }

      const response = await $fetch<GenerateCodecFromSourcesResponse>(
        `/api/workspaces/${props.workspaceId}/codecs/generate-from-sources`,
        {
          method: 'POST',
          body: payload
        }
      )

      generateResult.value = response
      validateResult.value = null

      toast.add({
        title: response.message || 'Codec generation completed',
        color: 'success'
      })

      await refreshNuxtData(wsKey(props.workspaceId, 'codecs', 'list'))
      if (!response.createdNewCodec && response.codec?.id) {
        await refreshNuxtData(wsKey(props.workspaceId, 'codecs', response.codec.id))
      }
      return
    }

    const payload: ValidateCodecAgainstSourcesRequest = {
      sources: effectiveSources.value,
      variantScope: 'ALL',
      variantIndex: normalizedVariantIndex.value,
      unindexedOnly: variantFilterMode.value === 'SPECIFIC' ? variantUnindexed.value : false,
      includeWhitespace: includeWhitespace.value
    }

    const response = await $fetch<ValidateCodecAgainstSourcesResponse>(
      `/api/workspaces/${props.workspaceId}/codecs/${selectedCodecId.value}/validate-against-sources`,
      {
        method: 'POST',
        body: payload
      }
    )

    validateResult.value = response
    generateResult.value = null

    toast.add({
      title: response.valid ? 'Codec validation passed' : 'Codec validation found missing characters',
      color: response.valid ? 'success' : 'warning'
    })
  } catch (error: unknown) {
    const description = error instanceof Error ? error.message : 'Request failed'
    toast.add({
      title: 'Codec action failed',
      description,
      color: 'error'
    })
  } finally {
    isSubmitting.value = false
  }
}

async function copyMissingCharacters() {
  const missing = validateResult.value?.missingCharacters ?? []
  if (missing.length === 0) return

  await copyTextToClipboard(missing.join(''), {
    successTitle: 'Missing characters copied',
    failureTitle: 'Failed to copy missing characters'
  })
}

function closeWithResult() {
  emit('close', generateResult.value ?? validateResult.value)
}
</script>

<template>
  <USlideover
    :title="mode === 'generate' ? 'Generate Codec From Sources' : 'Validate Codec Against Sources'"
    :description="'Analyze characters from selected project scopes.'"
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-6xl' }"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #body>
      <div class="space-y-4">
        <UCard>
          <template #header>
            <div class="text-sm font-medium">
              Source Scope
            </div>
          </template>

          <div v-if="isSourceEditable" class="space-y-3">
            <div class="flex items-center justify-between gap-2">
              <div class="text-xs text-muted">
                Select projects and optionally expand projects to choose specific pages.
              </div>
              <div class="flex items-center gap-2">
                <UButton
                  icon="i-lucide-list-checks"
                  color="neutral"
                  variant="soft"
                  size="xs"
                  @click="selectAllSourceProjects"
                >
                  Select all
                </UButton>
                <UButton
                  icon="i-lucide-list-x"
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  @click="clearSourceProjects"
                >
                  Clear
                </UButton>
              </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
              <USelectMenu
                v-model="selectedProjectTagFilters"
                :items="projectTagOptions"
                value-key="value"
                placeholder="Filter projects by tag"
                searchable
                multiple
                clear-search-on-close
                searchable-placeholder="Search project tags..."
              />
              <USelectMenu
                v-model="selectedPageTagFilters"
                :items="pageTagOptions"
                value-key="value"
                placeholder="Filter pages by tag"
                searchable
                multiple
                clear-search-on-close
                searchable-placeholder="Search page tags..."
              />
            </div>
            <div class="flex items-center justify-between gap-2 text-[11px] text-muted">
              <span>Page-tag options come from loaded pages.</span>
              <UButton
                color="neutral"
                variant="ghost"
                size="xs"
                icon="i-lucide-refresh-cw"
                @click="loadAllSourceProjectPages"
              >
                Load all page tags
              </UButton>
            </div>

            <div class="border border-default rounded-sm overflow-hidden">
              <AppTable
                table-id="codec-action-source-tree"
                v-model:expanded="sourceTreeExpanded"
                :columns="sourceTreeColumns"
                :data="sourceTreeRows"
                :loading="sourceProjectsPending"
                empty="No projects available in this workspace."
                :get-row-id="getSourceTreeRowId"
                :get-sub-rows="getSourceTreeSubRows"
                :expanded-options="{ getRowCanExpand: canExpandSourceTreeRow }"
                :ui="{
                  base: 'table-fixed border-separate border-spacing-0',
                  thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
                  tbody: '[&>tr]:last:[&>td]:border-b-0',
                  th: 'py-2 first:rounded-l-sm last:rounded-r-sm border-y border-default first:border-l last:border-r',
                  td: 'border-b border-default py-2',
                  separator: 'h-0'
                }"
              >
                <template #select-header>
                  <UCheckbox
                    :model-value="selectAllProjectsCheckboxState"
                    aria-label="Select all visible projects"
                    @update:model-value="(value) => setVisibleProjectSelection(!!value)"
                  />
                </template>

                <template #select-cell="{ row }">
                  <div class="flex items-center">
                    <template v-if="row.original.rowType === 'project'">
                      <UCheckbox
                        :model-value="getProjectCheckboxState(row.original.projectId)"
                        aria-label="Select project and all pages"
                        @update:model-value="(value) => setSourceProjectSelection(row.original.projectId, !!value)"
                      />
                    </template>
                    <template v-else>
                      <UCheckbox
                        :model-value="isSourcePageSelected(row.original.projectId, row.original.pageId ?? '')"
                        :disabled="!selectedSourceProjectIds.has(row.original.projectId)"
                        aria-label="Select page"
                        @update:model-value="(value) => setSourcePageSelection(row.original.projectId, row.original.pageId ?? '', !!value)"
                      />
                    </template>
                  </div>
                </template>

                <template #tags-cell="{ row }">
                  <div class="flex flex-wrap items-center gap-1 min-h-5">
                    <UBadge
                      v-for="tag in row.original.tags.slice(0, 3)"
                      :key="`${row.original.id}-${tag}`"
                      color="neutral"
                      variant="subtle"
                      size="xs"
                    >
                      {{ tag }}
                    </UBadge>
                    <span v-if="row.original.tags.length > 3" class="text-xs text-muted">
                      +{{ row.original.tags.length - 3 }}
                    </span>
                  </div>
                </template>
              </AppTable>
            </div>

            <div class="text-xs text-muted space-y-1">
              <div>Selected projects: {{ sourceProjectCount }}</div>
              <div v-if="selectedPageCount > 0">
                Explicitly selected pages: {{ selectedPageCount }}
              </div>
              <div v-else>
                Page scope: all pages in selected projects
              </div>
            </div>

            <div v-if="sourceProjectsErrorMessage" class="text-xs text-error">
              {{ sourceProjectsErrorMessage }}
            </div>
          </div>

          <div v-else class="text-sm text-muted space-y-1">
            <div>Projects: {{ sourceProjectCount }}</div>
            <div v-if="selectedPageCount > 0">
              Explicitly selected pages: {{ selectedPageCount }}
            </div>
            <div v-else>
              Page scope: all pages in selected projects
            </div>
          </div>
        </UCard>

        <UCard>
          <template #header>
            <div class="text-sm font-medium">
              Extraction Rules
            </div>
          </template>

          <div class="space-y-3">
            <UFormField label="Variant Selection">
              <USelect
                v-model="variantFilterMode"
                :items="[
                  { label: 'All indexed variants', value: 'ALL' },
                  { label: 'Specific index / unindexed', value: 'SPECIFIC' }
                ]"
                value-key="value"
              />
            </UFormField>

            <template v-if="variantFilterMode === 'SPECIFIC'">
              <UCheckbox v-model="variantUnindexed" label="Unindexed only" />

              <UFormField label="Variant index" :error="!variantUnindexed && normalizedVariantIndex === null ? 'Index must be a non-negative integer.' : undefined">
                <UInput
                  v-model.number="variantIndexInput"
                  type="number"
                  :min="0"
                  :disabled="variantUnindexed"
                  placeholder="0"
                />
              </UFormField>
            </template>

            <div class="flex items-center justify-between">
              <div>
                <div class="text-sm font-medium">
                  Include whitespace
                </div>
                <div class="text-xs text-muted">
                  Spaces, tabs, and newlines are included as codec characters.
                </div>
              </div>
              <USwitch v-model="includeWhitespace" />
            </div>
          </div>
        </UCard>

        <UCard v-if="mode === 'generate'">
          <template #header>
            <div class="text-sm font-medium">
              Target Codec
            </div>
          </template>

          <div class="space-y-3">
            <UFormField label="Mode">
              <USelect
                v-model="generateMode"
                :items="[
                  { label: 'Create new codec', value: 'create' },
                  { label: 'Append to existing codec', value: 'append' }
                ]"
                value-key="value"
              />
            </UFormField>

            <template v-if="generateMode === 'create'">
              <UFormField label="Codec Name" required>
                <UInput v-model="newCodecName" placeholder="New codec name" />
              </UFormField>

              <UFormField label="Description">
                <UTextarea v-model="newCodecDescription" placeholder="Optional description" />
              </UFormField>

              <UFormField label="Tags">
                <UInputTags v-model="newCodecTags" placeholder="Optional tags" />
              </UFormField>
            </template>

            <template v-else>
              <UFormField label="Select Codec" required>
                <USelect
                  v-model="selectedCodecId"
                  :items="codecOptions"
                  value-key="value"
                  placeholder="Choose codec"
                />
              </UFormField>
            </template>
          </div>
        </UCard>

        <UCard v-if="mode === 'validate'">
          <template #header>
            <div class="text-sm font-medium">
              Codec To Validate
            </div>
          </template>

          <UFormField label="Select Codec" required>
            <USelect
              v-model="selectedCodecId"
              :items="codecOptions"
              value-key="value"
              placeholder="Choose codec"
            />
          </UFormField>
        </UCard>

        <UCard v-if="generateResult">
          <template #header>
            <div class="text-sm font-medium">
              Generation Result
            </div>
          </template>

          <div class="text-sm space-y-1">
            <div>{{ generateResult.message }}</div>
            <div>Projects analyzed: {{ generateResult.analyzedProjectCount }}</div>
            <div>Pages analyzed: {{ generateResult.analyzedPageCount }}</div>
            <div>Extracted characters: {{ generateResult.extractedCharacterCount }}</div>
            <div>Added characters: {{ generateResult.addedCharacterCount }}</div>
            <div>Codec: {{ generateResult.codec.name }}</div>
          </div>
        </UCard>

        <UCard v-if="validateResult">
          <template #header>
            <div class="text-sm font-medium">
              Validation Result
            </div>
          </template>

          <div class="text-sm space-y-3">
            <div>{{ validateResult.message }}</div>
            <div>Projects analyzed: {{ validateResult.analyzedProjectCount }}</div>
            <div>Pages analyzed: {{ validateResult.analyzedPageCount }}</div>
            <div>Missing characters: {{ validateResult.missingCharacterCount }}</div>

            <div v-if="validateResult.missingCharacters.length > 0" class="space-y-2">
              <UButton
                size="sm"
                variant="soft"
                color="neutral"
                @click="copyMissingCharacters"
              >
                Copy Missing Characters
              </UButton>
            </div>

            <div v-if="validationMissingCharacterRows.length > 0" class="space-y-2">
              <div class="text-xs font-medium text-muted">
                Missing characters by page
              </div>
              <AppTable
                table-id="codec-action-validation-missing-chars"
                :data="validationMissingCharacterRows"
                :columns="validationMissingCharacterColumns"
                :ui="{
                  base: 'table-fixed border-separate border-spacing-0',
                  thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
                  tbody: '[&>tr]:last:[&>td]:border-b-0',
                  th: 'py-2 first:rounded-l-sm last:rounded-r-sm border-y border-default first:border-l last:border-r',
                  td: 'border-b border-default py-2 align-top',
                  separator: 'h-0'
                }"
              >
                <template #character-cell="{ row }">
                  <div class="flex items-center gap-2">
                    <span v-if="!getMissingCharacterLabel(row.original.character)" class="font-junicode text-base">
                      {{ row.original.character }}
                    </span>
                    <UBadge
                      v-else
                      color="warning"
                      variant="subtle"
                      size="xs"
                    >
                      {{ getMissingCharacterLabel(row.original.character) }}
                    </UBadge>
                  </div>
                </template>
                <template #pages-cell="{ row }">
                  <div class="flex flex-wrap items-center gap-1">
                    <UBadge
                      v-for="pageRef in row.original.pages"
                      :key="`${row.original.character}:${pageRef.projectId}:${pageRef.pageId}`"
                      color="neutral"
                      variant="subtle"
                      size="xs"
                    >
                      {{ pageRef.projectName ? `${pageRef.projectName} / ${pageRef.pageName ?? pageRef.pageId}` : (pageRef.pageName ?? pageRef.pageId) }}
                    </UBadge>
                    <span v-if="row.original.pages.length === 0" class="text-xs text-muted">
                      No page-level details available
                    </span>
                  </div>
                </template>
              </AppTable>
            </div>

            <div class="space-y-2">
              <div class="text-xs font-medium text-muted">
                Project actions
              </div>
              <div class="space-y-2">
                <div
                  v-for="projectResult in validateResult.projectResults"
                  :key="projectResult.projectId"
                  class="border border-default rounded-sm p-2 flex items-center justify-between gap-3"
                >
                  <div class="min-w-0">
                    <div class="font-medium truncate">
                      {{ projectResult.projectName || projectResult.projectId }}
                    </div>
                    <div class="text-xs text-muted">
                      {{ projectResult.missingCharacterCount }} missing characters, {{ projectResult.missingPageCount || 0 }} affected pages
                    </div>
                  </div>
                  <div class="flex items-center gap-2">
                    <UBadge :color="projectResult.valid ? 'success' : 'warning'" variant="soft" size="xs">
                      {{ projectResult.valid ? 'Valid' : 'Missing' }}
                    </UBadge>
                    <UButton
                      size="xs"
                      color="neutral"
                      variant="soft"
                      icon="i-lucide-square-arrow-out-up-right"
                      :loading="loadingMissingPagesProjectId === projectResult.projectId"
                      :disabled="!projectResult.missingPageIds || projectResult.missingPageIds.length === 0"
                      @click="openMissingPagesInEditor(projectResult.projectId, projectResult.missingPageIds ?? [])"
                    >
                      Open Missing Pages
                    </UButton>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </UCard>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton
          color="neutral"
          variant="subtle"
          :disabled="!canSubmit"
          :loading="isSubmitting"
          @click="handleSubmit"
        >
          {{ runButtonLabel }}
        </UButton>
        <UButton
          v-if="generateResult || validateResult"
          color="primary"
          @click="closeWithResult"
        >
          Done
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
