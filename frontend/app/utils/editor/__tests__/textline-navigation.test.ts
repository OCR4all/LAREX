import { describe, expect, it } from 'vitest'
import type { Region } from '@/models/editor/region'
import { Polygon } from '@/models/editor/geometry'
import { TextLine } from '@/models/editor/text'
import { collectRegionIdsInReadingOrder, collectTextlineIdsInPageOrder, getAdjacentTextlineId } from '../textline-navigation'

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
})
