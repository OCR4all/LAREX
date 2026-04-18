import { describe, expect, it } from 'vitest'
import type { Region } from '@/models/editor/region'
import { collectTextlineIdsInPageOrder, getAdjacentTextlineId } from '../textline-navigation'

function makePolygon() {
  return { points: [[0, 0], [1, 0], [1, 1], [0, 1]] } as any
}

describe('textline-navigation', () => {
  it('collects textline IDs in recursive PAGE traversal order', () => {
    const regions: Region[] = [
      {
        id: 'region-1',
        kind: 'TextRegion',
        coords: makePolygon(),
        textLines: [
          { id: 'line-1' } as any,
          { id: 'line-2' } as any
        ],
        regions: [
          {
            id: 'region-1a',
            kind: 'TextRegion',
            coords: makePolygon(),
            textLines: [{ id: 'line-3' } as any]
          } as any
        ]
      } as any,
      {
        id: 'region-2',
        kind: 'ImageRegion',
        coords: makePolygon()
      } as any,
      {
        id: 'region-3',
        kind: 'TextRegion',
        coords: makePolygon(),
        textLines: [{ id: 'line-4' } as any]
      } as any
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
})

