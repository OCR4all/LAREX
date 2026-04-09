import { describe, expect, it } from 'vitest'
import { buildDatasetReleaseShareCurlSnippet, buildDatasetReleaseShareWgetSnippet } from '../dataset-release-share'

describe('dataset release share snippets', () => {
  it('builds a curl command with bearer authorization', () => {
    expect(buildDatasetReleaseShareCurlSnippet('https://example.test/download', 'secret-token'))
      .toContain('Authorization: Bearer secret-token')
  })

  it('builds a wget command with bearer authorization', () => {
    expect(buildDatasetReleaseShareWgetSnippet('https://example.test/download', 'secret-token'))
      .toContain('--header="Authorization: Bearer secret-token"')
  })
})
