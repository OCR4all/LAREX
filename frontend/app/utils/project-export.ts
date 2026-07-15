import type {
  DocxOptions,
  ExportFormat,
  PdfProfile,
  SpreadsheetProfile,
  TeiProfile,
  TextLevel
} from '@/types/project-page'

export const PAGE_XML_PRIMARY_VERSION = '2019-07-15'

export function normalizePageXmlVersion(value: unknown): string {
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

export function normalizeExportFormat(value: unknown): ExportFormat | null {
  if (typeof value === 'string') return value as ExportFormat
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return value.value as ExportFormat
  }
  return null
}

export function normalizeTextLevel(value: unknown): TextLevel {
  if (typeof value === 'string' && ['PAGE', 'REGION', 'TEXT_LINE'].includes(value)) return value as TextLevel
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return normalizeTextLevel(value.value)
  }
  return 'PAGE'
}

export function normalizePdfProfile(value: unknown): PdfProfile {
  if (typeof value === 'string' && ['SEARCHABLE', 'IMAGES_ONLY', 'TEXT_PAGES', 'PDFA_SEARCHABLE'].includes(value)) return value as PdfProfile
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return normalizePdfProfile(value.value)
  }
  return 'SEARCHABLE'
}

export function normalizeTeiProfile(value: unknown): TeiProfile {
  if (typeof value === 'string' && ['STANDARD', 'LAYOUT'].includes(value)) return value as TeiProfile
  if (value && typeof value === 'object' && 'value' in value && typeof value.value === 'string') {
    return normalizeTeiProfile(value.value)
  }
  return 'STANDARD'
}

export function normalizeSpreadsheetProfiles(value: unknown): SpreadsheetProfile[] {
  if (Array.isArray(value)) {
    return value
      .map(item => typeof item === 'string' ? item : (item && typeof item === 'object' && 'value' in item && typeof item.value === 'string' ? item.value : null))
      .filter((item): item is SpreadsheetProfile => item === 'PAGE_METADATA' || item === 'TAGS' || item === 'REGIONS')
  }
  return ['PAGE_METADATA']
}

export function normalizeDocxOptions(value: unknown): DocxOptions {
  const source = value && typeof value === 'object' ? value as Partial<DocxOptions> : {}
  return {
    preserveLineBreaks: source.preserveLineBreaks !== false,
    forcePageBreaks: source.forcePageBreaks !== false,
    includeImageNames: source.includeImageNames === true,
    markUnclearWords: source.markUnclearWords === true
  }
}

export function formatProjectExportExtension(format: ExportFormat): string {
  switch (format) {
    case 'PAGE_XML': return 'xml'
    case 'ALTO_XML': return 'alto.xml'
    case 'TXT': return 'txt'
    case 'PDF': return 'pdf'
    case 'DOCX': return 'docx'
    case 'TEI': return 'tei.xml'
    case 'CSV': return 'csv'
    case 'XLSX': return 'xlsx'
  }
}
