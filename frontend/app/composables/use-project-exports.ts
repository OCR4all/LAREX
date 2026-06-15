import type { Ref } from 'vue'
import type {
  DocxOptions,
  ExportDialogMode,
  ExportDialogResult,
  ExportFormat,
  Page,
  PdfProfile,
  ProjectActionScope,
  ProjectData,
  SpreadsheetProfile,
  TeiProfile,
  TextLevel
} from '@/types/project-page'

const PAGE_XML_PRIMARY_VERSION = '2019-07-15'

type SlideoverInstance<T> = {
  result: Promise<T>
}

type ExportTargetSlideover = {
  open: (props: Record<string, unknown>) => SlideoverInstance<ExportDialogResult | null>
}

type ConfirmSlideover = {
  open: (props: Record<string, unknown>) => SlideoverInstance<boolean>
}

type ProjectExportsOptions = {
  projectId: string
  selectedWorkspace: Ref<string | null | undefined>
  project: Ref<ProjectData | null | undefined>
  selectedPageIds: Ref<Set<string>>
  exportTargetSlideover: ExportTargetSlideover
  confirmSlideover: ConfirmSlideover
}

export function useProjectExports(options: ProjectExportsOptions) {
  const toast = useToast()
  const backgroundDownloads = useBackgroundDownloads()

  function getExportPageIds(scope: ProjectActionScope): string[] | null {
    return scope === 'selection' ? Array.from(options.selectedPageIds.value) : null
  }

  async function exportBasicProject(scope: ProjectActionScope = 'all') {
    if (!options.selectedWorkspace.value || !options.project.value) return

    const exportOptions = await requestExportOptions('basic')
    if (!exportOptions) return

    const payload = {
      pageIds: getExportPageIds(scope),
      targetPageXmlVersion: exportOptions.targetPageXmlVersion,
      embeddedOutputs: exportOptions.embeddedOutputs
    }
    const fallbackName = `${options.project.value.name.replace(/\s+/g, '-').toLowerCase()}.zip`

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

          await backgroundDownloads.downloadBlobResponse(response, fallbackName, job)
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

    const exportOptions = await requestExportOptions('package')
    if (!exportOptions) return

    const payload = {
      pageIds: getExportPageIds(scope),
      targetPageXmlVersion: exportOptions.targetPageXmlVersion,
      embeddedOutputs: exportOptions.embeddedOutputs
    }
    const fallbackName = `${options.project.value.name.replace(/\s+/g, '-').toLowerCase()}.larex-project.zip`

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

          await backgroundDownloads.downloadBlobResponse(response, fallbackName, job)
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

    const exportOptions = await requestExportOptions('project')
    if (!exportOptions) return

    const format = normalizeExportFormat(exportOptions.format)
    if (!format) return

    const fallbackName = `${options.project.value.name.replace(/\s+/g, '-').toLowerCase()}.${formatExtension(format)}`
    const payload = {
      format,
      pageIds: getExportPageIds(scope),
      includePageDelimiters: exportOptions.includePageDelimiters,
      textLevel: normalizeTextLevel(exportOptions.textLevel),
      textVariantIndex: Number.isFinite(exportOptions.textVariantIndex) ? exportOptions.textVariantIndex : 0,
      pdfProfile: normalizePdfProfile(exportOptions.pdfProfile),
      teiProfile: normalizeTeiProfile(exportOptions.teiProfile),
      spreadsheetProfiles: normalizeSpreadsheetProfiles(exportOptions.spreadsheetProfiles),
      docxOptions: normalizeDocxOptions(exportOptions.docxOptions)
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

          await backgroundDownloads.downloadBlobResponse(response, fallbackName, job)
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

  async function exportPageOutput(page: Page) {
    const exportOptions = await requestExportOptions('page')
    if (!exportOptions) return

    const format = normalizeExportFormat(exportOptions.format)
    if (!format) return

    if (format === 'PAGE_XML') {
      await exportPageXml(page, exportOptions.targetPageXmlVersion)
      return
    }

    const fallbackName = `${page.name}.${formatExtension(format)}`

    try {
      await backgroundDownloads.runBackgroundJob({
        title: 'Exporting page output',
        subtitle: `${page.name} · ${format}`,
        statusLabel: 'Generating',
        completedLabel: 'Exported',
        icon: 'i-lucide-file-output',
        task: async (job) => {
          const response = await fetch(`/api/projects/${options.projectId}/pages/${page.id}/export`, {
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
              docxOptions: normalizeDocxOptions(exportOptions.docxOptions)
            })
          })

          if (!response.ok) {
            throw new Error(`Export failed (${response.status})`)
          }

          await backgroundDownloads.downloadBlobResponse(response, fallbackName, job)
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

  async function exportPageXml(page: Page, targetPageXmlVersion?: string) {
    try {
      const xmlFiles = await $fetch<{ id: string }[]>(`/api/projects/${options.projectId}/pages/${page.id}/xml`)
      if (!xmlFiles?.length) {
        toast.add({
          title: 'No XML files',
          description: 'This page has no XML files to export.',
          color: 'warning'
        })
        return
      }

      await backgroundDownloads.runBackgroundJob({
        title: 'Exporting PAGE XML',
        subtitle: page.name,
        statusLabel: 'Converting',
        completedLabel: 'Exported',
        icon: 'i-lucide-file-code',
        task: async (job) => {
          const xmlId = xmlFiles[0]!.id
          const selectedVersion = targetPageXmlVersion ?? PAGE_XML_PRIMARY_VERSION
          const query = new URLSearchParams({ targetPageXmlVersion: selectedVersion })
          const response = await fetch(`/api/projects/${options.projectId}/pages/xml/${xmlId}/export?${query.toString()}`)
          if (!response.ok) {
            throw new Error(`Export failed (${response.status})`)
          }

          await backgroundDownloads.downloadBlobResponse(response, `${page.name}.xml`, job)
        }
      })

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

  async function requestExportOptions(mode: ExportDialogMode): Promise<ExportDialogResult | null> {
    const selector = options.exportTargetSlideover.open({
      mode,
      title: mode === 'page'
        ? 'Export Page'
        : mode === 'project'
          ? 'Export Converted Output'
          : mode === 'basic'
            ? 'Export Project'
            : 'Export LAREX Package',
      description: mode === 'page'
        ? 'Choose the page export format and options.'
        : mode === 'project'
          ? 'Choose the converted output format and options.'
          : mode === 'basic'
            ? 'Create a flat zip with image variants and XML files using original filenames.'
            : 'Create a structured package for transfer to another LAREX instance without data loss.',
      initialTargetVersion: PAGE_XML_PRIMARY_VERSION,
      confirmLabel: mode === 'package' ? 'Export LAREX Package' : 'Export'
    })

    const result = await selector.result
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

    const confirmation = options.confirmSlideover.open({
      title: 'Confirm Legacy PAGE XML Export',
      message: 'Exporting to an older PAGE XML schema may drop PAGE 2019-only data. Continue anyway?',
      confirmLabel: 'Export anyway',
      confirmColor: 'warning',
      confirmIcon: 'i-lucide-triangle-alert'
    })

    const confirmed = await confirmation.result
    return confirmed ? selectedVersion : null
  }

  return {
    PAGE_XML_PRIMARY_VERSION,
    downloadBlobResponse: backgroundDownloads.downloadBlobResponse,
    exportBasicProject,
    exportPageOutput,
    exportPageXml,
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
