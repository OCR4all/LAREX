import type { PreparedDownloadTarget } from '@/composables/use-background-downloads'
import type { ConfirmSlideover, ExportTargetSlideover } from '@/composables/use-project-export-dialog'
import type { ExportPageRef, ImageVariantSelection } from '@/types/project-page'
import {
  formatProjectExportExtension as formatExtension,
  readExportWarnings
} from '@/utils/project-export'

export function usePageExport(exportTargetSlideover: ExportTargetSlideover, confirmSlideover: ConfirmSlideover) {
  const toast = useToast()
  const backgroundDownloads = useBackgroundDownloads()
  const { requestExportOptions } = useProjectExportDialog(exportTargetSlideover, confirmSlideover)

  async function exportPageOutput(projectId: string, page: ExportPageRef, initialImageVariantSelection?: ImageVariantSelection) {
    const exportOptions = await requestExportOptions('page', {
      suggestedBaseName: page.name,
      prepareDownload: backgroundDownloads.prepareDownload,
      imageVariantPages: [page],
      initialImageVariantSelection
    })
    if (!exportOptions) return

    const format = normalizeExportFormat(exportOptions.format)
    if (!format) return

    if (format === 'PAGE_XML') {
      await exportPageXml(projectId, page, exportOptions.targetPageXmlVersion, exportOptions.downloadTarget)
      return
    }

    const fallbackName = buildDownloadFileName(page.name, `.${formatExtension(format)}`, 'page')

    try {
      await backgroundDownloads.runBackgroundJob({
        title: 'Exporting page output',
        subtitle: `${page.name} · ${format}`,
        statusLabel: 'Generating',
        completedLabel: 'Exported',
        icon: 'i-lucide-file-output',
        task: async (job) => {
          const response = await fetch(`/api/projects/${projectId}/pages/${page.id}/export`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              format,
              targetPageXmlVersion: exportOptions.targetPageXmlVersion,
              textLevel: normalizeTextLevel(exportOptions.textLevel),
              textVariantIndex: Number.isFinite(exportOptions.textVariantIndex) ? exportOptions.textVariantIndex : 0,
              pdfProfile: normalizePdfProfile(exportOptions.pdfProfile),
              teiProfile: normalizeTeiProfile(exportOptions.teiProfile),
              spreadsheetProfiles: normalizeSpreadsheetProfiles(exportOptions.spreadsheetProfiles),
              docxOptions: normalizeDocxOptions(exportOptions.docxOptions),
              imageVariantSelection: exportOptions.imageVariantSelection
            })
          })

          if (!response.ok) {
            throw new Error(`Export failed (${response.status})`)
          }

          await backgroundDownloads.downloadBlobResponse(response, fallbackName, job, exportOptions.downloadTarget)
        }
      })

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

  async function exportPageXml(projectId: string, page: ExportPageRef, targetPageXmlVersion?: string, downloadTarget?: PreparedDownloadTarget) {
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

      const selectedVersion = targetPageXmlVersion ?? PAGE_XML_PRIMARY_VERSION
      let warnings: string[] = []
      await backgroundDownloads.runBackgroundJob({
        title: 'Exporting PAGE XML',
        subtitle: page.name,
        statusLabel: 'Converting',
        completedLabel: 'Exported',
        icon: 'i-lucide-file-code',
        task: async (job) => {
          const xmlId = xmlFiles[0]!.id
          const query = new URLSearchParams({ targetPageXmlVersion: selectedVersion })
          const response = await fetch(`/api/projects/${projectId}/pages/xml/${xmlId}/export?${query.toString()}`)
          if (!response.ok) {
            throw new Error(`Export failed (${response.status})`)
          }
          warnings = readExportWarnings(response.headers)

          await backgroundDownloads.downloadBlobResponse(response, `${page.name}.xml`, job, downloadTarget)
        }
      })

      if (warnings.length) {
        toast.add({
          title: `Exported with changes for PAGE ${selectedVersion}`,
          description: warnings.join('\n'),
          color: 'warning',
          icon: 'i-lucide-triangle-alert',
          duration: 0,
          ui: { description: 'whitespace-pre-line break-words' }
        })
      }

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

  return { exportPageOutput }
}
