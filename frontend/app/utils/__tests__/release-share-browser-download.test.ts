import { describe, expect, it } from 'vitest'
import { buildReleaseShareBrowserDownloadUrl } from '../release-share-browser-download'

describe('release share browser download URLs', () => {
  it('converts absolute dataset share URL to absolute browser URL', () => {
    expect(buildReleaseShareBrowserDownloadUrl('https://example.test/api/public/dataset-releases/share-123/download'))
      .toBe('https://example.test/share/share-123')
  })

  it('converts relative project share URL to relative browser URL', () => {
    expect(buildReleaseShareBrowserDownloadUrl('/api/public/project-releases/share-abc/download'))
      .toBe('/share/share-abc')
  })

  it('returns null for unsupported URLs', () => {
    expect(buildReleaseShareBrowserDownloadUrl('https://example.test/not-a-share-url'))
      .toBeNull()
  })
})
