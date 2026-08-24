export type ReleaseArchiveKind = 'dataset' | 'project'

function releaseArchiveName(kind: ReleaseArchiveKind): string {
  return `release.larex-${kind}.zip`
}

export function buildReleaseShareCurlSnippet(
  kind: ReleaseArchiveKind,
  downloadUrl: string,
  secret: string
): string {
  return `curl -fL --retry 3 -H "Authorization: Bearer ${secret}" "${downloadUrl}" -o ${releaseArchiveName(kind)}`
}

export function buildReleaseShareWgetSnippet(
  kind: ReleaseArchiveKind,
  downloadUrl: string,
  secret: string
): string {
  return `wget --header="Authorization: Bearer ${secret}" -O ${releaseArchiveName(kind)} "${downloadUrl}"`
}
