<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import {
  LazyCodecSlideoverAction,
  LazyPageSlideoverEdit,
  LazyPageModalImages,
  LazyProjectModalConflictResolution,
  LazyProjectSlideoverAddToDataset,
  LazyProjectSlideoverPdfPrefix,
  LazyProjectSlideoverEdit,
  LazyProjectSlideoverRelease,
  LazyProjectSlideoverReleaseShare,
  LazyProjectSlideoverExportTarget,
  LazyProjectSlideoverIiifImport,
  LazyProjectSlideoverBulkDeletePages,
  LazyProjectSlideoverXmlEditor,
  LazyUiConfirmSlideover,
  LazyUiDeleteSlideover,
  LazyEditorVersionHistorySlideover,
  LazyShareSlideover } from '#components'
import DiffMatchPatch from 'diff-match-patch'
import type { Diff } from 'diff-match-patch'
import type { DropdownMenuItem, BreadcrumbItem } from '@nuxt/ui'
import type { Subtask } from '~/types/index'
import { wsKey } from '@/utils/fetch-keys'
import { createSkeletonPageData, type PageResponse } from '@/services/editor/project-loader'
import { createPageXmlLabelSet } from '@/models/editor'
import { useCollaborationPageSummary } from '@/composables/use-collaboration-page-summary'
import type { LabelSet as ApiLabelSet } from '@/types/label-set'
import { useEditorStore } from '@/stores/editor/editor.store'
import { usePagePrefetch } from '@/composables/use-page-prefetch'
import type { CodecProjectScope, GenerateCodecFromSourcesResponse, ValidateCodecAgainstSourcesResponse } from '@/types/codec'
import type { DictionaryProjectScope, DictionaryValidateAgainstSourcesResponse } from '@/types/dictionary'
import type { ApplySourcesResponse, NormalizePreview, NormalizeSourcesResponse, NormalizationProfile, NormalizationProjectScope, NormalizeTarget } from '@/types/normalization-profile'
import type { ValidateAgainstSourcesResponse, ValidationProjectScope } from '@/types/validation-ruleset'
import type { ProjectPackageRelease } from '@/types/project-package-release'
import type { NormalizationPresetRuleKey } from '@/utils/normalization-preset-rule-help'
import UiColorTag from '@/components/ui/color-tag.vue'
import { useWorkspaceBootstrap } from '@/composables/use-workspace-bootstrap'
import { useIndexStatusPolling } from '@/composables/use-index-status-polling'
import { getAvatarInitials, resolveManagedProfileAvatarSrc } from '@/utils/avatar'
import { extractApiErrorMessage } from '@/utils/api-error'

const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')
const UDropdownMenu = resolveComponent('UDropdownMenu')
const NuxtTime = resolveComponent('NuxtTime')
const UPopover = resolveComponent('UPopover')
const UAvatar = resolveComponent('UAvatar')

const route = useRoute()
const toast = useToast()
const editorStore = useEditorStore()
const collaborationPageSummary = useCollaborationPageSummary()
const pagePrefetch = usePagePrefetch()
const { selectedWorkspace } = await useWorkspaceBootstrap()
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const { allow } = useActionVisibility()

const projectId = route.params.id as string

if (import.meta.client) {
  void collaborationPageSummary.ensureProjectSummary(projectId)
}

const projectKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId))
const projectReleasesKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'releases'))
const projectPagesKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'pages'))
const projectStatusKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'status'))
const starredProjectsKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', 'starred'))
const libraryProjectsKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', 'list'))

type ProjectData = {
  id: string
  name: string
  description: string
  tags: string[]
  created: string
  updated: string
  pageCount: number
  isStarred: boolean
  storageUsedBytes: number
  storageUsedFormatted: string
  locked: boolean
  lockedReason: string | null
  codecId?: string | null
  labelSetId?: string | null
  dictionaryId?: string | null
  normalizationProfileId?: string | null
  validationRulesetId?: string | null
  defaultGtIndex?: number | null
  capabilities?: {
    canEdit: boolean
    canShare: boolean
    canDelete: boolean
    canDeletePages: boolean
    canUpload: boolean
    canExportPackage: boolean
  }
}

type ResolvedTag = {
  id: string
  label: string
  color: string | null
}

type ConflictInfo = {
  conflictId: string
  conflictType: 'IMAGE_VARIANT_EXISTS' | 'XML_FILE_EXISTS'
  existingFileName: string
  newFileName: string
  existingFilePath: string
  newFilePath: string | null
  conflictTimestamp: string
  pageId: string
  pageName: string
  details: {
    existingFileSize: string | null
    newFileSize: string | null
    existingFileModified: string | null
    newFileModified: string | null
  }
}

const DEFAULT_CUSTOM_TAG_COLOR = '#2563eb'

type PageIndexingStatus = 'NOT_APPLICABLE' | 'UNINDEXED' | 'INDEXING' | 'INDEXED'
type ExportFormat = 'PAGE_XML' | 'ALTO_XML' | 'TXT' | 'PDF' | 'DOCX' | 'TEI' | 'CSV' | 'XLSX'
type TextLevel = 'PAGE' | 'REGION' | 'TEXT_LINE'
type SpreadsheetProfile = 'PAGE_METADATA' | 'TAGS' | 'REGIONS'
type PdfProfile = 'SEARCHABLE' | 'IMAGES_ONLY' | 'TEXT_PAGES' | 'PDFA_SEARCHABLE'
type TeiProfile = 'STANDARD' | 'LAYOUT'
type ExportDialogMode = 'page' | 'project' | 'package'
type ProjectActionScope = 'all' | 'selection'
type DocxOptions = {
  preserveLineBreaks: boolean
  forcePageBreaks: boolean
  includeImageNames: boolean
  markUnclearWords: boolean
}
type ExportDialogResult = {
  format: ExportFormat | null
  targetPageXmlVersion: string
  includePageDelimiters: boolean
  textLevel: TextLevel
  textVariantIndex: number
  pdfProfile: PdfProfile
  teiProfile: TeiProfile
  spreadsheetProfiles: SpreadsheetProfile[]
  docxOptions: DocxOptions
  embeddedOutputs: Array<{
    format: Exclude<ExportFormat, 'PAGE_XML'>
    includePageDelimiters?: boolean
    textLevel?: TextLevel
    textVariantIndex?: number
    pdfProfile?: PdfProfile
    teiProfile?: TeiProfile
    spreadsheetProfiles?: SpreadsheetProfile[]
    docxOptions?: DocxOptions
  }>
}

type Page = {
  id: string
  name: string
  description: string
  tags: string[]
  resolvedTags: ResolvedTag[] | null
  created: string
  updated: string
  xmlFileCount: number
  imageCount: number
  thumbnailUrl?: string | null
  indexingStatus?: PageIndexingStatus
}

function getPageCollaborationSummary(pageId: string) {
  return collaborationPageSummary.getPageSummary(pageId, projectId)
}

function normalizeProjectForEdit(project: ProjectData) {
  return {
    ...project,
    codecId: project.codecId ?? undefined,
    labelSetId: project.labelSetId ?? undefined,
    dictionaryId: project.dictionaryId ?? undefined,
    normalizationProfileId: project.normalizationProfileId ?? undefined,
    validationRulesetId: project.validationRulesetId ?? undefined
  }
}

const { data: project, error: projectError, pending: projectPending, refresh: refreshProject } = await useFetch<ProjectData>(() => `/api/workspaces/${selectedWorkspace.value}/projects/${projectId}`, {
  key: projectKey,
  watch: [selectedWorkspace]
})

const { data: releases, error: releasesError, pending: releasesPending, refresh: refreshReleases } = await useFetch<ProjectPackageRelease[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/releases`,
  {
    key: projectReleasesKey,
    watch: [selectedWorkspace],
    default: () => []
  }
)

function getErrorStatusCode(error: unknown): number | null {
  const candidate = error as {
    statusCode?: number | string
    status?: number | string
    data?: { statusCode?: number | string, status?: number | string }
    response?: { status?: number | string, _data?: { statusCode?: number | string, status?: number | string } }
  } | null

  const values = [
    candidate?.statusCode,
    candidate?.status,
    candidate?.data?.statusCode,
    candidate?.data?.status,
    candidate?.response?.status,
    candidate?.response?._data?.statusCode,
    candidate?.response?._data?.status
  ]

  for (const value of values) {
    if (value == null) continue
    const parsed = Number(value)
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed
    }
  }

  return null
}

function getErrorMessage(error: unknown, fallback: string): string {
  const candidate = error as {
    message?: unknown
    data?: { message?: unknown }
    response?: { _data?: { message?: unknown } }
  } | null

  const values = [
    candidate?.data?.message,
    candidate?.response?._data?.message,
    candidate?.message
  ]

  for (const value of values) {
    if (typeof value === 'string' && value.trim().length > 0) {
      return value
    }
  }

  return fallback
}

const projectErrorStatusCode = computed(() => getErrorStatusCode(projectError.value))
const isProjectNotFound = computed(() => projectErrorStatusCode.value === 404)
const projectLoadErrorMessage = computed(() => getErrorMessage(projectError.value, 'Failed to load project.'))
const projectCapabilities = useResourceCapabilities(project, 'project')
const canManageDatasets = computed(() => allow(workspaceCapabilities.value.canManageProjects))

const { data: pages, error: pagesError, pending: pagesPending, refresh: refreshPagesFetch } = await useFetch<Page[]>(() => `/api/projects/${projectId}/pages`, {
  key: projectPagesKey
})

const PAGE_INDEX_STATUS_POLL_MS = 3000

function hasIndexingPages(list: Page[] | null | undefined): boolean {
  return (list ?? []).some(page => page.indexingStatus === 'INDEXING')
}

const pageIndexStatusPolling = useIndexStatusPolling({
  ids: computed(() => [projectId]),
  intervalMs: PAGE_INDEX_STATUS_POLL_MS,
  signature: computed(() => `${projectId}:${hasIndexingPages(pages.value) ? 1 : 0}`),
  hasPending: () => hasIndexingPages(pages.value),
  poll: async () => {
    try {
      const statuses = await $fetch<Record<string, PageIndexingStatus>>(`/api/projects/${projectId}/pages/index-statuses`)
      if (pages.value) {
        pages.value = pages.value.map((page) => {
          const nextStatus = statuses[page.id]
          if (!nextStatus || nextStatus === page.indexingStatus) return page
          return { ...page, indexingStatus: nextStatus }
        })
      }
    } catch (error) {
      const statusCode = Number(
        (error as { statusCode?: number, response?: { status?: number } })?.statusCode
        ?? (error as { response?: { status?: number } })?.response?.status
        ?? 0
      )
      if (statusCode === 401 || statusCode === 403) {
        return false
      }
    }
  }
})

const subtaskSummaryKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'subtask-summary'))
const { data: subtaskSummary, refresh: _refreshSubtaskSummary } = await useFetch<Record<string, number>>(
  () => `/api/projects/${projectId}/pages/subtask-summary`,
  {
    key: subtaskSummaryKey,
    default: () => ({})
  }
)

const openSubtasksKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'open-subtasks'))
const { data: openSubtasksByPage } = await useFetch<Record<string, Subtask[]>>(
  () => `/api/projects/${projectId}/pages/subtasks/open`,
  {
    key: openSubtasksKey,
    default: () => ({})
  }
)

const items = computed<BreadcrumbItem[]>(() => [
  {
    label: 'Library',
    to: '/'
  },
  {
    label: project.value?.name || 'Project'
  }
])

const { data: projectStatus, refresh: refreshProjectStatus } = await useFetch<{ hasUnresolvedConflicts: boolean, isBlocked: boolean }>(() => `/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/status`, {
  key: projectStatusKey,
  watch: [selectedWorkspace]
})

const {
  isUploading,
  isManualPagesRefresh,
  showPagesLoadingSpinner,
  refreshPagesData,
  startProjectUpload
} = useProjectUploadOrchestration({
  projectId,
  workspaceId: computed(() => selectedWorkspace.value ?? undefined),
  projectName: computed(() => project.value?.name ?? undefined),
  pages,
  pagesPending,
  pagesError,
  refreshPagesFetch,
  refreshProject,
  refreshProjectStatus,
  onIndexingPagesDetected: () => pageIndexStatusPolling.schedule(projectId, 0)
})

const isStarring = ref(false)

async function toggleStar() {
  if (isStarring.value || !project.value) return

  try {
    isStarring.value = true
    const response = await $fetch<{ starred: boolean, message: string }>(`/api/projects/${projectId}/star/toggle`, {
      method: 'PUT'
    })

    project.value.isStarred = response.starred

    await refreshNuxtData(starredProjectsKey.value)

    toast.add({
      title: response.message,
      color: response.starred ? 'success' : 'info',
      icon: response.starred ? 'i-lucide-star' : 'i-lucide-star-off'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Error toggling star',
      description: message || 'Failed to update star status',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isStarring.value = false
  }
}

const fileInput = ref<HTMLInputElement>()

async function handleFileUpload(files: FileList | null) {
  if (!files || files.length === 0) {
    toast.add({
      title: 'No files selected',
      description: 'Please select files to upload (images, XML files, and/or PDFs)',
      color: 'warning',
      icon: 'i-lucide-alert-triangle'
    })
    return
  }

  const fileArray = Array.from(files)
  const pdfFiles = fileArray.filter(f => (f.type === 'application/pdf') || f.name.toLowerCase().endsWith('.pdf'))
  let pdfPrefixesByFileName: Record<string, string> | null = null

  if (pdfFiles.length > 0) {
    const infos = pdfFiles.map((f) => {
      const dotIndex = f.name.indexOf('.')
      const defaultPrefix = dotIndex >= 0 ? f.name.substring(0, dotIndex) : f.name
      return { fileName: f.name, defaultPrefix }
    })

    const instance = pdfPrefixSlideover.open({ files: infos })
    pdfPrefixesByFileName = await instance.result

    if (!pdfPrefixesByFileName) {
      if (fileInput.value) fileInput.value.value = ''
      return
    }
  }

  await startProjectUpload(fileArray, pdfPrefixesByFileName || undefined)

  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

async function viewConflicts() {
  try {
    const conflicts = await $fetch<ConflictInfo[]>(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/conflicts`)

    if (conflicts.length === 0) {
      toast.add({
        title: 'No conflicts found',
        description: 'All upload conflicts have been resolved',
        color: 'info',
        icon: 'i-lucide-info'
      })
      return
    }

    conflictResolutionModal.open({
      projectId,
      conflicts,
      uploadResult: {
        totalPagesCreated: 0,
        totalPagesUpdated: 0,
        totalImagesProcessed: 0,
        totalXmlFilesProcessed: 0,
        pages: []
      }
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Failed to load conflicts',
      description: message || 'An error occurred',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  }
}

const isLoadingEditor = ref(false)

async function loadProjectLabelSet() {
  const fallback = () => editorStore.setLabelSet(createPageXmlLabelSet())
  const labelSetId = project.value?.labelSetId
  if (!labelSetId) {
    fallback()
    return
  }
  try {
    const labelSet = await $fetch<ApiLabelSet>(`/api/workspaces/${selectedWorkspace.value}/label-sets/${labelSetId}`)
    editorStore.setLabelSetFromApi(labelSet)
  } catch {
    fallback()
  }
}

/**
 * Prefetch data when user hovers over the "Open in Editor" button.
 * This warms the cache so the editor opens faster.
 */
let prefetchTimeout: ReturnType<typeof setTimeout> | null = null
function handleEditorButtonHover() {
  if (prefetchTimeout) clearTimeout(prefetchTimeout)

  prefetchTimeout = setTimeout(() => {
    if (!pages.value || pages.value.length === 0) return

    const pageIdsToLoad = selectedPageIds.value.size > 0
      ? Array.from(selectedPageIds.value)
      : pages.value.slice(0, 5).map(p => p.id) // First 5 pages if none selected

    pagePrefetch.prefetchForEditor(projectId, pageIdsToLoad)
  }, 200) // 200ms debounce
}

function handleEditorButtonLeave() {
  if (prefetchTimeout) {
    clearTimeout(prefetchTimeout)
    prefetchTimeout = null
  }
}

async function handleOpenInEditor() {
  if (!pages.value || pages.value.length === 0) return

  try {
    isLoadingEditor.value = true

    const pagesToUse = selectedPageIds.value.size > 0
      ? pages.value.filter(p => selectedPageIds.value.has(p.id))
      : pages.value

    const pageResponses: PageResponse[] = pagesToUse.map(page => ({
      id: page.id,
      name: page.name,
      thumbnailUrl: page.thumbnailUrl ?? undefined,
      tags: page.tags ?? [],
      resolvedTags: page.resolvedTags ?? null,
      imageCount: page.imageCount,
      xmlFileCount: page.xmlFileCount,
      indexingStatus: page.indexingStatus
    }))

    const skeletonPages = createSkeletonPageData(pageResponses, {
      projectId,
      projectName: project.value?.name
    })

    editorStore.setPagesWithSession(skeletonPages, projectId, selectedWorkspace.value ?? null)
    await loadProjectLabelSet()

    await navigateTo('/editor')
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Failed to open editor',
      description: message || 'An error occurred',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isLoadingEditor.value = false
  }
}

const overlay = useOverlay()

const pageEditSlideover = overlay.create(LazyPageSlideoverEdit)
const pageDeleteSlideover = overlay.create(LazyUiDeleteSlideover)
const pageImagesModal = overlay.create(LazyPageModalImages)
const conflictResolutionModal = overlay.create(LazyProjectModalConflictResolution)
const pdfPrefixSlideover = overlay.create(LazyProjectSlideoverPdfPrefix)
const createReleaseSlideover = overlay.create(LazyProjectSlideoverRelease)
const releaseShareSlideover = overlay.create(LazyProjectSlideoverReleaseShare)
const exportTargetSlideover = overlay.create(LazyProjectSlideoverExportTarget)
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)

const projectEditSlideover = overlay.create(LazyProjectSlideoverEdit)
const projectDeleteSlideover = overlay.create(LazyUiDeleteSlideover)
const projectShareSlideover = overlay.create(LazyShareSlideover)
const codecActionSlideover = overlay.create(LazyCodecSlideoverAction)
const addToDatasetSlideover = overlay.create(LazyProjectSlideoverAddToDataset)
const bulkDeletePagesSlideover = overlay.create(LazyProjectSlideoverBulkDeletePages)
const versionHistorySlideover = overlay.create(LazyEditorVersionHistorySlideover)
const xmlEditorSlideover = overlay.create(LazyProjectSlideoverXmlEditor)
const iiifImportSlideover = overlay.create(LazyProjectSlideoverIiifImport)

const router = useRouter()
const isDeletingProject = ref(false)

async function goToLibrary() {
  await router.push('/')
}

async function refreshProjectPagesData() {
  try {
    await $fetch(`/api/projects/${projectId}/pages/invalidate-cache`, { method: 'POST' })
  } catch {
    // Best effort. The follow-up refresh still fetches current data when cache invalidation is unavailable.
  }

  const workspaceId = selectedWorkspace.value
  await Promise.allSettled([
    refreshPagesFetch(),
    refreshProject(),
    refreshProjectStatus(),
    workspaceId ? refreshNuxtData(libraryProjectsKey.value) : Promise.resolve(),
    workspaceId ? refreshNuxtData(starredProjectsKey.value) : Promise.resolve(),
    workspaceId ? refreshNuxtData(wsKey(workspaceId, 'storage', 'quota')) : Promise.resolve()
  ])
}

async function openIiifImportSlideover() {
  if (!project.value) return

  const instance = iiifImportSlideover.open({
    projectId: project.value.id,
    workspaceId: selectedWorkspace.value ?? '',
    onFinished: refreshProjectPagesData
  })

  const imported = await instance.result
  if (imported) {
    await refreshProjectPagesData()
  }
}

const projectNotFoundActions = computed<Array<Record<string, unknown>>>(() => [
  {
    icon: 'i-lucide-arrow-left',
    label: 'Back to library',
    color: 'neutral' as const,
    variant: 'solid' as const,
    onClick: goToLibrary
  },
  {
    icon: 'i-lucide-refresh-cw',
    label: 'Try again',
    color: 'neutral' as const,
    variant: 'ghost' as const,
    onClick: refreshProject
  }
])

async function handleDeleteProject() {
  if (!allow(projectCapabilities.value.canDelete)) return
  if (!project.value || isDeletingProject.value) return

  const instance = projectDeleteSlideover.open({
    name: project.value.name,
    entityType: 'Project',
    warningDetails: [
      `${project.value.pageCount} ${project.value.pageCount === 1 ? 'page' : 'pages'}`,
      'All associated images and XML files',
      'All project history and annotations'
    ]
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const workspaceId = selectedWorkspace.value
  if (!workspaceId) {
    toast.add({
      title: 'Delete Failed',
      description: 'No workspace selected',
      color: 'error'
    })
    return
  }

  const projectName = project.value.name
  const deletingProjectId = projectId
  const progressToast = toast.add({
    title: 'Deleting Project',
    description: projectName,
    color: 'neutral',
    icon: 'i-lucide-loader-circle',
    ui: { icon: 'animate-spin' },
    close: false,
    progress: false,
    duration: 0
  })
  const { data: libraryProjects } = useNuxtData<ProjectData[]>(libraryProjectsKey.value)
  const previousLibraryProjects = libraryProjects.value ? [...libraryProjects.value] : null

  if (libraryProjects.value) {
    libraryProjects.value = libraryProjects.value.filter(item => item.id !== deletingProjectId)
  }

  isDeletingProject.value = true

  try {
    await router.push('/')
  } catch {
    // Continue deletion even when navigation is interrupted.
  }

  try {
    await $fetch(`/api/workspaces/${workspaceId}/projects/${deletingProjectId}`, { method: 'DELETE' })
    toast.add({
      title: 'Project Deleted',
      description: `"${projectName}" has been permanently deleted`,
      color: 'success',
      icon: 'i-lucide-trash-2'
    })

    void refreshNuxtData(wsKey(workspaceId, 'projects', 'list'))
  } catch (error: unknown) {
    if (previousLibraryProjects) {
      libraryProjects.value = previousLibraryProjects
    }

    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Delete Failed',
      description: message || 'Failed to delete project',
      color: 'error'
    })

    void refreshNuxtData(wsKey(workspaceId, 'projects', 'list'))
  } finally {
    toast.remove(progressToast.id)
    isDeletingProject.value = false
  }
}

async function exportProjectPackage(scope: ProjectActionScope = 'all') {
  if (!selectedWorkspace.value || !project.value) return

  const options = await requestExportOptions('package')
  if (!options) return

  const payload = {
    pageIds: getExportPageIds(scope),
    targetPageXmlVersion: options.targetPageXmlVersion,
    embeddedOutputs: options.embeddedOutputs
  }
  const fallbackName = `${project.value.name.replace(/\\s+/g, '-').toLowerCase()}.larex-project.zip`

  try {
    const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/export-package`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    if (!response.ok) {
      throw new Error(`Export failed (${response.status})`)
    }

    await downloadBlobResponse(response, fallbackName)

    toast.add({
      title: 'Project package exported',
      color: 'success',
      icon: 'i-lucide-download'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export project package'
    toast.add({
      title: 'Export failed',
      description: message,
      color: 'error'
    })
  }
}

async function openCreateRelease() {
  if (!allow(projectCapabilities.value.canShare)) return
  if (!project.value) return

  const instance = createReleaseSlideover.open({
    projectId,
    suggestedTag: nextReleaseTag.value
  })
  const createdReleaseId = await instance.result as string | null
  if (!createdReleaseId) return
  await refreshReleases()
}

async function openReleaseShare(release: ProjectPackageRelease) {
  const instance = releaseShareSlideover.open({
    projectId,
    release
  })
  const shouldRefresh = await instance.result as boolean | null
  if (shouldRefresh) {
    await refreshReleases()
  }
}

async function downloadProjectRelease(release: ProjectPackageRelease) {
  if (!selectedWorkspace.value || !project.value) return

  try {
    const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/releases/${release.id}/download`)
    if (!response.ok) {
      const message = await response.text()
      throw new Error(message || `Download failed (${response.status})`)
    }
    await downloadBlobResponse(response, release.packageFileName || `${project.value.name}-${release.versionTag}.larex-project.zip`)
  } catch (error: unknown) {
    toast.add({
      title: 'Release download failed',
      description: extractApiErrorMessage(error, 'Failed to download release package'),
      color: 'error'
    })
  }
}

function getReleaseCardItems(release: ProjectPackageRelease): DropdownMenuItem[] {
  const isReady = release.status === 'READY'
  const items: DropdownMenuItem[] = [
    {
      label: 'Download',
      icon: 'i-lucide-download',
      disabled: !isReady,
      onSelect: () => { void downloadProjectRelease(release) }
    }
  ]

  if (allow(projectCapabilities.value.canShare)) {
    items.unshift({
      label: 'Share',
      icon: 'i-lucide-key-round',
      disabled: !isReady,
      onSelect: () => { void openReleaseShare(release) }
    })
  }

  return items
}

async function exportProjectOutput(scope: ProjectActionScope = 'all') {
  if (!selectedWorkspace.value || !project.value) return

  const options = await requestExportOptions('project')
  if (!options) return

  const format = normalizeExportFormat(options.format)
  if (!format) return

  const fallbackName = `${project.value.name.replace(/\\s+/g, '-').toLowerCase()}.${formatExtension(format)}`
  const payload = {
    format,
    pageIds: getExportPageIds(scope),
    includePageDelimiters: options.includePageDelimiters,
    textLevel: normalizeTextLevel(options.textLevel),
    textVariantIndex: Number.isFinite(options.textVariantIndex) ? options.textVariantIndex : 0,
    pdfProfile: normalizePdfProfile(options.pdfProfile),
    teiProfile: normalizeTeiProfile(options.teiProfile),
    spreadsheetProfiles: normalizeSpreadsheetProfiles(options.spreadsheetProfiles),
    docxOptions: normalizeDocxOptions(options.docxOptions)
  }

  try {
    const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    if (!response.ok) {
      throw new Error(`Export failed (${response.status})`)
    }

    await downloadBlobResponse(response, fallbackName)

    toast.add({
      title: 'Project output exported',
      color: 'success',
      icon: 'i-lucide-download'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export project output'
    toast.add({
      title: 'Export failed',
      description: message,
      color: 'error'
    })
  }
}

async function exportPageOutput(page: Page) {
  const options = await requestExportOptions('page')
  if (!options) return

  const format = normalizeExportFormat(options.format)
  if (!format) return

  if (format === 'PAGE_XML') {
    await exportPageXml(page, options.targetPageXmlVersion)
    return
  }

  const fallbackName = `${page.name}.${formatExtension(format)}`

  try {
    const response = await fetch(`/api/projects/${projectId}/pages/${page.id}/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        format,
        targetPageXmlVersion: options.targetPageXmlVersion,
        textLevel: normalizeTextLevel(options.textLevel),
        textVariantIndex: Number.isFinite(options.textVariantIndex) ? options.textVariantIndex : 0,
        pdfProfile: normalizePdfProfile(options.pdfProfile),
        teiProfile: normalizeTeiProfile(options.teiProfile),
        spreadsheetProfiles: normalizeSpreadsheetProfiles(options.spreadsheetProfiles),
        docxOptions: normalizeDocxOptions(options.docxOptions)
      })
    })

    if (!response.ok) {
      throw new Error(`Export failed (${response.status})`)
    }

    await downloadBlobResponse(response, fallbackName)

    toast.add({
      title: 'Page output exported',
      color: 'success',
      icon: 'i-lucide-download'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export output'
    toast.add({
      title: 'Export failed',
      description: message,
      color: 'error'
    })
  }
}

async function exportPageXml(page: Page, targetPageXmlVersion?: string) {
  try {
    const xmlFiles = await $fetch<{ id: string }[]>(`/api/projects/${projectId}/pages/${page.id}/xml`)
    if (!xmlFiles?.length) {
      toast.add({
        title: 'No XML files',
        description: 'This page has no XML files to export.',
        color: 'warning'
      })
      return
    }

    const xmlId = xmlFiles[0]!.id
    const selectedVersion = targetPageXmlVersion ?? PAGE_XML_PRIMARY_VERSION
    const query = new URLSearchParams({ targetPageXmlVersion: selectedVersion })
    const response = await fetch(`/api/projects/${projectId}/pages/xml/${xmlId}/export?${query.toString()}`)
    if (!response.ok) {
      throw new Error(`Export failed (${response.status})`)
    }

    await downloadBlobResponse(response, `${page.name}.xml`)

    if (xmlFiles.length > 1) {
      toast.add({
        title: 'Multiple XML files found',
        description: 'Exported the first XML variant for this page.',
        color: 'info'
      })
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export XML'
    toast.add({
      title: 'Export failed',
      description: message,
      color: 'error'
    })
  }
}

const PAGE_XML_PRIMARY_VERSION = '2019-07-15'

function normalizePageXmlVersion(value: unknown): string {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    const match = trimmed.match(/\d{4}-\d{2}-\d{2}/)
    return match ? match[0] : PAGE_XML_PRIMARY_VERSION
  }
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return normalizePageXmlVersion(value.value)
  }
  return PAGE_XML_PRIMARY_VERSION
}

function normalizeExportFormat(value: unknown): ExportFormat | null {
  if (typeof value === 'string') {
    return value as ExportFormat
  }
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return value.value as ExportFormat
  }
  return null
}

function normalizeTextLevel(value: unknown): TextLevel {
  if (typeof value === 'string' && ['PAGE', 'REGION', 'TEXT_LINE'].includes(value)) {
    return value as TextLevel
  }
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return normalizeTextLevel(value.value)
  }
  return 'PAGE'
}

function normalizePdfProfile(value: unknown): PdfProfile {
  if (typeof value === 'string' && ['SEARCHABLE', 'IMAGES_ONLY', 'TEXT_PAGES', 'PDFA_SEARCHABLE'].includes(value)) {
    return value as PdfProfile
  }
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return normalizePdfProfile(value.value)
  }
  return 'SEARCHABLE'
}

function normalizeTeiProfile(value: unknown): TeiProfile {
  if (typeof value === 'string' && ['STANDARD', 'LAYOUT'].includes(value)) {
    return value as TeiProfile
  }
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return normalizeTeiProfile(value.value)
  }
  return 'STANDARD'
}

function normalizeSpreadsheetProfiles(value: unknown): SpreadsheetProfile[] {
  if (Array.isArray(value)) {
    return value
      .map(item => typeof item === 'string' ? item : (item && typeof item === 'object' && 'value' in item && typeof item.value === 'string' ? item.value : null))
      .filter((item): item is SpreadsheetProfile => item === 'PAGE_METADATA' || item === 'TAGS' || item === 'REGIONS')
  }
  return ['PAGE_METADATA']
}

function normalizeDocxOptions(value: unknown): DocxOptions {
  const source = value && typeof value === 'object' ? value as Partial<DocxOptions> : {}
  return {
    preserveLineBreaks: source.preserveLineBreaks !== false,
    forcePageBreaks: source.forcePageBreaks !== false,
    includeImageNames: source.includeImageNames === true,
    markUnclearWords: source.markUnclearWords === true
  }
}

function formatExtension(format: ExportFormat): string {
  switch (format) {
    case 'PAGE_XML':
      return 'xml'
    case 'ALTO_XML':
      return 'alto.xml'
    case 'TXT':
      return 'txt'
    case 'PDF':
      return 'pdf'
    case 'DOCX':
      return 'docx'
    case 'TEI':
      return 'tei.xml'
    case 'CSV':
      return 'csv'
    case 'XLSX':
      return 'xlsx'
  }
}

async function downloadBlobResponse(response: Response, fallbackName: string) {
  const blob = await response.blob()
  const contentDisposition = response.headers.get('content-disposition')
  const match = contentDisposition?.match(/filename\*?=(?:UTF-8''|"?)([^";]+)/i)
  const fileName = match ? decodeURIComponent(match[1]!) : fallbackName

  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

const nextReleaseTag = computed(() => {
  const maxVersion = (releases.value || []).reduce((currentMax, release) => Math.max(currentMax, Number(release.versionNumber) || 0), 0)
  return `v${maxVersion + 1}`
})

const isReleaseSidebarVisible = ref(false)

const releasesForSidebar = computed(() => {
  const source = [...(releases.value ?? [])]

  return source.sort((a, b) => {
    const versionDiff = (Number(b.versionNumber) || 0) - (Number(a.versionNumber) || 0)
    if (versionDiff !== 0) return versionDiff

    const createdDiff = new Date(b.created).getTime() - new Date(a.created).getTime()
    if (createdDiff !== 0) return createdDiff

    return new Date(b.updated).getTime() - new Date(a.updated).getTime()
  })
})

const latestReleaseId = computed(() => releasesForSidebar.value[0]?.id ?? null)
const latestReleaseUpdatedAt = computed(() => releasesForSidebar.value[0]?.updated ?? releasesForSidebar.value[0]?.created ?? null)
const releaseSidebarSummary = computed(() => {
  const count = releasesForSidebar.value.length
  const countLabel = `${count} ${count === 1 ? 'release' : 'releases'}`

  if (!latestReleaseUpdatedAt.value) {
    return countLabel
  }

  return `${countLabel} · Last updated ${formatDate(latestReleaseUpdatedAt.value)}`
})

function formatDate(value?: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium'
  }).format(new Date(value))
}

async function requestExportOptions(mode: ExportDialogMode): Promise<ExportDialogResult | null> {
  const selector = exportTargetSlideover.open({
    mode,
    title: mode === 'page'
      ? 'Export Page'
      : mode === 'project'
        ? 'Export Project Output'
        : 'Export Project Package',
    description: mode === 'page'
      ? 'Choose the page export format and options.'
      : mode === 'project'
        ? 'Choose the project output format and options.'
        : 'Choose package export options and optional embedded outputs.',
    initialTargetVersion: PAGE_XML_PRIMARY_VERSION,
    confirmLabel: mode === 'package' ? 'Export Package' : 'Export'
  })

  const result = await selector.result as ExportDialogResult | null
  if (!result) {
    return null
  }

  const normalizedFormat = normalizeExportFormat(result.format)
  const normalizedTargetVersion = normalizePageXmlVersion(result.targetPageXmlVersion)
  const confirmedTarget = await confirmLegacyPageXmlVersion(normalizedTargetVersion)
  if (!confirmedTarget) {
    return null
  }

  return {
    ...result,
    format: normalizedFormat,
    targetPageXmlVersion: confirmedTarget,
    textLevel: normalizeTextLevel(result.textLevel),
    textVariantIndex: Number.isFinite(result.textVariantIndex) ? result.textVariantIndex : 0,
    pdfProfile: normalizePdfProfile(result.pdfProfile),
    teiProfile: normalizeTeiProfile(result.teiProfile),
    spreadsheetProfiles: normalizeSpreadsheetProfiles(result.spreadsheetProfiles),
    docxOptions: normalizeDocxOptions(result.docxOptions),
    embeddedOutputs: result.embeddedOutputs
      .flatMap((output) => {
        const format = normalizeExportFormat(output.format)
        if (!format || format === 'PAGE_XML') return []

        return [{
          format,
          includePageDelimiters: output.includePageDelimiters,
          textLevel: normalizeTextLevel(output.textLevel),
          textVariantIndex: Number.isFinite(output.textVariantIndex) ? output.textVariantIndex : 0,
          pdfProfile: normalizePdfProfile(output.pdfProfile),
          teiProfile: normalizeTeiProfile(output.teiProfile),
          spreadsheetProfiles: normalizeSpreadsheetProfiles(output.spreadsheetProfiles),
          docxOptions: normalizeDocxOptions(output.docxOptions)
        }]
      })
  }
}

async function confirmLegacyPageXmlVersion(selectedVersion: string): Promise<string | null> {
  if (selectedVersion === PAGE_XML_PRIMARY_VERSION) {
    return selectedVersion
  }

  const confirmation = confirmSlideover.open({
    title: 'Confirm Legacy PAGE XML Export',
    message: 'Exporting to an older PAGE XML schema may drop PAGE 2019-only data. Continue anyway?',
    confirmLabel: 'Export anyway',
    confirmColor: 'warning',
    confirmIcon: 'i-lucide-triangle-alert'
  })

  const confirmed = await confirmation.result as boolean
  return confirmed ? selectedVersion : null
}

const actionItems = computed<DropdownMenuItem[][]>(() => {
  const utilityItems: DropdownMenuItem[] = [
    {
      type: 'label',
      label: 'Utilities'
    },
    {
      label: 'Generate codec (full project)',
      icon: 'i-lucide-wand-sparkles',
      disabled: (pages.value?.length ?? 0) === 0,
      onSelect: () => {
        void openCodecGenerateSlideover('all')
      }
    },
    {
      label: 'Validate codec (full project)',
      icon: 'i-lucide-badge-check',
      disabled: (pages.value?.length ?? 0) === 0,
      onSelect: () => {
        void openCodecValidateSlideover('all')
      }
    },
    {
      label: 'Validate dictionary (full project)',
      icon: 'i-lucide-book-check',
      disabled: (pages.value?.length ?? 0) === 0,
      onSelect: () => {
        void openDictionaryValidationModal('all')
      }
    },
    {
      label: 'Normalization (full project)',
      icon: 'i-lucide-wand-sparkles',
      disabled: (pages.value?.length ?? 0) === 0,
      onSelect: () => {
        void openNormalizationPreviewModal('all')
      }
    },
    {
      label: 'Validate ruleset (full project)',
      icon: 'i-lucide-shield-alert',
      disabled: (pages.value?.length ?? 0) === 0,
      onSelect: () => {
        void openValidationRulesetModal('all')
      }
    }
  ]

  const exportItems: DropdownMenuItem[] = [
    {
      type: 'label',
      label: 'Export'
    },
    {
      label: 'Export output (full project)',
      icon: 'i-lucide-file-output',
      disabled: (pages.value?.length ?? 0) === 0 || !allow(projectCapabilities.value.canExportPackage),
      onSelect: () => {
        void exportProjectOutput('all')
      }
    },
    {
      label: 'Export package (full project)',
      icon: 'i-lucide-file-archive',
      disabled: (pages.value?.length ?? 0) === 0 || !allow(projectCapabilities.value.canExportPackage),
      onSelect: () => {
        void exportProjectPackage('all')
      }
    }
  ]

  const projectItems: DropdownMenuItem[] = [
    {
      type: 'label',
      label: 'Project'
    }
  ]

  if (allow(projectCapabilities.value.canEdit)) {
    projectItems.push({
      label: 'Import IIIF',
      icon: 'i-lucide-image-plus',
      disabled: project.value?.locked,
      onSelect: () => {
        void openIiifImportSlideover()
      }
    })

    projectItems.push({
      label: 'Edit project',
      icon: 'i-lucide-edit',
      disabled: project.value?.locked,
      onSelect: async () => {
        if (!project.value) return
        const instance = projectEditSlideover.open({ project: normalizeProjectForEdit(project.value) })
        const updated = await instance.result
        if (updated) {
          await refreshProject()
        }
      }
    })
  }

  if (allow(projectCapabilities.value.canShare)) {
    projectItems.push({
      label: 'Share project',
      icon: 'i-lucide-share-2',
      disabled: project.value?.locked,
      onSelect: async () => {
        if (!project.value) return
        const instance = projectShareSlideover.open({
          resourceId: project.value.id,
          resourceName: project.value.name,
          resourceType: 'PROJECT',
          currentWorkspaceId: selectedWorkspace.value ?? ''
        })
        const transferred = await instance.result
        if (transferred) {
          await refreshProject()
        }
      }
    })
  }

  if (allow(projectCapabilities.value.canDelete)) {
    projectItems.push({
      label: 'Delete project',
      icon: 'i-lucide-trash',
      color: 'error' as const,
      disabled: project.value?.locked || isDeletingProject.value,
      onSelect: handleDeleteProject
    })
  }

  return [projectItems, exportItems, utilityItems].filter(group => group.length > 1)
})

const autoCreatedDescription = 'Auto-created from bulk upload'

const getPageDescription = (page: Page) => {
  const description = page.description?.trim()
  if (!description || description === autoCreatedDescription) return ''
  return page.description
}

const pagesSafe = computed(() => (pages.value ?? []).map((page) => {
  const description = getPageDescription(page)
  if (description === page.description) return page
  return { ...page, description }
}))
const {
  sort,
  globalFilter,
  columnFilters,
  tagFilterOperator,
  filteredAndSortedData: filteredAndSortedPages,
  setColumnFilter,
  clearColumnFilter,
  resetAllFilters
} = useTableFilters(pagesSafe, { column: 'name', direction: 'asc' })

const selectedPageIds = ref<Set<string>>(new Set())
const hasSelection = computed(() => selectedPageIds.value.size > 0)
const canBulkDeletePages = computed(() =>
  allow(projectCapabilities.value.canDeletePages) && !project.value?.locked
)

function togglePageSelection(pageId: string) {
  const newSet = new Set(selectedPageIds.value)
  if (newSet.has(pageId)) {
    newSet.delete(pageId)
  } else {
    newSet.add(pageId)
  }
  selectedPageIds.value = newSet
}

function toggleAllPages() {
  if (selectedPageIds.value.size === filteredPages.value.length) {
    selectedPageIds.value = new Set()
  } else {
    selectedPageIds.value = new Set(filteredPages.value.map(p => p.id))
  }
}

function clearSelection() {
  selectedPageIds.value = new Set()
}

const page = ref(1)
const itemsPerPage = ref(25)
const totalItems = computed(() => filteredPages.value.length)
const totalPagesCount = computed(() => Math.ceil(totalItems.value / itemsPerPage.value))
const paginatedPages = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  return filteredPages.value.slice(start, start + itemsPerPage.value)
})

async function openAddToDatasetSlideover() {
  if (!hasSelection.value || !canManageDatasets.value) return

  const selectedPages = Array.from(selectedPageIds.value).map((pageId) => {
    const sourcePage = pages.value?.find(page => page.id === pageId)
    return {
      id: pageId,
      name: sourcePage?.name || pageId
    }
  })

  const instance = addToDatasetSlideover.open({
    projectId,
    projectName: project.value?.name ?? 'Project',
    projectTags: project.value?.tags ?? [],
    pages: selectedPages
  })
  const result = await instance.result as { datasetId: string, addedCount: number, skippedCount: number } | null
  if (!result) return

  clearSelection()
}

async function openBulkDeleteSlideover() {
  if (!canBulkDeletePages.value) return
  if (!hasSelection.value) return

  const selectedPages = Array.from(selectedPageIds.value).map((pageId) => {
    return {
      id: pageId,
      name: pages.value?.find(p => p.id === pageId)?.name || pageId
    }
  })

  const instance = bulkDeletePagesSlideover.open({
    projectId,
    projectName: project.value?.name ?? 'Project',
    pages: selectedPages
  })
  const result = await instance.result
  if (!result) return

  clearSelection()
  await Promise.all([
    refreshPagesData(),
    refreshProject()
  ])
}

function getScopedPageIds(scope: ProjectActionScope): string[] {
  return scope === 'selection' ? Array.from(selectedPageIds.value) : []
}

function getExportPageIds(scope: ProjectActionScope): string[] | null {
  return scope === 'selection' ? Array.from(selectedPageIds.value) : null
}

function getCodecSources(scope: ProjectActionScope): CodecProjectScope[] {
  return [{ projectId, pageIds: getScopedPageIds(scope) }]
}

function getDictionarySources(scope: ProjectActionScope): DictionaryProjectScope[] {
  return [{ projectId, pageIds: getScopedPageIds(scope) }]
}

function getNormalizationSources(scope: ProjectActionScope): NormalizationProjectScope[] {
  return [{ projectId, pageIds: getScopedPageIds(scope) }]
}

function getValidationSources(scope: ProjectActionScope): ValidationProjectScope[] {
  return [{ projectId, pageIds: getScopedPageIds(scope) }]
}

type NormalizationDiffSegment = {
  text: string
  changed: boolean
  kind: 'equal' | 'insert' | 'delete'
}

type NormalizationPreviewRow = NormalizePreview & {
  key: string
  originalSegments: NormalizationDiffSegment[]
  normalizedSegments: NormalizationDiffSegment[]
}

const dictionaryValidationResult = ref<DictionaryValidateAgainstSourcesResponse | null>(null)
const isDictionaryValidationModalOpen = ref(false)
const isDictionaryValidationLoading = ref(false)
const normalizationPreviewResult = ref<NormalizeSourcesResponse | null>(null)
const isNormalizationPreviewSlideoverOpen = ref(false)
const isNormalizationPreviewLoading = ref(false)
const isApplyingNormalization = ref(false)
const normalizationApplyingRowKey = ref<string | null>(null)
const activeNormalizationProfile = ref<NormalizationProfile | null>(null)
const isNormalizationProfileLoading = ref(false)
const normalizationProfileLoadError = ref<string | null>(null)
const isNormalizationRulesSlideoverOpen = ref(false)
const normalizationActionScope = ref<ProjectActionScope>('all')
const normalizationVariantMode = ref<'PROJECT_GT' | 'CUSTOM'>('PROJECT_GT')
const normalizationVariantIndexInput = ref<number>(0)
const validationRulesetResult = ref<ValidateAgainstSourcesResponse | null>(null)
const isValidationRulesetModalOpen = ref(false)
const isValidationRulesetLoading = ref(false)

let normalizationDmp: DiffMatchPatch | null = null

function getNormalizationDmp(): DiffMatchPatch {
  if (!normalizationDmp) {
    normalizationDmp = new DiffMatchPatch()
  }
  return normalizationDmp
}

function buildNormalizationDiffSegments(originalText: string, normalizedText: string, side: 'original' | 'normalized'): NormalizationDiffSegment[] {
  const dmp = getNormalizationDmp()
  const diffs = dmp.diff_main(originalText || '', normalizedText || '') as Diff[]
  dmp.diff_cleanupSemantic(diffs)

  const segments: NormalizationDiffSegment[] = []
  for (const diff of diffs) {
    const [operation, text] = diff
    if (!text) continue

    if (operation === 0) {
      segments.push({ text, changed: false, kind: 'equal' })
      continue
    }

    if (operation === -1 && side === 'original') {
      segments.push({ text, changed: true, kind: 'delete' })
      continue
    }

    if (operation === 1 && side === 'normalized') {
      segments.push({ text, changed: true, kind: 'insert' })
    }
  }

  return segments.length > 0 ? segments : [{ text: side === 'original' ? originalText : normalizedText, changed: false, kind: 'equal' }]
}

const normalizationPreviewRows = computed<NormalizationPreviewRow[]>(() =>
  (normalizationPreviewResult.value?.previews ?? []).map(preview => ({
    ...preview,
    key: [preview.pageId, preview.textLineId ?? preview.regionId ?? 'row', preview.variantIndex ?? 'primary', preview.originalText, preview.normalizedText].join('::'),
    originalSegments: buildNormalizationDiffSegments(preview.originalText || '', preview.normalizedText || '', 'original'),
    normalizedSegments: buildNormalizationDiffSegments(preview.originalText || '', preview.normalizedText || '', 'normalized')
  }))
)

const normalizationPresetRules = computed<Array<{ key: NormalizationPresetRuleKey, label: string, enabled: boolean, value: string }>>(() => {
  const profile = activeNormalizationProfile.value
  if (!profile) return []

  return [
    {
      key: 'unicodeNormalization',
      label: 'Unicode normalization',
      enabled: profile.unicodeNormalization !== 'NONE',
      value: profile.unicodeNormalization === 'NONE' ? 'Disabled' : profile.unicodeNormalization
    },
    { key: 'collapseWhitespace', label: 'Collapse whitespace', enabled: profile.collapseWhitespace, value: profile.collapseWhitespace ? 'Enabled' : 'Disabled' },
    { key: 'trimText', label: 'Trim text', enabled: profile.trimText, value: profile.trimText ? 'Enabled' : 'Disabled' },
    { key: 'dehyphenateLineBreaks', label: 'Dehyphenate line breaks', enabled: profile.dehyphenateLineBreaks, value: profile.dehyphenateLineBreaks ? 'Enabled' : 'Disabled' },
    { key: 'mapLongSToS', label: 'Map long s to s', enabled: profile.mapLongSToS, value: profile.mapLongSToS ? 'Enabled' : 'Disabled' },
    { key: 'expandCommonLigatures', label: 'Expand common ligatures', enabled: profile.expandCommonLigatures, value: profile.expandCommonLigatures ? 'Enabled' : 'Disabled' },
    { key: 'normalizeQuotes', label: 'Normalize quotes', enabled: profile.normalizeQuotes, value: profile.normalizeQuotes ? 'Enabled' : 'Disabled' },
    { key: 'normalizeDashes', label: 'Normalize dashes', enabled: profile.normalizeDashes, value: profile.normalizeDashes ? 'Enabled' : 'Disabled' },
    { key: 'normalizeEllipsis', label: 'Normalize ellipsis', enabled: profile.normalizeEllipsis, value: profile.normalizeEllipsis ? 'Enabled' : 'Disabled' }
  ]
})

const enabledNormalizationPresetRuleCount = computed(() =>
  normalizationPresetRules.value.filter(rule => rule.enabled).length
)

const normalizationManualRuleCount = computed(() =>
  activeNormalizationProfile.value?.replacementRules.length ?? 0
)

const defaultNormalizationVariantIndex = computed(() => {
  const value = project.value?.defaultGtIndex
  return Number.isInteger(value) && Number(value) >= 0 ? Number(value) : 0
})

const normalizedCustomNormalizationVariantIndex = computed(() => {
  const parsed = Number(normalizationVariantIndexInput.value)
  if (!Number.isInteger(parsed) || parsed < 0) {
    return null
  }
  return parsed
})

const effectiveNormalizationVariantIndex = computed(() =>
  normalizationVariantMode.value === 'PROJECT_GT'
    ? defaultNormalizationVariantIndex.value
    : normalizedCustomNormalizationVariantIndex.value
)

watch(defaultNormalizationVariantIndex, (value) => {
  if (normalizationVariantMode.value === 'PROJECT_GT') {
    normalizationVariantIndexInput.value = value
  }
}, { immediate: true })

function buildNormalizationRequestBody(targets?: NormalizeTarget[]) {
  return {
    sources: getNormalizationSources(normalizationActionScope.value),
    variantIndex: effectiveNormalizationVariantIndex.value,
    ...(targets && targets.length > 0 ? { targets } : {})
  }
}

function buildNormalizationRowTargets(preview: NormalizationPreviewRow): NormalizeTarget[] {
  return [{
    pageId: preview.pageId,
    textLineId: preview.textLineId ?? null,
    regionId: preview.regionId ?? null,
    variantIndex: preview.variantIndex ?? null
  }]
}

async function loadActiveNormalizationProfile() {
  if (!selectedWorkspace.value || !project.value?.normalizationProfileId) {
    activeNormalizationProfile.value = null
    normalizationProfileLoadError.value = null
    return
  }

  isNormalizationProfileLoading.value = true
  normalizationProfileLoadError.value = null

  try {
    activeNormalizationProfile.value = await $fetch<NormalizationProfile>(
      `/api/workspaces/${selectedWorkspace.value}/normalization-profiles/${project.value.normalizationProfileId}`
    )
  } catch (error) {
    activeNormalizationProfile.value = null
    normalizationProfileLoadError.value = getErrorMessage(error, 'Could not load normalization profile details.')
  } finally {
    isNormalizationProfileLoading.value = false
  }
}

async function loadNormalizationPreview(closeOnError = false) {
  if (!selectedWorkspace.value || !project.value?.normalizationProfileId) {
    return
  }
  if (effectiveNormalizationVariantIndex.value === null) {
    toast.add({
      title: 'Invalid normalization index',
      description: 'The target text index must be a non-negative integer.',
      color: 'warning'
    })
    return
  }

  try {
    normalizationPreviewResult.value = null
    isNormalizationPreviewLoading.value = true
    normalizationPreviewResult.value = await $fetch<NormalizeSourcesResponse>(
      `/api/workspaces/${selectedWorkspace.value}/normalization-profiles/${project.value.normalizationProfileId}/normalize-sources`,
      {
        method: 'POST',
        body: buildNormalizationRequestBody()
      }
    )
  } catch (error) {
    if (closeOnError) {
      isNormalizationPreviewSlideoverOpen.value = false
    }
    toast.add({
      title: 'Normalization preview failed',
      description: getErrorMessage(error, 'Could not load normalization details for this project.'),
      color: 'error'
    })
  } finally {
    isNormalizationPreviewLoading.value = false
  }
}

async function openCodecGenerateSlideover(scope: ProjectActionScope = 'all') {
  if (!selectedWorkspace.value) return

  const instance = codecActionSlideover.open({
    mode: 'generate',
    workspaceId: selectedWorkspace.value,
    sources: getCodecSources(scope)
  })
  const result = await instance.result as GenerateCodecFromSourcesResponse | null
  if (!result) return

  await Promise.all([
    refreshNuxtData(wsKey(selectedWorkspace.value, 'codecs', 'list')),
    refreshProject()
  ])
}

async function openCodecValidateSlideover(scope: ProjectActionScope = 'all') {
  if (!selectedWorkspace.value || !project.value?.codecId) {
    toast.add({
      title: 'No project codec configured',
      description: 'Assign a codec to this project first, then run validation.',
      color: 'warning'
    })
    return
  }

  const instance = codecActionSlideover.open({
    mode: 'validate',
    workspaceId: selectedWorkspace.value,
    sources: getCodecSources(scope),
    defaultCodecId: project.value.codecId
  })
  await instance.result as ValidateCodecAgainstSourcesResponse | null
}

async function openDictionaryValidationModal(scope: ProjectActionScope = 'all') {
  if (!selectedWorkspace.value || !project.value?.dictionaryId) {
    toast.add({
      title: 'No project dictionary configured',
      description: 'Assign a dictionary to this project first, then run validation.',
      color: 'warning'
    })
    return
  }

  try {
    dictionaryValidationResult.value = null
    isDictionaryValidationModalOpen.value = true
    isDictionaryValidationLoading.value = true
    const result = await $fetch<DictionaryValidateAgainstSourcesResponse>(
      `/api/workspaces/${selectedWorkspace.value}/dictionaries/${project.value.dictionaryId}/validate-against-sources`,
      {
        method: 'POST',
        body: {
          sources: getDictionarySources(scope)
        }
      }
    )
    dictionaryValidationResult.value = result
  } catch (error) {
    isDictionaryValidationModalOpen.value = false
    toast.add({
      title: 'Dictionary validation failed',
      description: getErrorMessage(error, 'Could not validate project text against the dictionary.'),
      color: 'error'
    })
  } finally {
    isDictionaryValidationLoading.value = false
  }
}

async function openNormalizationPreviewModal(scope: ProjectActionScope = 'all') {
  if (!selectedWorkspace.value || !project.value?.normalizationProfileId) {
    toast.add({
      title: 'No project normalization profile configured',
      description: 'Assign a normalization profile to this project first, then run a preview.',
      color: 'warning'
    })
    return
  }

  normalizationActionScope.value = scope
  normalizationVariantIndexInput.value = defaultNormalizationVariantIndex.value
  isNormalizationPreviewSlideoverOpen.value = true
  await Promise.all([
    loadActiveNormalizationProfile(),
    loadNormalizationPreview(true)
  ])
}

async function openNormalizationRulesSlideover() {
  isNormalizationRulesSlideoverOpen.value = true

  if (!activeNormalizationProfile.value && !isNormalizationProfileLoading.value) {
    await loadActiveNormalizationProfile()
  }
}

async function applyNormalizationPreview(options: {
  targets?: NormalizeTarget[]
  rowKey?: string | null
  closeOnSuccess?: boolean
} = {}) {
  if (!selectedWorkspace.value || !project.value?.normalizationProfileId) {
    return
  }
  if (project.value?.locked) {
    toast.add({
      title: 'Project is locked',
      description: project.value.lockedReason || 'Unlock the project before applying normalization changes.',
      color: 'warning'
    })
    return
  }
  if (effectiveNormalizationVariantIndex.value === null) {
    toast.add({
      title: 'Invalid normalization index',
      description: 'The target text index must be a non-negative integer.',
      color: 'warning'
    })
    return
  }

  const { targets, rowKey = null, closeOnSuccess = rowKey === null } = options

  try {
    if (rowKey) {
      normalizationApplyingRowKey.value = rowKey
    } else {
      isApplyingNormalization.value = true
    }

    const response = await $fetch<ApplySourcesResponse>(
      `/api/workspaces/${selectedWorkspace.value}/normalization-profiles/${project.value.normalizationProfileId}/apply-sources`,
      {
        method: 'POST',
        body: buildNormalizationRequestBody(targets)
      }
    )

    toast.add({
      title: response.changedRowCount > 0
        ? (rowKey ? 'Row normalization applied' : 'Normalization applied')
        : (rowKey ? 'No row normalization changes applied' : 'No normalization changes applied'),
      description: response.message,
      color: response.changedRowCount > 0 ? 'success' : 'info',
      icon: response.changedRowCount > 0 ? 'i-lucide-check' : 'i-lucide-info'
    })

    normalizationPreviewResult.value = null
    if (closeOnSuccess) {
      isNormalizationPreviewSlideoverOpen.value = false
    } else {
      await loadNormalizationPreview()
    }
  } catch (error) {
    toast.add({
      title: 'Normalization apply failed',
      description: getErrorMessage(error, 'Could not apply normalization changes to this project.'),
      color: 'error'
    })
  } finally {
    if (rowKey) {
      normalizationApplyingRowKey.value = null
    } else {
      isApplyingNormalization.value = false
    }
  }
}

async function applyNormalizationRow(preview: NormalizationPreviewRow) {
  await applyNormalizationPreview({
    targets: buildNormalizationRowTargets(preview),
    rowKey: preview.key,
    closeOnSuccess: false
  })
}

async function openValidationRulesetModal(scope: ProjectActionScope = 'all') {
  if (!selectedWorkspace.value || !project.value?.validationRulesetId) {
    toast.add({
      title: 'No project validation ruleset configured',
      description: 'Assign a validation ruleset to this project first, then run validation.',
      color: 'warning'
    })
    return
  }

  try {
    validationRulesetResult.value = null
    isValidationRulesetModalOpen.value = true
    isValidationRulesetLoading.value = true
    validationRulesetResult.value = await $fetch<ValidateAgainstSourcesResponse>(
      `/api/workspaces/${selectedWorkspace.value}/validation-rulesets/${project.value.validationRulesetId}/validate-against-sources`,
      {
        method: 'POST',
        body: { sources: getValidationSources(scope) }
      }
    )
  } catch (error) {
    isValidationRulesetModalOpen.value = false
    toast.add({
      title: 'Validation ruleset check failed',
      description: getErrorMessage(error, 'Could not validate project text against the selected ruleset.'),
      color: 'error'
    })
  } finally {
    isValidationRulesetLoading.value = false
  }
}

const xmlStatusFilter = ref<'all' | 'has_xml' | 'no_xml'>('all')

const xmlStatusOptions = [
  { value: 'all', label: 'All Pages' },
  { value: 'has_xml', label: 'With XML' },
  { value: 'no_xml', label: 'Without XML' }
]

const filteredPages = computed(() => {
  let result = filteredAndSortedPages.value
  if (xmlStatusFilter.value !== 'all') {
    result = result.filter((page) => {
      const hasXml = page.xmlFileCount > 0
      return xmlStatusFilter.value === 'has_xml' ? hasXml : !hasXml
    })
  }
  return result
})

watch([globalFilter, columnFilters, xmlStatusFilter], () => {
  page.value = 1
}, { deep: true })

const resetFilters = () => {
  resetAllFilters()
  xmlStatusFilter.value = 'all'
}

const selectionMoreActionItems = computed<DropdownMenuItem[][]>(() => {
  const exportItems: DropdownMenuItem[] = [
    {
      type: 'label',
      label: 'Export'
    },
    {
      label: 'Export Output (selected pages)',
      icon: 'i-lucide-file-output',
      disabled: !hasSelection.value || !allow(projectCapabilities.value.canExportPackage),
      onSelect: () => {
        void exportProjectOutput('selection')
      }
    },
    {
      label: 'Export package (selected pages)',
      icon: 'i-lucide-file-archive',
      disabled: !hasSelection.value || !allow(projectCapabilities.value.canExportPackage),
      onSelect: () => {
        void exportProjectPackage('selection')
      }
    }
  ]

  const utilityItems: DropdownMenuItem[] = [
    {
      type: 'label',
      label: 'Utilities'
    },
    {
      label: 'Generate codec (selected pages)',
      icon: 'i-lucide-wand-sparkles',
      disabled: !hasSelection.value,
      onSelect: () => {
        void openCodecGenerateSlideover('selection')
      }
    },
    {
      label: 'Validate codec (selected pages)',
      icon: 'i-lucide-badge-check',
      disabled: !hasSelection.value,
      onSelect: () => {
        void openCodecValidateSlideover('selection')
      }
    },
    {
      label: 'Validate dictionary (selected pages)',
      icon: 'i-lucide-book-check',
      disabled: !hasSelection.value,
      onSelect: () => {
        void openDictionaryValidationModal('selection')
      }
    },
    {
      label: 'Normalization (selected pages)',
      icon: 'i-lucide-wand-sparkles',
      disabled: !hasSelection.value,
      onSelect: () => {
        void openNormalizationPreviewModal('selection')
      }
    },
    {
      label: 'Validate ruleset (selected pages)',
      icon: 'i-lucide-shield-alert',
      disabled: !hasSelection.value,
      onSelect: () => {
        void openValidationRulesetModal('selection')
      }
    }
  ]

  return [exportItems, utilityItems]
})

const uniqueTags = computed(() => {
  if (!pages.value) return []
  const tagCounts = new Map<string, number>()
  pages.value.forEach((page) => {
    page.tags.forEach((tag) => {
      tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1)
    })
  })
  return Array.from(tagCounts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([tag, count]) => ({ label: tag, value: tag, count }))
})

const selectedTags = computed({
  get: () => {
    const tags = columnFilters.value['tags']
    return Array.isArray(tags) ? tags : []
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

function renderIndexingStatusBadge(status?: PageIndexingStatus) {
  const value = status ?? 'NOT_APPLICABLE'
  if (value === 'INDEXED') {
    return h(UBadge, { color: 'success', variant: 'soft', size: 'sm' }, () => 'Yes')
  }
  if (value === 'INDEXING') {
    return h(UBadge, { color: 'warning', variant: 'soft', size: 'sm' }, () => [
      h('span', { class: 'inline-block w-1.5 h-1.5 rounded-full bg-amber-500 mr-1 animate-pulse' }),
      'Indexing'
    ])
  }
  if (value === 'UNINDEXED') {
    return h(UBadge, { color: 'error', variant: 'soft', size: 'sm' }, () => 'No')
  }
  return h(UBadge, { color: 'neutral', variant: 'soft', size: 'sm' }, () => 'Empty')
}

function renderCollaborationSummaryCell(page: Page) {
  const summary = getPageCollaborationSummary(page.id)
  if (!summary) return null

  const detailLines = [
    summary.editor
      ? `${summary.editor.user.displayName} editing (${summary.isLive ? 'Live' : 'Idle'})`
      : null,
    summary.viewerCount > 0
      ? `${summary.viewerCount} viewer${summary.viewerCount === 1 ? '' : 's'}`
      : null,
    summary.hasPendingTakeover ? 'Pending request' : null
  ].filter((value): value is string => Boolean(value))

  if (!summary.editor) {
    return h(UPopover, {
      mode: 'hover',
      content: { side: 'top' }
    }, {
      default: () => h(UBadge, {
        color: 'info',
        variant: 'soft',
        size: 'sm'
      }, () => 'Watching'),
      content: () => h('div', { class: 'p-3 w-56 space-y-1.5' }, [
        h('p', { class: 'text-xs font-medium text-highlighted' }, 'Page activity'),
        ...detailLines.map(line => h('p', { class: 'text-xs text-muted' }, line))
      ])
    })
  }

  const editor = summary.editor
  const avatarRingClass = summary.isLive ? 'ring-emerald-400/90' : 'ring-neutral-400/90'

  return h(UPopover, {
    mode: 'hover',
    content: { side: 'top' }
  }, {
    default: () => h('div', { class: 'flex items-center justify-center' }, [
      h(UAvatar, {
        src: resolveManagedProfileAvatarSrc(editor.user.avatar),
        alt: editor.user.displayName,
        text: getAvatarInitials({
          name: editor.user.displayName,
          username: editor.user.username
        }),
        size: 'sm',
        class: `ring-2 ${avatarRingClass}`
      })
    ]),
    content: () => h('div', { class: 'p-3 w-56 space-y-1.5' }, [
      h('p', { class: 'text-xs font-medium text-highlighted' }, 'Page activity'),
      ...detailLines.map(line => h('p', { class: 'text-xs text-muted' }, line))
    ])
  })
}

const pageColumns = [
  {
    id: 'select',
    header: () => h('input', {
      type: 'checkbox',
      checked: selectedPageIds.value.size === filteredPages.value.length && filteredPages.value.length > 0,
      indeterminate: selectedPageIds.value.size > 0 && selectedPageIds.value.size < filteredPages.value.length,
      onChange: toggleAllPages,
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    }),
    cell: ({ row }: { row: { original: Page } }) => h('input', {
      type: 'checkbox',
      checked: selectedPageIds.value.has(row.original.id),
      onChange: () => togglePageSelection(row.original.id),
      class: 'rounded-sm border-neutral-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
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
    ]),
    cell: ({ row }: { row: { original: Page } }) => h('p', { class: 'font-medium truncate' }, row.original.name)
  },
  {
    accessorKey: 'description',
    header: 'Description',
    cell: ({ row }: { row: { original: Page } }) => {
      const description = getPageDescription(row.original)
      return description ? h('p', { class: 'text-sm text-muted truncate' }, description) : null
    }
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }: { row: { original: Page } }) => {
      const resolvedTags = row.original.resolvedTags as ResolvedTag[] | null
      const tags = row.original.tags as string[]
      if (!tags || tags.length === 0) return null

      const displayTags = resolvedTags && resolvedTags.length > 0
        ? resolvedTags.map(rt => ({ label: rt.label || rt.id, color: rt.color || DEFAULT_CUSTOM_TAG_COLOR }))
        : tags.map(tagId => ({ label: tagId, color: DEFAULT_CUSTOM_TAG_COLOR }))

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

      if (tags.length <= 2) {
        return h('div', { class: 'flex flex-wrap gap-1' },
          displayTags.map((tag, index) => renderTagBadge(tag, index))
        )
      }

      const visibleTags = displayTags.slice(0, 2)
      const hiddenTags = displayTags.slice(2)

      return h('div', { class: 'flex flex-wrap gap-1' }, [
        ...visibleTags.map((tag, index) => renderTagBadge(tag, index)),
        h(UBadge, { color: 'primary', variant: 'subtle', size: 'sm' }, () => `+${hiddenTags.length}`)
      ])
    }
  },
  {
    accessorKey: 'imageCount',
    header: () => h('div', { class: 'flex items-center justify-end gap-2' }, [
      h('span', 'Images'),
      h(UButton, {
        icon: sort.value.column === 'imageCount'
          ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
          : 'i-lucide-arrow-up-down',
        size: 'xs',
        variant: 'ghost',
        color: sort.value.column === 'imageCount' ? 'primary' : 'neutral',
        onClick: () => {
          if (sort.value.column === 'imageCount') {
            sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
          } else {
            sort.value = { column: 'imageCount', direction: 'desc' }
          }
        }
      })
    ]),
    cell: ({ row }: { row: { original: Page } }) => h('div', { class: 'text-right font-medium' }, row.original.imageCount)
  },
  {
    accessorKey: 'xmlFileCount',
    header: 'XML',
    cell: ({ row }: { row: { original: Page } }) => h(UBadge, {
      color: row.original.xmlFileCount > 0 ? 'success' : 'neutral',
      variant: 'soft',
      size: 'sm'
    }, () => row.original.xmlFileCount > 0 ? 'Yes' : 'No')
  },
  {
    accessorKey: 'indexingStatus',
    header: 'Indexed',
    cell: ({ row }: { row: { original: Page } }) => renderIndexingStatusBadge(row.original.indexingStatus)
  },
  {
    id: 'mySubtasks',
    header: 'My Tasks',
    cell: ({ row }: { row: { original: Page } }) => {
      const count = subtaskSummary.value?.[row.original.id] || 0
      if (count === 0) return null
      const subtasks = openSubtasksByPage.value?.[row.original.id] || []
      return h(UPopover, { mode: 'hover' }, {
        default: () => h(UBadge, {
          color: 'warning',
          variant: 'soft',
          size: 'sm'
        }, () => `${count} open`),
        content: () => h('div', { class: 'p-3 w-64' }, [
          h('p', { class: 'text-xs text-muted mb-2' }, `Open tasks (${count})`),
          subtasks.length > 0
            ? h('ul', { class: 'space-y-2' }, subtasks.map(subtask =>
                h('li', { key: subtask.id, class: 'text-sm' }, [
                  h('div', { class: 'font-medium truncate' }, subtask.title),
                  (subtask.description || subtask.taskDescription)
                    ? h('p', { class: 'text-xs text-muted mt-0.5 line-clamp-2' }, String(subtask.description || subtask.taskDescription))
                    : null
                ])
              ))
            : h('p', { class: 'text-xs text-muted' }, 'Loading subtasks...')
        ])
      })
    }
  },
  {
    id: 'collaboration',
    header: 'Editing',
    cell: ({ row }: { row: { original: Page } }) => renderCollaborationSummaryCell(row.original)
  },
  {
    accessorKey: 'updated',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
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
            sort.value = { column: 'updated', direction: 'desc' }
          }
        }
      })
    ]),
    cell: ({ row }: { row: { original: Page } }) => h(NuxtTime, { datetime: row.original.updated })
  },
  {
    id: 'actions',
    cell: ({ row }: { row: { original: Page } }) => h(UDropdownMenu, {
      content: { align: 'end' },
      items: getPageRowItems(row.original)
    }, () => h(UButton, { icon: 'i-lucide-ellipsis-vertical', color: 'neutral', variant: 'ghost' }))
  }
]

function getPageRowItems(page: Page) {
  const items: Array<Record<string, unknown>> = [
    { label: 'Edit', icon: 'i-lucide-edit', disabled: project.value?.locked || !allow(projectCapabilities.value.canEdit), onSelect: () => openEditModal(page) },
    { label: 'View Images', icon: 'i-lucide-images', disabled: page.imageCount === 0, onSelect: () => openImageModal(page) },
    { label: 'View/Edit XML', icon: 'i-lucide-file-pen-line', disabled: page.xmlFileCount === 0, onSelect: () => openXmlEditor(page) },
    { label: 'Export', icon: 'i-lucide-file-output', disabled: page.xmlFileCount === 0, onSelect: () => exportPageOutput(page) },
    { label: 'Version History', icon: 'i-lucide-history', disabled: page.xmlFileCount === 0, onSelect: () => openVersionHistory(page) }
  ]

  if (allow(projectCapabilities.value.canDeletePages)) {
    items.push({ type: 'separator' })
    items.push({ label: 'Delete', icon: 'i-lucide-trash', color: 'error', disabled: project.value?.locked, onSelect: () => openDeleteModal(page) })
  }

  return items
}

const contextMenuPage = ref<Page | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuPage.value) return []
  return getPageRowItems(contextMenuPage.value)
})

function handlePageRowContextMenu(_event: Event, row: { original: Record<string, unknown> }) {
  contextMenuPage.value = row.original as unknown as Page
}

function openEditModal(page: Page) {
  pageEditSlideover.open({
    projectId,
    page
  })
}

async function openDeleteModal(page: Page) {
  if (!allow(projectCapabilities.value.canDeletePages)) return
  const instance = pageDeleteSlideover.open({
    name: page.name,
    entityType: 'Page',
    warningDetails: [
      `${page.imageCount} ${page.imageCount === 1 ? 'image' : 'images'}`,
      'All associated XML files and annotations'
    ]
  })
  const confirmed = await instance.result
  if (confirmed) {
    try {
      await $fetch(`/api/projects/${projectId}/pages/${page.id}`, { method: 'DELETE' })
      toast.add({
        title: 'Page deleted',
        description: `"${page.name}" has been deleted`,
        color: 'success',
        icon: 'i-lucide-trash-2'
      })
      await refreshPagesData()
      await refreshProject()
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : ''
      toast.add({
        title: 'Failed to delete page',
        description: message || 'An error occurred',
        color: 'error'
      })
    }
  }
}

function openImageModal(page: Page) {
  const pageIndex = pages.value?.findIndex(p => p.id === page.id) ?? 0
  const pagesBasic = (pages.value ?? []).map(p => ({ id: p.id, name: p.name }))

  pageImagesModal.open({
    projectId,
    pages: pagesBasic,
    initialPageIndex: pageIndex
  })
}

async function openVersionHistory(page: Page) {
  try {
    const xmlFiles = await $fetch<{ id: string }[]>(`/api/projects/${projectId}/pages/${page.id}/xml`)
    const firstXml = xmlFiles.at(0)
    if (!firstXml) {
      toast.add({
        title: 'No XML files',
        description: 'This page has no XML annotation files.',
        color: 'warning'
      })
      return
    }
    versionHistorySlideover.open({
      projectId,
      pageId: page.id,
      xmlId: firstXml.id
    })
  } catch {
    toast.add({
      title: 'Error',
      description: 'Failed to load XML files for this page.',
      color: 'error'
    })
  }
}

async function openXmlEditor(page: Page) {
  try {
    const xmlFiles = await $fetch<Array<{ id: string, schema: string }>>(`/api/projects/${projectId}/pages/${page.id}/xml`)
    if (!xmlFiles?.length) {
      toast.add({
        title: 'No XML files',
        description: 'This page has no XML annotation files.',
        color: 'warning'
      })
      return
    }

    const pageXml = xmlFiles.find(xml => xml.schema === 'PAGE_XML')
    if (!pageXml) {
      toast.add({
        title: 'No PAGE XML',
        description: 'This page has no PAGE XML file to edit.',
        color: 'warning'
      })
      return
    }

    xmlEditorSlideover.open({
      projectId,
      pageId: page.id,
      xmlId: pageXml.id,
      pageName: page.name,
      readOnly: Boolean(project.value?.locked),
      readOnlyMessage: project.value?.locked
        ? 'This project is locked, so the XML is currently view-only.'
        : undefined
    })
  } catch {
    toast.add({
      title: 'Error',
      description: 'Failed to load PAGE XML for this page.',
      color: 'error'
    })
  }
}

useHead({
  title: computed(() => project.value?.name ? `${project.value.name} - LAREX` : 'Project - LAREX')
})
</script>

<template>
  <UDashboardPanel :id="projectId" :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <UDashboardNavbar>
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #title>
          <UBreadcrumb :items="items">
            <template #separator>
              <span class="mx-2 text-muted">/</span>
            </template>
          </UBreadcrumb>
        </template>

        <template #right>
          <div class="flex items-center gap-2">
            <div v-if="project?.storageUsedFormatted" class="flex items-center gap-1 text-xs text-muted mr-2">
              <UIcon name="i-lucide-hard-drive" class="w-3 h-3" />
              <span>{{ project.storageUsedFormatted }}</span>
            </div>

            <UButton
              v-if="project"
              :icon="project.isStarred ? 'i-prime-star-fill' : 'i-prime-star'"
              color="neutral"
              variant="ghost"
              :loading="isStarring"
              :class="project.isStarred ? 'text-yellow-500 dark:text-yellow-400' : 'text-primary'"
              :aria-label="project.isStarred ? 'Unstar project' : 'Star project'"
              @click="toggleStar"
            />

            <UFieldGroup>
              <UButton
                icon="i-lucide-pencil"
                color="neutral"
                variant="outline"
                :loading="isLoadingEditor"
                :disabled="!pages || pages.length === 0 || project?.locked"
                @click="handleOpenInEditor"
                @mouseenter="handleEditorButtonHover"
                @mouseleave="handleEditorButtonLeave"
                @focus="handleEditorButtonHover"
                @blur="handleEditorButtonLeave"
              >
                Open in Editor
              </UButton>

              <UButton
                v-if="allow(projectCapabilities.canUpload)"
                icon="i-lucide-upload"
                color="neutral"
                variant="outline"
                :loading="isUploading"
                :disabled="projectStatus?.isBlocked || project?.locked"
                @click="() => fileInput?.click()"
              >
                Upload Files
              </UButton>

              <UDropdownMenu :items="actionItems" :content="{ align: 'end' }">
                <UButton
                  color="neutral"
                  variant="outline"
                  icon="i-lucide-chevron-down"
                />
              </UDropdownMenu>
            </UFieldGroup>

            <input
              ref="fileInput"
              type="file"
              multiple
              accept="image/*,.xml,application/pdf,.pdf"
              class="hidden"
              @change="(e) => handleFileUpload((e.target as HTMLInputElement).files)"
            >
          </div>
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="project">
        <template #left>
          <UInput
            v-model="globalFilter"
            icon="i-lucide-search"
            placeholder="Search pages..."
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
            multiple
            class="w-48"
          >
            <template #leading>
              <UIcon name="i-lucide-tag" />
            </template>
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
              <UIcon name="i-lucide-git-merge" />
            </template>
          </USelectMenu>

          <USelectMenu
            v-model="xmlStatusFilter"
            :items="xmlStatusOptions"
            value-key="value"
            class="w-40"
          >
            <template #leading>
              <UIcon name="i-lucide-file-text" />
            </template>
          </USelectMenu>
        </template>
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            size="sm"
            :loading="isManualPagesRefresh"
            @click="refreshPagesData({ manual: true })"
          >
            Refresh
          </UButton>

          <UButton
            icon="i-lucide-x"
            color="neutral"
            variant="ghost"
            size="sm"
            @click="resetFilters"
          >
            Clear Filters
          </UButton>

          <USeparator orientation="vertical" class="h-4" />

          <UButton
            color="neutral"
            variant="ghost"
            size="sm"
            :icon="isReleaseSidebarVisible ? 'i-lucide-panel-right-close' : 'i-lucide-panel-right-open'"
            :aria-label="isReleaseSidebarVisible ? 'Hide release sidebar' : 'Show release sidebar'"
            @click="isReleaseSidebarVisible = !isReleaseSidebarVisible"
          >
            Releases
          </UButton>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="projectError" class="mb-4">
        <UEmpty
          v-if="isProjectNotFound"
          variant="naked"
          class="py-4"
          :actions="projectNotFoundActions"
          icon="i-lucide-folder-x"
          title="Project not found"
          description="This project may have been deleted before you opened this link, or you no longer have access to it."
        />

        <UAlert
          v-else
          color="error"
          variant="subtle"
          icon="i-lucide-alert-circle"
          title="Error loading project"
          :description="projectLoadErrorMessage"
        />
      </div>

      <div v-else-if="projectPending" class="flex items-center justify-center py-8">
        <UIcon name="i-lucide-loader" class="animate-spin text-neutral-500" />
        <span class="ml-2 text-sm text-neutral-600 dark:text-neutral-400">Loading project...</span>
      </div>

      <div v-else-if="project" class="flex min-h-full flex-col gap-4">
        <div v-if="project.locked" class="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-sm p-4">
          <div class="flex items-start">
            <UIcon name="i-lucide-lock" class="text-amber-500 mt-0.5 mr-3" />
            <div>
              <h4 class="font-medium text-amber-800 dark:text-amber-200">
                Project Locked
              </h4>
              <p class="text-sm text-amber-700 dark:text-amber-300 mt-1">
                {{ project.lockedReason || 'This project is locked and cannot be edited.' }}
              </p>
            </div>
          </div>
        </div>

        <div v-if="projectStatus?.hasUnresolvedConflicts" class="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-sm p-4">
          <div class="flex items-start justify-between">
            <div class="flex items-start">
              <UIcon name="i-lucide-alert-triangle" class="text-amber-500 mt-0.5 mr-3" />
              <div>
                <h4 class="font-medium text-amber-800 dark:text-amber-200">
                  Upload Conflicts Detected
                </h4>
                <p class="text-sm text-amber-700 dark:text-amber-300 mt-1">
                  This project has unresolved file upload conflicts. Some functionality is blocked until conflicts are resolved.
                </p>
              </div>
            </div>
            <UButton
              color="warning"
              class="self-center"
              icon="i-lucide-git-merge"
              variant="solid"
              size="sm"
              @click="viewConflicts"
            >
              Resolve Conflicts
            </UButton>
          </div>
        </div>

        <div class="flex flex-1 flex-col gap-0 xl:min-h-0 xl:flex-row xl:items-stretch">
          <div class="min-w-0 flex-1 space-y-6 p-6">
            <div v-if="globalFilter || selectedTags.length > 0 || xmlStatusFilter !== 'all'" class="flex items-center gap-2 flex-wrap">
              <span class="text-xs text-neutral-500">Active filters:</span>
              <UBadge
                v-if="globalFilter"
                color="neutral"
                variant="soft"
                size="sm"
                class="cursor-pointer"
                @click="globalFilter = ''"
              >
                Search: {{ globalFilter }} ×
              </UBadge>
              <UBadge
                v-for="tag in selectedTags"
                :key="tag"
                color="neutral"
                variant="soft"
                size="sm"
                class="cursor-pointer"
                @click="selectedTags = selectedTags.filter(t => t !== tag)"
              >
                Tag: {{ tag }} ×
              </UBadge>
              <UBadge
                v-if="xmlStatusFilter !== 'all'"
                color="neutral"
                variant="soft"
                size="sm"
                class="cursor-pointer"
                @click="xmlStatusFilter = 'all'"
              >
                XML: {{ xmlStatusOptions.find(o => o.value === xmlStatusFilter)?.label }} ×
              </UBadge>
            </div>

            <div v-if="pagesError" class="py-8 text-center">
              <div class="flex items-center justify-center gap-2 text-red-600 dark:text-red-400">
                <UIcon name="i-lucide-alert-circle" />
                <p class="text-sm">
                  <strong>Error loading pages:</strong> {{ pagesError.message || pagesError }}
                </p>
              </div>
            </div>

            <div v-else-if="showPagesLoadingSpinner" class="py-8 text-center">
              <div class="flex items-center justify-center">
                <UIcon name="i-lucide-loader" class="animate-spin text-neutral-500" />
                <span class="ml-2 text-sm text-neutral-600 dark:text-neutral-400">Loading pages...</span>
              </div>
            </div>

            <div v-else-if="pages && pages.length === 0" class="py-12 text-center">
              <UIcon name="i-lucide-file-text" class="mx-auto text-4xl text-neutral-400 mb-4" />
              <p class="text-neutral-600 dark:text-neutral-400 mb-4">
                No pages found in this project.
              </p>
              <div class="text-sm text-neutral-500 dark:text-neutral-500 space-y-2 max-w-md mx-auto">
                <p><strong>Upload Files:</strong> Upload images and XML files to create pages automatically.</p>
                <p>Files are grouped by basename (everything before the first dot) to create organized pages.</p>
              </div>
            </div>

            <div v-else-if="pages && filteredPages.length === 0" class="py-12 text-center">
              <UIcon name="i-lucide-filter-x" class="mx-auto text-4xl text-neutral-400 mb-4" />
              <p class="text-neutral-600 dark:text-neutral-400 mb-4">
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

            <div v-else-if="pages">
              <UContextMenu :items="contextMenuItems as any">
                <UTable
                  v-if="paginatedPages.length > 0"
                  :columns="pageColumns"
                  :data="paginatedPages"
                  :loading="isManualPagesRefresh"
                  class="flex-1"
                  :ui="{
                    base: 'table-fixed border-separate border-spacing-0',
                    thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
                    tbody: '[&>tr]:last:[&>td]:border-b-0',
                    th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
                    td: 'border-b border-default',
                    separator: 'h-0'
                  }"
                  @contextmenu="handlePageRowContextMenu"
                />
              </UContextMenu>

              <div v-if="totalPagesCount > 1" class="flex justify-between items-center p-4 border-t border-neutral-200 dark:border-neutral-800">
                <div class="flex items-center gap-2 text-sm text-neutral-600 dark:text-neutral-400">
                  <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} pages</span>
                </div>

                <div class="flex items-center gap-4">
                  <USelect
                    v-model="itemsPerPage"
                    :items="[10, 25, 50, 100]"
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

            <UiFloatingSelectionMenu
              :selected-count="selectedPageIds.size"
              @clear="clearSelection"
            >
              <UButton
                icon="i-lucide-pencil"
                color="neutral"
                variant="ghost"
                size="sm"
                class="text-neutral-50 hover:bg-white/10"
                :loading="isLoadingEditor"
                :disabled="!pages || pages.length === 0 || project?.locked"
                aria-label="Open selected pages in editor"
                @click="handleOpenInEditor"
              >
                <span class="hidden sm:inline">Open in Editor</span>
              </UButton>
              <UButton
                v-if="canManageDatasets"
                icon="i-lucide-database-zap"
                color="neutral"
                variant="ghost"
                size="sm"
                class="text-neutral-50 hover:bg-white/10"
                aria-label="Add selected pages to dataset"
                @click="openAddToDatasetSlideover"
              >
                <span class="hidden sm:inline">Add To Dataset</span>
              </UButton>
              <UDropdownMenu :items="selectionMoreActionItems" :content="{ align: 'end' }">
                <UButton
                  icon="i-lucide-ellipsis"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  class="text-neutral-50 hover:bg-white/10"
                  aria-label="More selected page actions"
                >
                  <span class="hidden sm:inline">More</span>
                </UButton>
              </UDropdownMenu>
              <UButton
                v-if="canBulkDeletePages"
                icon="i-lucide-trash-2"
                color="error"
                variant="ghost"
                size="sm"
                class="hover:bg-white/10"
                aria-label="Delete selected pages"
                @click="openBulkDeleteSlideover"
              >
                <span class="hidden sm:inline">Delete</span>
              </UButton>
            </UiFloatingSelectionMenu>
          </div>

          <aside
            v-if="isReleaseSidebarVisible"
            class="w-full shrink-0 border-t border-default bg-muted/40 xl:w-[340px] xl:self-stretch xl:border-l xl:border-t-0"
          >
            <div class="h-full space-y-5 p-4">
              <div class="flex items-center justify-between gap-3">
                <div class="flex items-center gap-2 text-sm font-semibold text-highlighted">
                  <UIcon name="i-lucide-box" class="size-4 text-muted" />
                  <span>Releases</span>
                </div>
                <UButton
                  v-if="allow(projectCapabilities.canShare)"
                  color="neutral"
                  variant="solid"
                  size="xs"
                  icon="i-lucide-plus"
                  @click="openCreateRelease"
                >
                  New Release
                </UButton>
              </div>

              <p class="text-xs text-muted">
                {{ releaseSidebarSummary }}
              </p>

              <USeparator />

              <UAlert
                v-if="releasesError"
                color="error"
                variant="soft"
                icon="i-lucide-alert-circle"
                :title="extractApiErrorMessage(releasesError, 'Failed to load releases')"
              />

              <div v-if="releasesPending && releasesForSidebar.length === 0" class="flex items-center gap-2 py-3 text-sm text-muted">
                <UIcon name="i-lucide-loader-2" class="size-4 animate-spin" />
                <span>Loading releases...</span>
              </div>

              <div v-else-if="releasesForSidebar.length === 0" class="rounded-lg border border-dashed border-default p-4 text-sm text-muted">
                No releases yet.
              </div>

              <div v-else class="space-y-2">
                <div
                  v-for="release in releasesForSidebar"
                  :key="release.id"
                  :class="[
                    'px-3 py-3',
                    release.id === latestReleaseId
                      ? 'rounded-xl border border-default bg-default'
                      : 'rounded-lg'
                  ]"
                >
                  <div class="flex items-start justify-between gap-2">
                    <div class="min-w-0 flex items-center gap-2">
                      <UIcon name="i-lucide-git-branch" class="size-4 text-muted" />
                      <p class="truncate text-sm font-semibold text-highlighted">
                        {{ release.versionTag }}
                      </p>
                    </div>
                    <div class="flex items-center gap-1">
                      <UBadge
                        v-if="release.id === latestReleaseId"
                        color="success"
                        variant="soft"
                        size="sm"
                      >
                        Latest
                      </UBadge>

                      <UDropdownMenu
                        :items="getReleaseCardItems(release)"
                        :content="{ align: 'end' }"
                      >
                        <UButton
                          icon="i-lucide-ellipsis-vertical"
                          color="neutral"
                          variant="ghost"
                          size="xs"
                        />
                      </UDropdownMenu>
                    </div>
                  </div>

                  <div class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                    <span class="inline-flex items-center gap-1">
                      <UIcon name="i-lucide-clock-3" class="size-3.5" />
                      {{ formatDate(release.created) }}
                    </span>
                    <span class="inline-flex items-center gap-1">
                      <UIcon name="i-lucide-file-text" class="size-3.5" />
                      {{ release.pageCount }} pages
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </template>
  </UDashboardPanel>

  <UModal v-model:open="isDictionaryValidationModalOpen" title="Dictionary Validation">
    <template #body>
      <div v-if="isDictionaryValidationLoading" class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
        </div>
        <USkeleton class="h-20 w-full rounded-lg" />
        <div class="space-y-3">
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
        </div>
      </div>
      <div v-else-if="dictionaryValidationResult" class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <UPageCard title="Pages" :description="String(dictionaryValidationResult.analyzedPageCount)" variant="subtle" />
          <UPageCard title="Tokens" :description="String(dictionaryValidationResult.analyzedTokenCount)" variant="subtle" />
          <UPageCard title="Known" :description="String(dictionaryValidationResult.knownTokenCount)" variant="subtle" />
          <UPageCard title="Unknown" :description="String(dictionaryValidationResult.unknownTokenCount)" variant="subtle" />
        </div>

        <UAlert
          :color="dictionaryValidationResult.valid ? 'success' : 'warning'"
          variant="subtle"
          :title="dictionaryValidationResult.valid ? 'All checked tokens were found in the dictionary.' : 'Unknown tokens were found in the dictionary check.'"
          :description="dictionaryValidationResult.message"
        />

        <div v-if="dictionaryValidationResult.unknownTokenResults.length > 0" class="space-y-3">
          <h3 class="text-sm font-semibold">
            Unknown Tokens
          </h3>
          <div
            v-for="tokenResult in dictionaryValidationResult.unknownTokenResults.slice(0, 25)"
            :key="tokenResult.normalizedToken"
            class="rounded-lg border border-default p-3 space-y-2"
          >
            <div class="flex items-center justify-between gap-3">
              <div class="min-w-0">
                <p class="font-medium break-all">
                  {{ tokenResult.token }}
                </p>
                <p class="text-xs text-muted">
                  {{ tokenResult.occurrenceCount }} occurrence(s)
                </p>
              </div>
              <UBadge color="warning" variant="soft">
                {{ tokenResult.pages.length }} page(s)
              </UBadge>
            </div>

            <div v-if="tokenResult.suggestions.length > 0" class="flex flex-wrap gap-2">
              <UBadge
                v-for="suggestion in tokenResult.suggestions"
                :key="`${tokenResult.normalizedToken}-${suggestion.normalized}`"
                color="neutral"
                variant="subtle"
              >
                {{ suggestion.display }}
              </UBadge>
            </div>
          </div>
        </div>
      </div>
      <UAlert
        v-else
        color="neutral"
        variant="subtle"
        title="No dictionary validation results"
        description="Run the dictionary check again to load validation details."
      />
    </template>
  </UModal>

  <USlideover
    v-model:open="isNormalizationPreviewSlideoverOpen"
    title="Normalization"
    description="Review and apply text rewrites for the current project scope."
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-6xl' }"
  >
    <template #body>
      <div v-if="isNormalizationPreviewLoading" class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
        </div>
        <USkeleton class="h-20 w-full rounded-lg" />
        <div class="space-y-3">
          <USkeleton class="h-32 w-full rounded-lg" />
          <USkeleton class="h-32 w-full rounded-lg" />
        </div>
      </div>
      <div v-else-if="normalizationPreviewResult" class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <UPageCard title="Projects" :description="String(normalizationPreviewResult.analyzedProjectCount)" variant="subtle" />
          <UPageCard title="Pages" :description="String(normalizationPreviewResult.analyzedPageCount)" variant="subtle" />
          <UPageCard title="Rows" :description="String(normalizationPreviewResult.analyzedRowCount)" variant="subtle" />
          <UPageCard title="Changed Rows" :description="String(normalizationPreviewResult.changedRowCount)" variant="subtle" />
        </div>

        <UAlert
          :color="normalizationPreviewResult.changedRowCount > 0 ? 'warning' : 'success'"
          variant="subtle"
          :title="normalizationPreviewResult.changedRowCount > 0 ? 'Normalization changes were detected.' : 'No normalization changes were detected.'"
          :description="normalizationPreviewResult.message"
        />

        <UAlert
          v-if="project?.locked"
          color="warning"
          variant="subtle"
          title="Project is locked"
          :description="project.lockedReason || 'Unlock the project before applying normalization changes.'"
        />

        <div v-if="isNormalizationProfileLoading" class="space-y-3">
          <USkeleton class="h-24 w-full rounded-lg" />
        </div>

        <UAlert
          v-else-if="normalizationProfileLoadError"
          color="warning"
          variant="subtle"
          title="Normalization profile details unavailable"
          :description="normalizationProfileLoadError"
        />

        <UCard v-else-if="activeNormalizationProfile">
          <template #header>
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="text-sm font-medium">
                  Assigned Normalization Profile
                </p>
                <p class="text-xs text-muted">
                  This normalization run uses the assigned profile for all selected sources.
                </p>
              </div>
              <UButton
                icon="i-lucide-list"
                color="neutral"
                variant="outline"
                size="sm"
                @click="openNormalizationRulesSlideover"
              >
                View rules
              </UButton>
            </div>
          </template>

          <div class="space-y-3">
            <div class="flex flex-wrap items-center gap-2">
              <UBadge color="primary" variant="soft">
                {{ activeNormalizationProfile.name }}
              </UBadge>
              <UBadge color="neutral" variant="subtle">
                {{ enabledNormalizationPresetRuleCount }} preset rule(s) active
              </UBadge>
              <UBadge color="neutral" variant="subtle">
                {{ normalizationManualRuleCount }} manual rule(s)
              </UBadge>
            </div>

            <p v-if="activeNormalizationProfile.description" class="text-sm text-muted">
              {{ activeNormalizationProfile.description }}
            </p>

            <div v-if="activeNormalizationProfile.tags.length > 0" class="flex flex-wrap gap-2">
              <UBadge
                v-for="tag in activeNormalizationProfile.tags"
                :key="`normalization-profile-tag-${tag}`"
                color="neutral"
                variant="soft"
              >
                {{ tag }}
              </UBadge>
            </div>
          </div>
        </UCard>

        <UCard>
          <template #header>
            <div class="text-sm font-medium">
              Target Text Index
            </div>
          </template>

          <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_160px]">
            <UFormField label="Source variant">
              <USelect
                v-model="normalizationVariantMode"
                :items="[
                  { label: `Project GT index (${defaultNormalizationVariantIndex})`, value: 'PROJECT_GT' },
                  { label: 'Custom index', value: 'CUSTOM' }
                ]"
                value-key="value"
              />
            </UFormField>

            <UFormField
              label="Index"
              :error="normalizationVariantMode === 'CUSTOM' && normalizedCustomNormalizationVariantIndex === null ? 'Index must be a non-negative integer.' : undefined"
            >
              <UInput
                v-model.number="normalizationVariantIndexInput"
                type="number"
                :min="0"
                :disabled="normalizationVariantMode !== 'CUSTOM'"
                placeholder="0"
              />
            </UFormField>
          </div>
          <p class="mt-2 text-xs text-muted">
            Normalization runs against the selected text variant. By default this uses the project GT index.
          </p>
        </UCard>

        <div v-if="normalizationPreviewRows.length > 0" class="space-y-3">
          <h3 class="text-sm font-semibold">
            Affected Rows
          </h3>
          <div
            v-for="preview in normalizationPreviewRows"
            :key="preview.key"
            class="rounded-lg border border-default p-3 space-y-3"
          >
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="font-medium">
                  {{ preview.pageName }}
                </p>
                <p class="text-xs text-muted">
                  {{ preview.projectName }}
                </p>
              </div>
              <div class="flex flex-wrap items-center gap-2">
                <UBadge color="neutral" variant="soft">
                  {{ preview.textLineId ? 'Text Line' : 'Region' }}
                </UBadge>
                <UBadge color="neutral" variant="subtle">
                  Variant {{ preview.variantIndex ?? 0 }}
                </UBadge>
                <UButton
                  icon="i-lucide-wand-sparkles"
                  color="warning"
                  variant="outline"
                  size="xs"
                  :loading="normalizationApplyingRowKey === preview.key"
                  :disabled="project?.locked || isApplyingNormalization || (normalizationApplyingRowKey !== null && normalizationApplyingRowKey !== preview.key)"
                  @click="applyNormalizationRow(preview)"
                >
                  Apply row
                </UButton>
              </div>
            </div>

            <div v-if="preview.matchedRules.length > 0" class="space-y-2">
              <p class="text-xs font-medium text-muted">
                Matched rules
              </p>
              <div class="flex flex-wrap gap-2">
                <UPopover
                  v-for="rule in preview.matchedRules"
                  :key="`${preview.key}-${rule.key}`"
                  mode="hover"
                >
                  <UBadge :color="rule.manual ? 'warning' : 'primary'" variant="soft" class="cursor-help">
                    {{ rule.label }}
                  </UBadge>

                  <template #content>
                    <div class="max-w-xs space-y-1 p-3">
                      <p class="text-sm font-medium">
                        {{ rule.label }}
                      </p>
                      <p class="text-xs text-muted">
                        {{ rule.description || 'No additional rule details.' }}
                      </p>
                    </div>
                  </template>
                </UPopover>
              </div>
            </div>

            <div class="grid gap-3 lg:grid-cols-2">
              <div>
                <p class="mb-1 text-xs font-medium text-muted">
                  Original
                </p>
                <div class="rounded border border-default bg-muted/30 p-2 text-sm whitespace-pre-wrap break-words font-junicode">
                  <template v-for="(segment, segmentIndex) in preview.originalSegments" :key="`original-${preview.key}-${segmentIndex}`">
                    <mark v-if="segment.changed" class="normalization-preview-mark normalization-preview-mark--delete">{{ segment.text }}</mark>
                    <template v-else>
                      {{ segment.text }}
                    </template>
                  </template>
                </div>
              </div>
              <div>
                <p class="mb-1 text-xs font-medium text-muted">
                  Normalized
                </p>
                <div class="rounded border border-default bg-muted/30 p-2 text-sm whitespace-pre-wrap break-words font-junicode">
                  <template v-for="(segment, segmentIndex) in preview.normalizedSegments" :key="`normalized-${preview.key}-${segmentIndex}`">
                    <mark v-if="segment.changed" class="normalization-preview-mark normalization-preview-mark--insert">{{ segment.text }}</mark>
                    <template v-else>
                      {{ segment.text }}
                    </template>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <UAlert
        v-else
        color="neutral"
        variant="subtle"
        title="No normalization results"
        description="Run normalization again to load normalization details."
      />
    </template>
    <template #footer>
      <div class="flex w-full flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p class="text-xs text-muted">
          Applying normalization updates PAGE XML text variants. Search and filter indexes refresh asynchronously afterward.
        </p>
        <div class="flex items-center justify-end gap-2">
          <UButton color="neutral" variant="ghost" @click="isNormalizationPreviewSlideoverOpen = false">
            Close
          </UButton>
          <UButton
            icon="i-lucide-rotate-cw"
            color="neutral"
            variant="outline"
            :loading="isNormalizationPreviewLoading"
            :disabled="effectiveNormalizationVariantIndex === null"
            @click="loadNormalizationPreview()"
          >
            Refresh
          </UButton>
          <UButton
            icon="i-lucide-wand-sparkles"
            color="warning"
            variant="solid"
            :loading="isApplyingNormalization"
            :disabled="project?.locked || normalizationApplyingRowKey !== null || effectiveNormalizationVariantIndex === null || !normalizationPreviewResult || normalizationPreviewResult.changedRowCount === 0"
            @click="applyNormalizationPreview()"
          >
            Apply normalization
          </UButton>
        </div>
      </div>
    </template>
  </USlideover>

  <USlideover
    v-model:open="isNormalizationRulesSlideoverOpen"
    title="Normalization Rules"
    description="Inspect the preset and manual rules of the assigned normalization profile."
    :ui="{ content: 'w-full max-w-[96vw] sm:max-w-4xl' }"
  >
    <template #body>
      <div v-if="isNormalizationProfileLoading" class="space-y-3">
        <USkeleton class="h-24 w-full rounded-lg" />
        <USkeleton class="h-48 w-full rounded-lg" />
      </div>

      <UAlert
        v-else-if="normalizationProfileLoadError"
        color="warning"
        variant="subtle"
        title="Normalization profile details unavailable"
        :description="normalizationProfileLoadError"
      />

      <div v-else-if="activeNormalizationProfile" class="space-y-4">
        <UCard>
          <template #header>
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p class="text-sm font-medium">
                  {{ activeNormalizationProfile.name }}
                </p>
                <p class="text-xs text-muted">
                  {{ activeNormalizationProfile.description || 'No description provided.' }}
                </p>
              </div>
              <div class="flex flex-wrap items-center gap-2">
                <UBadge color="neutral" variant="soft">
                  {{ enabledNormalizationPresetRuleCount }} preset rule(s) active
                </UBadge>
                <UBadge color="neutral" variant="soft">
                  {{ normalizationManualRuleCount }} manual rule(s)
                </UBadge>
              </div>
            </div>
          </template>

          <div v-if="activeNormalizationProfile.tags.length > 0" class="flex flex-wrap gap-2">
            <UBadge
              v-for="tag in activeNormalizationProfile.tags"
              :key="`normalization-rules-tag-${tag}`"
              color="neutral"
              variant="subtle"
            >
              {{ tag }}
            </UBadge>
          </div>
          <p v-else class="text-sm text-muted">
            No tags configured.
          </p>
        </UCard>

        <UPageCard title="Preset Rules" variant="subtle">
          <div class="space-y-3">
            <div
              v-for="rule in normalizationPresetRules"
              :key="rule.key"
              class="flex items-center justify-between gap-3 rounded-lg border border-default p-3"
            >
              <div>
                <div class="flex items-center gap-1">
                  <p class="text-sm font-medium">
                    {{ rule.label }}
                  </p>
                  <NormalizationPresetRuleHelpPopover :rule-key="rule.key" />
                </div>
                <p class="text-xs text-muted">
                  {{ rule.value }}
                </p>
              </div>
              <UBadge :color="rule.enabled ? 'primary' : 'neutral'" variant="soft">
                {{ rule.enabled ? 'Enabled' : 'Disabled' }}
              </UBadge>
            </div>
          </div>
        </UPageCard>

        <UPageCard title="Manual Replacement Rules" variant="subtle">
          <div v-if="activeNormalizationProfile.replacementRules.length === 0" class="rounded-lg border border-dashed border-default p-4 text-sm text-muted">
            No manual replacement rules configured.
          </div>

          <div v-else class="space-y-3">
            <div
              v-for="(rule, index) in activeNormalizationProfile.replacementRules"
              :key="`normalization-rules-${index}-${rule.search}-${rule.replacement}`"
              class="rounded-lg border border-default p-4 space-y-3"
            >
              <div class="flex items-center justify-between gap-3">
                <p class="text-sm font-medium">
                  Rule {{ index + 1 }}
                </p>
                <UBadge :color="rule.regex ? 'warning' : 'neutral'" variant="soft">
                  {{ rule.regex ? 'Regex' : 'Plain text' }}
                </UBadge>
              </div>

              <div class="grid gap-3 md:grid-cols-2">
                <div>
                  <p class="mb-1 text-xs font-medium text-muted">
                    {{ rule.regex ? 'Pattern' : 'Search' }}
                  </p>
                  <div class="rounded border border-default bg-muted/30 p-2 text-sm font-mono whitespace-pre-wrap break-words">
                    {{ rule.search || ' ' }}
                  </div>
                </div>
                <div>
                  <p class="mb-1 text-xs font-medium text-muted">
                    Replacement
                  </p>
                  <div class="rounded border border-default bg-muted/30 p-2 text-sm font-mono whitespace-pre-wrap break-words">
                    {{ rule.replacement || ' ' }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </UPageCard>
      </div>

      <UAlert
        v-else
        color="neutral"
        variant="subtle"
        title="No normalization profile loaded"
        description="Open normalization from a project with an assigned profile to inspect its rules."
      />
    </template>
    <template #footer>
      <div class="flex justify-end">
        <UButton color="neutral" variant="ghost" @click="isNormalizationRulesSlideoverOpen = false">
          Close
        </UButton>
      </div>
    </template>
  </USlideover>

  <UModal v-model:open="isValidationRulesetModalOpen" title="Validation Ruleset Results">
    <template #body>
      <div v-if="isValidationRulesetLoading" class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
        </div>
        <USkeleton class="h-20 w-full rounded-lg" />
        <div class="space-y-3">
          <USkeleton class="h-24 w-full rounded-lg" />
          <USkeleton class="h-24 w-full rounded-lg" />
        </div>
      </div>
      <div v-else-if="validationRulesetResult" class="space-y-4">
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <UPageCard title="Projects" :description="String(validationRulesetResult.analyzedProjectCount)" variant="subtle" />
          <UPageCard title="Pages" :description="String(validationRulesetResult.analyzedPageCount)" variant="subtle" />
          <UPageCard title="Occurrences" :description="String(validationRulesetResult.totalOccurrenceCount)" variant="subtle" />
          <UPageCard title="Rules Matched" :description="String(validationRulesetResult.ruleResults.length)" variant="subtle" />
        </div>

        <UAlert
          :color="validationRulesetResult.valid ? 'success' : 'warning'"
          variant="subtle"
          :title="validationRulesetResult.valid ? 'No rules matched the selected text.' : 'Validation rules matched suspicious patterns.'"
          :description="validationRulesetResult.message"
        />

        <div v-if="validationRulesetResult.ruleResults.length > 0" class="space-y-3">
          <h3 class="text-sm font-semibold">
            Matched Rules
          </h3>
          <div
            v-for="ruleResult in validationRulesetResult.ruleResults"
            :key="ruleResult.ruleId"
            class="rounded-lg border border-default p-3 space-y-2"
          >
            <div class="flex items-center justify-between gap-3">
              <div>
                <p class="font-medium">
                  {{ ruleResult.ruleName }}
                </p>
                <p class="text-xs text-muted">
                  {{ ruleResult.message }}
                </p>
              </div>
              <div class="flex items-center gap-2">
                <UBadge :color="ruleResult.severity === 'ERROR' ? 'error' : (ruleResult.severity === 'WARNING' ? 'warning' : 'neutral')" variant="soft">
                  {{ ruleResult.severity }}
                </UBadge>
                <UBadge color="neutral" variant="soft">
                  {{ ruleResult.occurrenceCount }} hit(s)
                </UBadge>
              </div>
            </div>

            <div v-if="ruleResult.matchedSamples.length > 0" class="flex flex-wrap gap-2">
              <UBadge
                v-for="sample in ruleResult.matchedSamples"
                :key="`${ruleResult.ruleId}-${sample}`"
                color="neutral"
                variant="subtle"
              >
                {{ sample }}
              </UBadge>
            </div>

            <p class="text-xs text-muted">
              {{ ruleResult.pages.length }} page(s) affected
            </p>
          </div>
        </div>
      </div>
      <UAlert
        v-else
        color="neutral"
        variant="subtle"
        title="No validation ruleset results"
        description="Run the ruleset check again to load validation details."
      />
    </template>
  </UModal>
</template>

<style scoped>
.normalization-preview-mark {
  border-radius: 0.25rem;
  padding: 0 0.1rem;
}

.normalization-preview-mark--delete {
  background: color-mix(in srgb, var(--ui-error) 14%, transparent);
  box-shadow: inset 0 -1px 0 color-mix(in srgb, var(--ui-error) 60%, transparent);
}

.normalization-preview-mark--insert {
  background: color-mix(in srgb, var(--ui-warning) 18%, transparent);
  box-shadow: inset 0 -1px 0 color-mix(in srgb, var(--ui-warning) 70%, transparent);
}
</style>
