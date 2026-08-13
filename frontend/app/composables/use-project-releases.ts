import { useMediaQuery } from '@vueuse/core'
import type { Ref } from 'vue'
import type { ProjectPackageRelease } from '@/types/project-package-release'
import type { Page, ProjectData } from '@/types/project-page'
import type { PreparedDownloadTarget } from '@/composables/use-background-downloads'
import { extractApiErrorMessage } from '@/utils/api-error'

type CreateReleaseSlideover = {
  open: (props: { projectId: string, suggestedTag: string, imageVariantPages: Page[] }) => { result: Promise<unknown> }
}

type ReleaseShareSlideover = {
  open: (props: { projectId: string, release: ProjectPackageRelease }) => { result: Promise<unknown> }
}

type ProjectReleasesOptions = {
  projectId: string
  selectedWorkspace: Ref<string | null | undefined>
  project: Ref<ProjectData | null | undefined>
  pages: Ref<Page[] | null | undefined>
  canShareProject: Ref<boolean>
  createReleaseSlideover: unknown
  releaseShareSlideover: unknown
  downloadBlobResponse: (response: Response, fallbackName: string, controls?: { update: (updates: { subtitle?: string, statusLabel?: string, progressPercent?: number | null, icon?: string }) => void }, target?: PreparedDownloadTarget) => Promise<void>
}

export async function useProjectReleases(options: ProjectReleasesOptions) {
  const toast = useToast()
  const backgroundDownloads = useBackgroundDownloads()
  const projectReleasesKey = computed(() => wsKey(options.selectedWorkspace.value as string, 'projects', options.projectId, 'releases'))
  const { data: releases, error: releasesError, pending: releasesPending, refresh: refreshReleases } = await useFetch<ProjectPackageRelease[]>(
    () => `/api/workspaces/${options.selectedWorkspace.value}/projects/${options.projectId}/releases`,
    {
      key: projectReleasesKey,
      watch: [options.selectedWorkspace],
      default: () => []
    }
  )

  const nextReleaseTag = computed(() => {
    const maxVersion = (releases.value || []).reduce((currentMax, release) => Math.max(currentMax, Number(release.versionNumber) || 0), 0)
    return `v${maxVersion + 1}`
  })

  const isReleaseSidebarVisible = ref(false)
  const isReleaseSlideoverOpen = ref(false)
  const useReleaseSidebarLayout = useMediaQuery('(min-width: 1280px)')
  const isReleasePanelVisible = computed(() =>
    useReleaseSidebarLayout.value ? isReleaseSidebarVisible.value : isReleaseSlideoverOpen.value
  )

  function toggleReleasePanel() {
    if (useReleaseSidebarLayout.value) {
      isReleaseSidebarVisible.value = !isReleaseSidebarVisible.value
      return
    }

    isReleaseSlideoverOpen.value = !isReleaseSlideoverOpen.value
  }

  watch(useReleaseSidebarLayout, (useSidebar) => {
    if (useSidebar) {
      isReleaseSlideoverOpen.value = false
    }
  })

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

    return `${countLabel} - Last updated ${formatDate(latestReleaseUpdatedAt.value)}`
  })

  async function openCreateRelease() {
    if (!options.canShareProject.value) return
    if (!options.project.value) return

    const createReleaseSlideover = options.createReleaseSlideover as CreateReleaseSlideover
    const instance = createReleaseSlideover.open({
      projectId: options.projectId,
      suggestedTag: nextReleaseTag.value,
      imageVariantPages: options.pages.value ?? []
    })
    const createdReleaseId = await instance.result as string | null
    if (!createdReleaseId) return
    await refreshReleases()
  }

  async function openReleaseShare(release: ProjectPackageRelease) {
    const releaseShareSlideover = options.releaseShareSlideover as ReleaseShareSlideover
    const instance = releaseShareSlideover.open({
      projectId: options.projectId,
      release
    })
    const shouldRefresh = await instance.result as boolean | null
    if (shouldRefresh) {
      await refreshReleases()
    }
  }

  async function downloadProjectRelease(release: ProjectPackageRelease) {
    if (!options.selectedWorkspace.value || !options.project.value) return

    const workspaceId = options.selectedWorkspace.value
    const projectName = options.project.value.name
    const fallbackName = release.packageFileName || `${projectName}-${release.versionTag}.larex-project.zip`
    const target = await backgroundDownloads.prepareDownload(fallbackName)
    if (!target) return

    try {
      await backgroundDownloads.runBackgroundJob({
        title: 'Downloading project release',
        subtitle: `${projectName} · ${release.versionTag}`,
        statusLabel: 'Preparing',
        completedLabel: 'Downloaded',
        icon: 'i-lucide-download',
        task: async (job) => {
          const response = await fetch(`/api/workspaces/${workspaceId}/projects/${options.projectId}/releases/${release.id}/download`)
          if (!response.ok) {
            const message = await response.text()
            throw new Error(message || `Download failed (${response.status})`)
          }
          await options.downloadBlobResponse(response, fallbackName, job, target)
        }
      })
    } catch (error: unknown) {
      toast.add({
        title: 'Release download failed',
        description: extractApiErrorMessage(error, 'Failed to download release package'),
        color: 'error'
      })
    }
  }

  return {
    releases,
    releasesError,
    releasesPending,
    refreshReleases,
    nextReleaseTag,
    isReleaseSidebarVisible,
    isReleaseSlideoverOpen,
    isReleasePanelVisible,
    releasesForSidebar,
    latestReleaseId,
    releaseSidebarSummary,
    toggleReleasePanel,
    openCreateRelease,
    openReleaseShare,
    downloadProjectRelease
  }
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium'
  }).format(new Date(value))
}
