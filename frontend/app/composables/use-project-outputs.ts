import { useMediaQuery } from '@vueuse/core'
import type { Ref } from 'vue'
import type { ActionOutput, ActionOutputFile } from '@/types/action-output'
import type { ProjectData } from '@/types/project-page'

type OutputShareSlideover = {
  open: (props: { projectId: string, output: ActionOutput }) => { result: Promise<unknown> }
}

type DeleteSlideover = {
  open: (props: Record<string, unknown>) => { result: Promise<unknown> }
}

type Options = {
  projectId: string
  selectedWorkspace: Ref<string | null | undefined>
  project: Ref<ProjectData | null | undefined>
  canManageOutputs: Ref<boolean>
  outputShareSlideover: unknown
  deleteSlideover: unknown
  downloadBlobResponse: (response: Response, fallbackName: string, controls?: { update: (updates: { subtitle?: string, statusLabel?: string, progressPercent?: number | null, icon?: string }) => void }) => Promise<void>
}

export async function useProjectOutputs(options: Options) {
  const toast = useToast()
  const backgroundDownloads = useBackgroundDownloads()
  const key = computed(() => wsKey(options.selectedWorkspace.value as string, 'projects', options.projectId, 'outputs'))
  const { data: outputs, error: outputsError, pending: outputsPending, refresh: refreshOutputs } = await useFetch<ActionOutput[]>(
    () => `/api/workspaces/${options.selectedWorkspace.value}/projects/${options.projectId}/outputs`,
    { key, watch: [options.selectedWorkspace], default: () => [] }
  )

  const isOutputSidebarVisible = ref(false)
  const isOutputSlideoverOpen = ref(false)
  const useSidebarLayout = useMediaQuery('(min-width: 1280px)')
  const isOutputPanelVisible = computed(() => useSidebarLayout.value ? isOutputSidebarVisible.value : isOutputSlideoverOpen.value)

  function toggleOutputPanel() {
    if (useSidebarLayout.value) isOutputSidebarVisible.value = !isOutputSidebarVisible.value
    else isOutputSlideoverOpen.value = !isOutputSlideoverOpen.value
  }

  function closeOutputPanel() {
    isOutputSidebarVisible.value = false
    isOutputSlideoverOpen.value = false
  }

  watch(useSidebarLayout, (useSidebar) => {
    if (useSidebar) isOutputSlideoverOpen.value = false
  })

  const outputsForPanel = computed(() => [...(outputs.value ?? [])].sort((a, b) =>
    Date.parse(b.completedAt || b.created) - Date.parse(a.completedAt || a.created)
  ))
  const outputSummary = computed(() => {
    const count = outputsForPanel.value.length
    return `${count} ${count === 1 ? 'output' : 'outputs'}`
  })

  async function download(url: string, fallbackName: string, subtitle: string) {
    try {
      await backgroundDownloads.runBackgroundJob({
        title: 'Downloading Action output', subtitle, statusLabel: 'Preparing', completedLabel: 'Downloaded', icon: 'i-lucide-download',
        task: async (job) => {
          const response = await fetch(url)
          if (!response.ok) throw new Error(await response.text() || `Download failed (${response.status})`)
          await options.downloadBlobResponse(response, fallbackName, job)
        }
      })
    } catch (error: unknown) {
      toast.add({ title: 'Output download failed', description: extractApiErrorMessage(error, 'Failed to download output'), color: 'error' })
    }
  }

  async function downloadOutput(output: ActionOutput) {
    if (!options.selectedWorkspace.value) return
    await download(
      `/api/workspaces/${options.selectedWorkspace.value}/projects/${options.projectId}/outputs/${output.id}/download`,
      `${output.processorKey}-${output.id}.zip`, output.processorName
    )
  }

  async function downloadOutputFile(output: ActionOutput, file: ActionOutputFile) {
    if (!options.selectedWorkspace.value) return
    await download(
      `/api/workspaces/${options.selectedWorkspace.value}/projects/${options.projectId}/outputs/${output.id}/files/${file.id}/download`,
      file.fileName, `${output.processorName} · ${file.fileName}`
    )
  }

  async function openOutputShare(output: ActionOutput) {
    if (!options.canManageOutputs.value) return
    const instance = (options.outputShareSlideover as OutputShareSlideover).open({ projectId: options.projectId, output })
    if (await instance.result) await refreshOutputs()
  }

  async function deleteOutput(output: ActionOutput) {
    if (!options.canManageOutputs.value || !options.selectedWorkspace.value) return
    const confirmation = (options.deleteSlideover as DeleteSlideover).open({
      name: `the output from ${output.processorName}`,
      entityType: 'Action output',
      warningDetails: [`${output.fileCount} file${output.fileCount === 1 ? '' : 's'} will be permanently removed.`, 'Any active public share will be revoked.']
    })
    if (!await confirmation.result) return
    try {
      await $fetch(`/api/workspaces/${options.selectedWorkspace.value}/projects/${options.projectId}/outputs/${output.id}`, { method: 'DELETE' })
      await refreshOutputs()
      toast.add({ title: 'Output deleted', color: 'success' })
    } catch (error: unknown) {
      toast.add({ title: 'Delete failed', description: extractApiErrorMessage(error, 'Failed to delete output'), color: 'error' })
    }
  }

  return {
    outputsError, outputsPending, refreshOutputs, outputsForPanel, outputSummary,
    isOutputSidebarVisible, isOutputSlideoverOpen, isOutputPanelVisible,
    toggleOutputPanel, closeOutputPanel, downloadOutput, downloadOutputFile, openOutputShare, deleteOutput
  }
}
