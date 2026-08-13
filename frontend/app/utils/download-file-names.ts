const INVALID_DOWNLOAD_FILE_NAME_CHARACTERS = /[\\\\/:*?"<>|]+/g

function replaceControlCharacters(value: string): string {
  return Array.from(value, (character) => {
    const codePoint = character.codePointAt(0)
    return codePoint !== undefined && (codePoint <= 0x1F || codePoint === 0x7F) ? ' ' : character
  }).join('')
}

export function sanitizeDownloadFileName(value: string | null | undefined, fallback: string): string {
  const normalized = replaceControlCharacters(value ?? '')
    .replace(INVALID_DOWNLOAD_FILE_NAME_CHARACTERS, ' ')
    .replace(/\s+/g, ' ')
    .trim()

  if (!normalized || normalized === '.' || normalized === '..') return fallback
  return normalized
}

export function buildDownloadFileName(
  baseName: string | null | undefined,
  suffix: string,
  fallbackBaseName: string
): string {
  return `${sanitizeDownloadFileName(baseName, fallbackBaseName)}${suffix}`
}

export function buildToolkitPackageFileName(name: string | null | undefined, fallback = 'toolkit'): string {
  return buildDownloadFileName(name, '.larex-toolkit.json', fallback)
}

export function buildProjectBasicExportFileName(name: string | null | undefined): string {
  return buildDownloadFileName(name, ' - flat export.zip', 'project')
}

export function buildProjectPackageFileName(name: string | null | undefined): string {
  return buildDownloadFileName(name, ' - LAREX package.larex-project.zip', 'project')
}

export function buildDatasetPackageFileName(name: string | null | undefined): string {
  return buildDownloadFileName(name, ' - LAREX dataset.larex-dataset.zip', 'dataset')
}

export function buildBatchProjectExportFileName(): string {
  return 'larex-projects-batch-export.zip'
}

export function buildSharedReleaseFileName(sharePublicId: string | null | undefined): string {
  const baseName = sharePublicId ? `larex-shared-release-${sharePublicId}` : null
  return buildDownloadFileName(baseName, '.zip', 'larex-shared-release')
}

export function buildActionOutputFileName(name: string | null | undefined, completedAt?: string | null): string {
  const timestampDigits = completedAt?.match(/\d/g)?.join('').slice(0, 14)
  const timestamp = timestampDigits && timestampDigits.length === 14
    ? `${timestampDigits.slice(0, 8)}-${timestampDigits.slice(8)}`
    : 'output'
  return `${sanitizeDownloadFileName(name, 'action-output')}-${timestamp}.zip`
}
