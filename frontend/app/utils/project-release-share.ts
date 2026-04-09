export function buildProjectReleaseShareCurlSnippet(downloadUrl: string, secret: string): string {
  return `curl -fL --retry 3 -H "Authorization: Bearer ${secret}" "${downloadUrl}" -o release.larex-project.zip`
}

export function buildProjectReleaseShareWgetSnippet(downloadUrl: string, secret: string): string {
  return `wget --header="Authorization: Bearer ${secret}" -O release.larex-project.zip "${downloadUrl}"`
}
