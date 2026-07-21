import { describe, expect, it } from 'vitest'
import { LabelDefinition, LabelSet } from '@/models/editor/labels'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import { createPolygonElementLabel, createPolylineElementLabel } from '../element-labels'

const region: RenderablePolygon = {
  id: 'region-1',
  type: 'region',
  label: 'paragraph',
  regionKind: 'TextRegion',
  regionSubtype: 'paragraph',
  points: [
    { x: 0, y: 0 },
    { x: 4, y: 0 },
    { x: 4, y: 2 },
    { x: 0, y: 2 }
  ]
}

describe('element label helpers', () => {
  it('keeps the PAGE kind but omits a subtype equivalent to the mapped label', () => {
    const labelSet = new LabelSet('labels', 'Test', [
      new LabelDefinition(
        'body',
        'Paragraph',
        'region',
        '#000000',
        '',
        true,
        false,
        null,
        {
          pageXml: {
            regionType: 'TextRegion',
            textType: 'paragraph',
            customKey: 'structure'
          }
        }
      )
    ])

    expect(createPolygonElementLabel(region, labelSet)).toEqual({
      id: 'region-1',
      position: { x: 2, y: 1 },
      label: 'Paragraph',
      elementType: 'TextRegion'
    })
  })

  it('does not repeat a region label that is identical to its PAGE kind', () => {
    const graphicRegion: RenderablePolygon = {
      id: 'graphic-1',
      type: 'region',
      label: 'GraphicRegion',
      regionKind: 'GraphicRegion',
      points: region.points
    }

    expect(createPolygonElementLabel(graphicRegion)).toEqual({
      id: 'graphic-1',
      position: { x: 2, y: 1 },
      label: 'GraphicRegion',
      elementType: undefined
    })
  })

  it('identifies text lines and baselines and anchors a baseline halfway along its path', () => {
    const textline: RenderablePolygon = {
      id: 'line-1',
      type: 'textline',
      label: 'line-1',
      parentId: 'region-1',
      points: [
        { x: 0, y: 0 },
        { x: 4, y: 0 },
        { x: 4, y: 2 },
        { x: 0, y: 2 }
      ]
    }
    const baseline: RenderablePolyline = {
      id: 'baseline:line-1',
      type: 'baseline',
      label: 'baseline',
      parentId: 'line-1',
      points: [
        { x: 0, y: 1 },
        { x: 2, y: 1 },
        { x: 8, y: 1 }
      ]
    }

    expect(createPolygonElementLabel(textline)).toMatchObject({
      label: 'line-1',
      elementType: 'TextLine'
    })
    expect(createPolylineElementLabel(baseline, textline)).toEqual({
      id: 'baseline:line-1',
      position: { x: 4, y: 1 },
      label: 'line-1',
      elementType: 'Baseline'
    })
  })
})
