import { describe, expect, it } from 'vitest'
import type { Region } from '@/models/editor/region'
import { Polygon } from '@/models/editor/geometry'
import { TextLine } from '@/models/editor/text'
import {
  collectRegionIdsInReadingOrder,
  collectTextlineIdsInPageOrder,
  collectTextlineIdsInReadingOrder,
  getAdjacentTextlineId,
  getTextlineExplicitOrder
} from '../textline-navigation'

function makePolygon() {
  return new Polygon([[0, 0], [1, 0], [1, 1], [0, 1]])
}

function makeTextLine(id: string) {
  return new TextLine({ id, coords: makePolygon() })
}

describe('textline-navigation', () => {
  it('collects textline IDs in recursive PAGE traversal order', () => {
    const regions: Region[] = [
      {
        id: 'region-1',
        kind: 'TextRegion',
        coords: makePolygon(),
        textLines: [
          makeTextLine('line-1'),
          makeTextLine('line-2')
        ],
        regions: [
          {
            id: 'region-1a',
            kind: 'TextRegion',
            coords: makePolygon(),
            textLines: [makeTextLine('line-3')]
          }
        ]
      },
      {
        id: 'region-2',
        kind: 'ImageRegion',
        coords: makePolygon()
      },
      {
        id: 'region-3',
        kind: 'TextRegion',
        coords: makePolygon(),
        textLines: [makeTextLine('line-4')]
      }
    ]

    expect(collectTextlineIdsInPageOrder(regions)).toEqual([
      'line-1',
      'line-2',
      'line-3',
      'line-4'
    ])
  })

  it('returns adjacent IDs with wrap-around and fallback behavior', () => {
    const ordered = ['line-1', 'line-2', 'line-3']

    expect(getAdjacentTextlineId(ordered, 'line-1', -1)).toBe('line-3')
    expect(getAdjacentTextlineId(ordered, 'line-3', 1)).toBe('line-1')
    expect(getAdjacentTextlineId(ordered, 'missing', 1)).toBe('line-1')
    expect(getAdjacentTextlineId(ordered, 'missing', -1)).toBe('line-3')
    expect(getAdjacentTextlineId(ordered, null, 1)).toBe('line-1')
    expect(getAdjacentTextlineId([], 'line-1', 1)).toBeNull()
  })

  it('collects region IDs in nested PAGE reading order without duplicates', () => {
    expect(collectRegionIdsInReadingOrder({
      root: {
        kind: 'OrderedGroup',
        id: 'root',
        elements: [
          {
            kind: 'RegionRef',
            id: 'ro-3',
            regionRef: 'region-3'
          },
          {
            kind: 'OrderedGroup',
            id: 'group-1',
            regionRef: 'region-1',
            elements: [
              {
                kind: 'RegionRef',
                id: 'ro-2',
                regionRef: 'region-2'
              },
              {
                kind: 'RegionRef',
                id: 'ro-1-duplicate',
                regionRef: 'region-1'
              }
            ]
          }
        ]
      }
    })).toEqual(['region-3', 'region-1', 'region-2'])
  })

  it('collects textlines by reading order and appends unreferenced regions in PAGE order', () => {
    const nestedRegion: Region = {
      id: 'region-nested',
      kind: 'TextRegion',
      coords: makePolygon(),
      textLines: [makeTextLine('line-nested')]
    }
    const regions: Region[] = [
      {
        id: 'region-1',
        kind: 'TextRegion',
        coords: makePolygon(),
        textLines: [makeTextLine('line-1a'), makeTextLine('line-1b')],
        regions: [nestedRegion]
      },
      {
        id: 'region-2',
        kind: 'TextRegion',
        coords: makePolygon(),
        textLines: [makeTextLine('line-2')]
      },
      {
        id: 'region-3',
        kind: 'TextRegion',
        coords: makePolygon(),
        textLines: [makeTextLine('line-3')]
      }
    ]

    expect(collectTextlineIdsInReadingOrder(regions, {
      root: {
        kind: 'OrderedGroup',
        id: 'root',
        elements: [
          { kind: 'RegionRef', id: 'ro-3', regionRef: 'region-3' },
          { kind: 'RegionRef', id: 'ro-1', regionRef: 'region-1' },
          { kind: 'RegionRef', id: 'ro-1-duplicate', regionRef: 'region-1' }
        ]
      }
    })).toEqual([
      'line-3',
      'line-1a',
      'line-1b',
      'line-nested',
      'line-2'
    ])
  })

  it('falls back to recursive PAGE order when reading order is absent', () => {
    const regions: Region[] = [
      {
        id: 'region-1',
        kind: 'TextRegion',
        coords: makePolygon(),
        textLines: [makeTextLine('line-1')],
        regions: [{
          id: 'region-1a',
          kind: 'TextRegion',
          coords: makePolygon(),
          textLines: [makeTextLine('line-2')]
        }]
      }
    ]

    expect(collectTextlineIdsInReadingOrder(regions, undefined)).toEqual(['line-1', 'line-2'])
  })

  describe('textline sort order within a region', () => {
    function makeLineAt(id: string, top: number, extra: { index?: number, custom?: string } = {}) {
      return new TextLine({
        id,
        coords: new Polygon([[0, top], [100, top], [100, top + 10], [0, top + 10]]),
        ...extra
      })
    }

    function makeRegion(textLines: TextLine[]): Region[] {
      return [{ id: 'region-1', kind: 'TextRegion', coords: makePolygon(), textLines }]
    }

    it('orders textlines top to bottom by default', () => {
      const regions = makeRegion([
        makeLineAt('line-a', 50),
        makeLineAt('line-b', 10),
        makeLineAt('line-c', 30)
      ])

      expect(collectTextlineIdsInPageOrder(regions)).toEqual(['line-b', 'line-c', 'line-a'])
      expect(collectTextlineIdsInReadingOrder(regions, undefined)).toEqual(['line-b', 'line-c', 'line-a'])
    })

    it('orders textlines top to bottom in y-up world coordinates', () => {
      const makeWorldLine = (id: string, top: number) => new TextLine({
        id,
        coords: new Polygon([[-0.5, top], [0.5, top], [0.5, top - 0.1], [-0.5, top - 0.1]])
      })
      const regions = makeRegion([
        makeWorldLine('line-bottom', -0.5),
        makeWorldLine('line-top', 0.8),
        makeWorldLine('line-middle', 0.2)
      ])

      expect(collectTextlineIdsInPageOrder(regions)).toEqual(['line-top', 'line-middle', 'line-bottom'])
    })

    it('orders textlines by natural ID order when alphabetical', () => {
      const regions = makeRegion([
        makeLineAt('line_10', 10),
        makeLineAt('line_2', 20),
        makeLineAt('line_1', 30)
      ])

      expect(collectTextlineIdsInPageOrder(regions, 'alphabetical')).toEqual(['line_1', 'line_2', 'line_10'])
    })

    it('prefers explicit textline reading order over the fallback order', () => {
      const regions = makeRegion([
        makeLineAt('line-top', 10, { index: 2 }),
        makeLineAt('line-middle', 20, { custom: 'readingOrder {index:0;}' }),
        makeLineAt('line-bottom', 30, { index: 1 }),
        makeLineAt('line-unordered', 0)
      ])

      expect(collectTextlineIdsInPageOrder(regions)).toEqual([
        'line-middle',
        'line-bottom',
        'line-top',
        'line-unordered'
      ])
    })

    it('reads explicit order from @index before Transkribus custom attributes', () => {
      expect(getTextlineExplicitOrder({ index: 3, custom: 'readingOrder {index:7;}' })).toBe(3)
      expect(getTextlineExplicitOrder({ custom: 'structure {type:x;} readingOrder {index:7;}' })).toBe(7)
      expect(getTextlineExplicitOrder({ custom: 'structure {type:x;}' })).toBeUndefined()
      expect(getTextlineExplicitOrder({})).toBeUndefined()
    })
  })
})
