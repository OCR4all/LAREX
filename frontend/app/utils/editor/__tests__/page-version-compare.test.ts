import { describe, expect, it } from 'vitest'
import type { PageDto } from '@/types/page-dto'
import { comparePageVersions, groupPageVersionChanges, textDiffSegments } from '../page-version-compare'

function page(regions: PageDto['regions']): PageDto {
  return {
    imageWidth: 1000,
    imageHeight: 1000,
    regions
  }
}

function polygon(offset = 0) {
  return {
    points: [
      [-0.5 + offset, -0.5],
      [0.5 + offset, -0.5],
      [0.5 + offset, 0.5],
      [-0.5 + offset, 0.5]
    ] as [number, number][]
  }
}

function pixelToWorldX(pixel: number, width = 1000): number {
  return (pixel / width) * 2 - 1
}

function pixelToWorldY(pixel: number, height = 1000): number {
  return 1 - (pixel / height) * 2
}

function polygonFromPixels(subpixelOffset = 0) {
  return {
    points: [
      [pixelToWorldX(100 + subpixelOffset), pixelToWorldY(100 + subpixelOffset)],
      [pixelToWorldX(500 + subpixelOffset), pixelToWorldY(100 + subpixelOffset)],
      [pixelToWorldX(500 + subpixelOffset), pixelToWorldY(500 + subpixelOffset)],
      [pixelToWorldX(100 + subpixelOffset), pixelToWorldY(500 + subpixelOffset)]
    ] as [number, number][]
  }
}

describe('page-version-compare', () => {
  it('detects added, removed, geometry, text, metadata, and unchanged elements', () => {
    const current = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygon(),
        comments: 'changed metadata',
        textLines: [
          {
            id: 'line-a',
            coords: polygon(),
            baseline: { points: [[-0.4, 0], [0.4, 0]] },
            textContentVariants: [{ index: 0, unicode: 'new text' }]
          },
          {
            id: 'line-added',
            coords: polygon(),
            textContentVariants: [{ index: 0, unicode: 'added' }]
          }
        ]
      },
      {
        id: 'region-geometry',
        kind: 'ImageRegion',
        coords: polygon(0.1)
      },
      {
        id: 'region-added',
        kind: 'GraphicRegion',
        coords: polygon()
      },
      {
        id: 'region-unchanged',
        kind: 'GraphicRegion',
        coords: polygon()
      }
    ])

    const compared = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygon(),
        comments: 'old metadata',
        textLines: [
          {
            id: 'line-a',
            coords: polygon(),
            baseline: { points: [[-0.4, 0.1], [0.4, 0.1]] },
            textContentVariants: [{ index: 0, unicode: 'old text' }]
          },
          {
            id: 'line-removed',
            coords: polygon(),
            textContentVariants: [{ index: 0, unicode: 'removed' }]
          }
        ]
      },
      {
        id: 'region-geometry',
        kind: 'ImageRegion',
        coords: polygon()
      },
      {
        id: 'region-unchanged',
        kind: 'GraphicRegion',
        coords: polygon()
      }
    ])

    const summary = comparePageVersions(current, compared)
    const byId = Object.fromEntries(summary.changes.map(change => [change.id, change.changeType]))

    expect(byId['region-added']).toBe('added')
    expect(byId['line-added']).toBe('added')
    expect(byId['line-removed']).toBe('removed')
    expect(byId['region-geometry']).toBe('geometry')
    expect(byId['line-a']).toBe('text')
    expect(byId['baseline:line-a']).toBe('geometry')
    expect(byId['region-a']).toBe('metadata')
    expect(byId['region-unchanged']).toBeUndefined()
  })

  it('does not report metadata changes for incidental empty conversion fields', () => {
    const current = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygon(0.05),
        textLines: [
          {
            id: 'line-a',
            coords: polygon(),
            baseline: { points: [[-0.4, 0], [0.4, 0]] },
            textContentVariants: [{ index: 0, unicode: 'same text' }],
            words: [],
            alternativeImages: [],
            labels: [],
            userDefined: { attributes: [] }
          }
        ],
        nestedRegions: [],
        alternativeImages: [],
        labels: [],
        userDefined: { attributes: [] }
      }
    ])

    const compared = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygon(),
        textLines: [
          {
            id: 'line-a',
            coords: polygon(),
            baseline: { points: [[-0.4, 0], [0.4, 0]] },
            textContentVariants: [{ index: 0, unicode: 'same text' }]
          }
        ]
      }
    ])

    const summary = comparePageVersions(current, compared)
    const byId = Object.fromEntries(summary.changes.map(change => [change.id, change.changeType]))

    expect(byId).toEqual({ 'region-a': 'geometry' })
    expect(summary.counts.region.geometry).toBe(1)
    expect(summary.counts.region.metadata).toBe(0)
    expect(summary.counts.textline.metadata).toBe(0)
  })

  it('classifies text-only changes after PAGE XML coordinate quantization as text', () => {
    const current = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygonFromPixels(0.24),
        textLines: [
          {
            id: 'line-a',
            coords: polygonFromPixels(0.24),
            textContentVariants: [{ index: 0, unicode: 'new text' }]
          }
        ]
      }
    ])
    const compared = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygonFromPixels(),
        textLines: [
          {
            id: 'line-a',
            coords: polygonFromPixels(),
            textContentVariants: [{ index: 0, unicode: 'old text' }]
          }
        ]
      }
    ])

    const summary = comparePageVersions(current, compared)

    expect(summary.changes.map(change => [change.id, change.changeType])).toEqual([
      ['line-a', 'text']
    ])
    expect(summary.counts.textline.text).toBe(1)
    expect(summary.counts.region.geometry).toBe(0)
    expect(summary.counts.textline.geometry).toBe(0)
  })

  it('classifies metadata-only changes after PAGE XML coordinate quantization as metadata', () => {
    const current = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygonFromPixels(0.24),
        comments: 'new comment'
      }
    ])
    const compared = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygonFromPixels(),
        comments: 'old comment'
      }
    ])

    const summary = comparePageVersions(current, compared)

    expect(summary.changes.map(change => [change.id, change.changeType])).toEqual([
      ['region-a', 'metadata']
    ])
    expect(summary.counts.region.metadata).toBe(1)
    expect(summary.counts.region.geometry).toBe(0)
  })

  it('reports independent categories when one element has multiple kinds of changes', () => {
    const current = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygon(),
        textLines: [
          {
            id: 'line-a',
            coords: polygon(),
            comments: 'new comment',
            textContentVariants: [{ index: 0, unicode: 'new text' }]
          }
        ]
      }
    ])
    const compared = page([
      {
        id: 'region-a',
        kind: 'TextRegion',
        coords: polygon(),
        textLines: [
          {
            id: 'line-a',
            coords: polygon(),
            comments: 'old comment',
            textContentVariants: [{ index: 0, unicode: 'old text' }]
          }
        ]
      }
    ])

    const summary = comparePageVersions(current, compared)

    expect(summary.changes.map(change => [change.id, change.changeType])).toEqual([
      ['line-a', 'text'],
      ['line-a', 'metadata']
    ])
  })

  it('groups multiple change categories into one row per element', () => {
    const summary = comparePageVersions(
      page([
        {
          id: 'region-a',
          kind: 'TextRegion',
          coords: polygon(),
          textLines: [
            {
              id: 'line-a',
              coords: polygon(),
              comments: 'new comment',
              textContentVariants: [{ index: 0, unicode: 'new text' }]
            }
          ]
        }
      ]),
      page([
        {
          id: 'region-a',
          kind: 'TextRegion',
          coords: polygon(),
          textLines: [
            {
              id: 'line-a',
              coords: polygon(),
              comments: 'old comment',
              textContentVariants: [{ index: 0, unicode: 'old text' }]
            }
          ]
        }
      ])
    )

    const groups = groupPageVersionChanges(summary.changes)

    expect(groups).toHaveLength(1)
    expect(groups[0]).toMatchObject({
      key: 'textline:line-a',
      id: 'line-a',
      kind: 'textline',
      changeTypes: ['text', 'metadata']
    })
  })

  it('builds inline text diff segments', () => {
    expect(textDiffSegments('the new text', 'the old text')).toEqual([
      { value: 'the ', status: 'same' },
      { value: 'new', status: 'current' },
      { value: 'old', status: 'compared' },
      { value: ' text', status: 'same' }
    ])
  })
})
