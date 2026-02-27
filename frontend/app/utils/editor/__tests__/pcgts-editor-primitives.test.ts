import { describe, expect, it } from 'vitest'
import { Metadata, Page, PcGts, Polygon, Polyline, TextContentVariant, TextLine, type TextRegion } from '@/models/editor'
import {
  baselineIdForTextLineId,
  collectRenderablePolygonsFromPcGts,
  collectRenderablePolylinesFromPcGts
} from '../pcgts-editor-primitives'

function createPcGtsFixture(): PcGts {
  const textLine = new TextLine({
    id: 'tl-1',
    coords: new Polygon([[0, 0], [1, 0], [1, 0.1], [0, 0.1]]),
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
    expect(region?.textContentVariants?.[0]?.confidence).toBe(0.8)
    expect(textline?.confidence).toBe(0.41)
    expect(textline?.textContentVariants?.length).toBe(2)
  })

  it('collects baseline confidence into renderable polylines', () => {
    const polylines = collectRenderablePolylinesFromPcGts(createPcGtsFixture())

    const baselineId = baselineIdForTextLineId('tl-1')
    const baseline = polylines.find(p => p.id === baselineId)

    expect(baseline?.confidence).toBe(0.22)
  })
})
