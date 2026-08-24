import { describe, expect, it } from 'vitest'
import { buildReleaseShareCurlSnippet, buildReleaseShareWgetSnippet } from '../release-share'

const downloadUrl = 'https://example.test/download'
const secret = 'secret-token'

describe('release share snippets', () => {
  it('builds exact curl commands for dataset and project archives', () => {
    expect(buildReleaseShareCurlSnippet('dataset', downloadUrl, secret)).toBe(
      'curl -fL --retry 3 -H "Authorization: Bearer secret-token" "https://example.test/download" -o release.larex-dataset.zip'
    )
    expect(buildReleaseShareCurlSnippet('project', downloadUrl, secret)).toBe(
      'curl -fL --retry 3 -H "Authorization: Bearer secret-token" "https://example.test/download" -o release.larex-project.zip'
    )
  })

  it('builds exact wget commands for dataset and project archives', () => {
    expect(buildReleaseShareWgetSnippet('dataset', downloadUrl, secret)).toBe(
      'wget --header="Authorization: Bearer secret-token" -O release.larex-dataset.zip "https://example.test/download"'
    )
    expect(buildReleaseShareWgetSnippet('project', downloadUrl, secret)).toBe(
      'wget --header="Authorization: Bearer secret-token" -O release.larex-project.zip "https://example.test/download"'
    )
  })
})
