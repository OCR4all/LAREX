const MAX_PROJECT_NAME_LENGTH = 100
const IMPORTED_NAME_SUFFIX = ' (imported)'

export function resolveProjectPackageRenameName(
  projectName: string,
  suggestedProjectName?: string | null
) {
  const suggested = typeof suggestedProjectName === 'string'
    ? suggestedProjectName.trim()
    : ''
  if (suggested) return suggested

  const source = typeof projectName === 'string' && projectName.trim()
    ? projectName.trim()
    : 'Imported Project'
  const availableBaseLength = MAX_PROJECT_NAME_LENGTH - IMPORTED_NAME_SUFFIX.length
  const base = source.slice(0, availableBaseLength).trimEnd()
  return `${base}${IMPORTED_NAME_SUFFIX}`
}

export function projectPackageRenameNameError(value: unknown) {
  const normalized = typeof value === 'string' ? value.trim() : ''
  if (!normalized) return 'Enter a name for the imported project.'
  if (normalized.length > MAX_PROJECT_NAME_LENGTH) {
    return `Project names may contain at most ${MAX_PROJECT_NAME_LENGTH} characters.`
  }
  return undefined
}
