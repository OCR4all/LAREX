import type { ExportDialogMode, ExportDialogResult } from '@/types/project-page'
import {
  normalizeDocxOptions,
  normalizeExportFormat,
  normalizePageXmlVersion,
  normalizePdfProfile,
  normalizeSpreadsheetProfiles,
  normalizeTeiProfile,
  normalizeTextLevel,
  PAGE_XML_PRIMARY_VERSION
} from '@/utils/project-export'

type SlideoverInstance<T> = { result: Promise<T> }
type ExportTargetSlideover = { open: (props: Record<string, unknown>) => SlideoverInstance<ExportDialogResult | null> }
type ConfirmSlideover = { open: (props: Record<string, unknown>) => SlideoverInstance<boolean> }

export function useProjectExportDialog(exportTargetSlideover: ExportTargetSlideover, confirmSlideover: ConfirmSlideover) {
  async function requestExportOptions(mode: ExportDialogMode): Promise<ExportDialogResult | null> {
    const selector = exportTargetSlideover.open({
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
    if (!result) return null

    const normalizedFormat = normalizeExportFormat(result.format)
    const normalizedTargetVersion = normalizePageXmlVersion(result.targetPageXmlVersion)
    const confirmedTarget = await confirmLegacyPageXmlVersion(normalizedTargetVersion)
    if (!confirmedTarget) return null

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
      embeddedOutputs: result.embeddedOutputs.flatMap((output) => {
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
    if (selectedVersion === PAGE_XML_PRIMARY_VERSION) return selectedVersion

    const confirmation = confirmSlideover.open({
      title: 'Confirm Legacy PAGE XML Export',
      message: 'Exporting to an older PAGE XML schema may drop PAGE 2019-only data. Continue anyway?',
      confirmLabel: 'Export anyway',
      confirmColor: 'warning',
      confirmIcon: 'i-lucide-triangle-alert'
    })
    return await confirmation.result ? selectedVersion : null
  }

  return { requestExportOptions }
}
