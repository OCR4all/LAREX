export function buildDatasetReleaseShareCurlSnippet(downloadUrl: string, secret: string): string {
  return `curl -fL --retry 3 -H "Authorization: Bearer ${secret}" "${downloadUrl}" -o release.larex-dataset.zip`
}

export function buildDatasetReleaseShareWgetSnippet(downloadUrl: string, secret: string): string {
  return `wget --header="Authorization: Bearer ${secret}" -O release.larex-dataset.zip "${downloadUrl}"`
}
