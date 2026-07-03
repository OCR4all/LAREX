import { describe, expect, it } from 'vitest'
import {
  createDocumentFromFlatData,
  deserializeDocument
} from '../document-serialization'

describe('document serialization metadata', () => {
  it('uses the supplied username when creating a document', () => {
    const document = createDocumentFromFlatData('page-1', [], [], 'tester')

    expect(document.metadata.creator).toBe('tester')
  })

  it('uses the supplied username when deserialized metadata has no creator', () => {
    const document = deserializeDocument(JSON.stringify({
      metadata: {},
      page: {
        imageFilename: 'page.png',
        imageWidth: 1200,
        imageHeight: 1800,
        regions: []
      }
    }), 'tester')

    expect(document.metadata.creator).toBe('tester')
  })

  it('preserves an existing creator when deserializing', () => {
    const document = deserializeDocument(JSON.stringify({
      metadata: { creator: 'original-author' },
      page: {
        imageFilename: 'page.png',
        imageWidth: 1200,
        imageHeight: 1800,
        regions: []
      }
    }), 'tester')

    expect(document.metadata.creator).toBe('original-author')
  })
})
