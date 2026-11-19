import { describe, expect, it } from 'vitest'
import { Metadata, Page, PcGts, Polygon, Polyline, TextContentVariant, TextLine, type Region, type TextRegion } from '@/models/editor'
import {
  baselineIdForTextLineId,
  collectRenderablePolygonsFromPcGts,
  collectRenderablePolylinesFromPcGts
} from '../pcgts-editor-primitives'

function createPcGtsFixture(): PcGts {
  const textLine = new TextLine({
    id: 'tl-1',
    coords: new Polygon([[0, 0], [1, 0], [1, 0.1], [0, 0.1]]),
    comments: 'line comment',
    baseline: {
      points: new Polyline([[0, 0.05], [1, 0.05]]),
      conf: 0.22
    },
    confidence: 0.41,
    textContentVariants: [
      new TextContentVariant('abc', undefined, 0.9, 0),
      new TextContentVariant('abc2', undefined, 0.5, 1)
    ]
  })

  const textRegion: TextRegion = {
    id: 'r-1',
    kind: 'TextRegion',
    coords: new Polygon([[0, 0], [1, 0], [1, 1], [0, 1]]),
    comments: 'region comment',
    confidence: 0.73,
    type: 'paragraph',
    textContentVariants: [new TextContentVariant('region', undefined, 0.8, 0)],
    textLines: [textLine],
    regions: []
  }

  return new PcGts(
    new Metadata({ creator: 'test' }),
    new Page({
      imageFilename: 'image.png',
      imageWidth: 1000,
      imageHeight: 1000,
      regions: [textRegion]
    })
  )
}

describe('pcgts-editor-primitives confidence extraction', () => {
  it('collects region and textline confidence into renderable polygons', () => {
    const polygons = collectRenderablePolygonsFromPcGts(createPcGtsFixture())

    const region = polygons.find(p => p.id === 'r-1')
    const textline = polygons.find(p => p.id === 'tl-1')

    expect(region?.confidence).toBe(0.73)
    expect(region?.comments).toBe('region comment')
    expect(region?.textContentVariants?.[0]?.confidence).toBe(0.8)
    expect(textline?.confidence).toBe(0.41)
    expect(textline?.comments).toBe('line comment')
    expect(textline?.textContentVariants?.length).toBe(2)
  })

  it('collects baseline confidence into renderable polylines', () => {
    const polylines = collectRenderablePolylinesFromPcGts(createPcGtsFixture())

    const baselineId = baselineIdForTextLineId('tl-1')
    const baseline = polylines.find(p => p.id === baselineId)

    expect(baseline?.confidence).toBe(0.22)
  })
})

describe('collecting table regions', () => {
  function tablePcGts(): PcGts {
    const cell = (id: string, rowIndex: number, columnIndex: number, header?: boolean): TextRegion => ({
      id,
      kind: 'TextRegion',
      coords: new Polygon([[0, 0], [1, 0], [1, 1]]),
      roles: { tableCellRole: { rowIndex, columnIndex, header } },
      regions: []
    })
    const table = {
      id: 't1',
      kind: 'TableRegion',
      coords: new Polygon([[0, 0], [20, 0], [20, 20], [0, 20]]),
      grid: {
        rows: [
          { index: 0, points: new Polygon([[0, 0], [10, 0], [20, 0]]) },
          { index: 1, points: new Polygon([[0, 10], [10, 10], [20, 10]]) },
          { index: 2, points: new Polygon([[0, 20], [10, 20], [20, 20]]) }
        ]
      },
      regions: [cell('h', 0, 0, true), cell('b', 1, 1)]
    } as Region
    return new PcGts(new Metadata({ creator: 'test' }), new Page({ imageFilename: 'image.png', imageWidth: 20, imageHeight: 20, regions: [table] }))
  }

  it('marks cells with their table position', () => {
    const polygons = collectRenderablePolygonsFromPcGts(tablePcGts())
    expect(polygons.find(p => p.id === 'h')?.tableCell).toEqual({ tableId: 't1', row: 0, col: 0, rowSpan: 1, colSpan: 1, header: true })
    expect(polygons.find(p => p.id === 'b')?.tableCell?.header).toBe(false)
  })

  it('gives tables their interior grid lines', () => {
    const table = collectRenderablePolygonsFromPcGts(tablePcGts()).find(p => p.id === 't1')
    expect(table?.tableCell).toBeUndefined()
    expect(table?.tableGridLines).toEqual([
      [{ x: 0, y: 10 }, { x: 10, y: 10 }, { x: 20, y: 10 }],
      [{ x: 10, y: 0 }, { x: 10, y: 10 }, { x: 10, y: 20 }]
    ])
  })
})
