const RELEASE_DOWNLOAD_PATH_PATTERN = /(?:^|\/)(?:api\/)?public\/(?:dataset-releases|project-releases)\/([^/]+)\/download\/?$/i

function stripQueryAndHash(value: string): string {
  const [withoutHash = ''] = value.split('#', 1)
  const [withoutQuery = ''] = withoutHash.split('?', 1)
  return withoutQuery
}

function parseAbsoluteUrl(value: string): URL | null {
  try {
    return new URL(value)
  } catch {
    return null
  }
}

export function buildReleaseShareBrowserDownloadUrl(downloadUrl: string): string | null {
  if (!downloadUrl || !downloadUrl.trim()) {
    return null
  }

  const absolute = parseAbsoluteUrl(downloadUrl)
  const candidatePath = absolute ? absolute.pathname : stripQueryAndHash(downloadUrl)
  const match = candidatePath.match(RELEASE_DOWNLOAD_PATH_PATTERN)
  if (!match) {
    return null
  }

  const encodedSharePublicId = match[1]
  if (!encodedSharePublicId) {
    return null
  }

  const sharePublicId = decodeURIComponent(encodedSharePublicId)
  const browserPath = `/share/${encodeURIComponent(sharePublicId)}`

  if (!absolute) {
    return browserPath
  }

  return `${absolute.origin}${browserPath}`
}
