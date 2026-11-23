import type { Ref } from 'vue'
import type {
  Page,
  ProjectActionScope,
  ProjectData
} from '@/types/project-page'
import type { ConfirmSlideover, ExportTargetSlideover } from '@/composables/use-project-export-dialog'
import { formatProjectExportExtension as formatExtension } from '@/utils/project-export'

type ProjectExportsOptions = {
  projectId: string
  selectedWorkspace: Ref<string | null | undefined>
  project: Ref<ProjectData | null | undefined>
  pages: Ref<Page[] | null | undefined>
  selectedPageIds: Ref<Set<string>>
  exportTargetSlideover: ExportTargetSlideover
  confirmSlideover: ConfirmSlideover
}

export function useProjectExports(options: ProjectExportsOptions) {
  const toast = useToast()
  const backgroundDownloads = useBackgroundDownloads()
  const { requestExportOptions } = useProjectExportDialog(options.exportTargetSlideover, options.confirmSlideover)
  const pageExport = usePageExport(options.exportTargetSlideover, options.confirmSlideover)

  function getExportPageIds(scope: ProjectActionScope): string[] | null {
    return scope === 'selection' ? Array.from(options.selectedPageIds.value) : null
  }

  function getExportPages(scope: ProjectActionScope): Page[] {
    const pages = options.pages.value ?? []
    if (scope !== 'selection') return pages
    const selected = options.selectedPageIds.value
    return pages.filter(page => selected.has(page.id))
  }

  async function exportBasicProject(scope: ProjectActionScope = 'all') {
    if (!options.selectedWorkspace.value || !options.project.value) return

    const exportOptions = await requestExportOptions('basic', {
      suggestedBaseName: options.project.value.name,
      prepareDownload: backgroundDownloads.prepareDownload,
      imageVariantPages: getExportPages(scope)
    })
    if (!exportOptions) return

    const payload = {
      pageIds: getExportPageIds(scope),
      targetPageXmlVersion: exportOptions.targetPageXmlVersion,
      embeddedOutputs: exportOptions.embeddedOutputs
    }
    const fallbackName = buildProjectBasicExportFileName(options.project.value.name)

    try {
      await backgroundDownloads.runBackgroundJob({
        title: 'Exporting project',
        subtitle: options.project.value.name,
        statusLabel: 'Generating',
        completedLabel: 'Exported',
        icon: 'i-lucide-file-archive',
        task: async (job) => {
          const response = await fetch(`/api/workspaces/${options.selectedWorkspace.value}/projects/${options.projectId}/export-basic`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
          })

          if (!response.ok) {
            throw new Error(`Export failed (${response.status})`)
          }

          await backgroundDownloads.downloadBlobResponse(response, fallbackName, job, exportOptions.downloadTarget)
        }
      })

      toast.add({
        title: 'Project exported',
        color: 'success',
        icon: 'i-lucide-download'
      })
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Failed to export project'
      toast.add({
        title: 'Export failed',
        description: message,
        color: 'error'
      })
    }
  }

  async function exportProjectPackage(scope: ProjectActionScope = 'all') {
    if (!options.selectedWorkspace.value || !options.project.value) return

    const exportOptions = await requestExportOptions('package', {
      suggestedBaseName: options.project.value.name,
      prepareDownload: backgroundDownloads.prepareDownload,
      imageVariantPages: getExportPages(scope)
    })
    if (!exportOptions) return

    const payload = {
      pageIds: getExportPageIds(scope),
      targetPageXmlVersion: exportOptions.targetPageXmlVersion,
      embeddedOutputs: exportOptions.embeddedOutputs,
      includeXmlHistory: exportOptions.includeXmlHistory
    }
    const fallbackName = buildProjectPackageFileName(options.project.value.name)

    try {
      await backgroundDownloads.runBackgroundJob({
        title: 'Exporting project package',
        subtitle: options.project.value.name,
        statusLabel: 'Generating',
        completedLabel: 'Exported',
        icon: 'i-lucide-package',
        task: async (job) => {
          const response = await fetch(`/api/workspaces/${options.selectedWorkspace.value}/projects/${options.projectId}/export-package`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
          })

          if (!response.ok) {
            throw new Error(`Export failed (${response.status})`)
          }

          await backgroundDownloads.downloadBlobResponse(response, fallbackName, job, exportOptions.downloadTarget)
        }
      })

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

  async function exportProjectOutput(scope: ProjectActionScope = 'all') {
    if (!options.selectedWorkspace.value || !options.project.value) return

    const exportOptions = await requestExportOptions('project', {
      suggestedBaseName: options.project.value.name,
      prepareDownload: backgroundDownloads.prepareDownload,
      imageVariantPages: getExportPages(scope)
    })
    if (!exportOptions) return

    const format = normalizeExportFormat(exportOptions.format)
    if (!format) return

    const fallbackName = buildDownloadFileName(options.project.value.name, `.${formatExtension(format)}`, 'project')
    const payload = {
      format,
      pageIds: getExportPageIds(scope),
      includePageDelimiters: exportOptions.includePageDelimiters,
      textLevel: normalizeTextLevel(exportOptions.textLevel),
      textVariantIndex: Number.isFinite(exportOptions.textVariantIndex) ? exportOptions.textVariantIndex : 0,
      pdfProfile: normalizePdfProfile(exportOptions.pdfProfile),
      teiProfile: normalizeTeiProfile(exportOptions.teiProfile),
      spreadsheetProfiles: normalizeSpreadsheetProfiles(exportOptions.spreadsheetProfiles),
      docxOptions: normalizeDocxOptions(exportOptions.docxOptions),
      imageVariantSelection: exportOptions.imageVariantSelection
    }

    try {
      await backgroundDownloads.runBackgroundJob({
        title: 'Exporting project output',
        subtitle: `${options.project.value.name} · ${format}`,
        statusLabel: 'Generating',
        completedLabel: 'Exported',
        icon: 'i-lucide-file-output',
        task: async (job) => {
          const response = await fetch(`/api/workspaces/${options.selectedWorkspace.value}/projects/${options.projectId}/export`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
          })

          if (!response.ok) {
            throw new Error(`Export failed (${response.status})`)
          }

          await backgroundDownloads.downloadBlobResponse(response, fallbackName, job, exportOptions.downloadTarget)
        }
      })

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

  return {
    PAGE_XML_PRIMARY_VERSION,
    downloadBlobResponse: backgroundDownloads.downloadBlobResponse,
    exportBasicProject,
    exportPageOutput: (page: Page) => pageExport.exportPageOutput(options.projectId, page),
    exportProjectOutput,
    exportProjectPackage,
    formatExtension,
    getExportPageIds,
    normalizeDocxOptions,
    normalizeExportFormat,
    normalizePageXmlVersion,
    normalizePdfProfile,
    normalizeSpreadsheetProfiles,
    normalizeTeiProfile,
    normalizeTextLevel,
    requestExportOptions
  }
}
