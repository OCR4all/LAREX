import { describe, expect, it } from 'vitest'
import { buildProjectReleaseShareCurlSnippet, buildProjectReleaseShareWgetSnippet } from '../project-release-share'

describe('project release share snippets', () => {
  it('builds a curl command with bearer authorization', () => {
    expect(buildProjectReleaseShareCurlSnippet('https://example.test/download', 'secret-token'))
      .toContain('Authorization: Bearer secret-token')
  })

  it('builds a wget command with bearer authorization', () => {
    expect(buildProjectReleaseShareWgetSnippet('https://example.test/download', 'secret-token'))
      .toContain('--header="Authorization: Bearer secret-token"')
  })
})
