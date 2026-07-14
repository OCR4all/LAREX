import { describe, expect, it, vi } from 'vitest'
import { PolygonType } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { BACKGROUND_ELEMENT, RENDER_ALPHA, RENDER_COLORS } from '@/utils/editor/editor-constants'
import { drawPolygonOutlineWithStyle } from '@/webgl/editor/polygon-outline-dispatch'
import { resolvePolygonRenderStyle } from '@/webgl/editor/polygon-style-resolver'

function makePolygon(overrides: Partial<RenderablePolygon> = {}): RenderablePolygon {
  return {
    id: 'polygon-1',
    type: PolygonType.REGION,
    label: 'TextRegion',
    regionKind: 'TextRegion',
    points: [
      { x: 0, y: 0 },
      { x: 1, y: 0 },
      { x: 1, y: 1 },
      { x: 0, y: 1 }
    ],
    ...overrides
  }
}

describe('resolvePolygonRenderStyle', () => {
  it('keeps text regions solid and region-kind derived', () => {
    const style = resolvePolygonRenderStyle(makePolygon({
      label: 'Heading',
      regionKind: 'TextRegion',
      regionSubtype: 'heading'
    }), {
      showPersistentFill: true
    })

    expect(style.strokePattern).toBe('solid')
    expect(style.strokeColor).toEqual([25 / 255, 118 / 255, 210 / 255, 1])
    expect(style.persistentFill).toEqual([25 / 255, 118 / 255, 210 / 255, RENDER_ALPHA.FILL_LABEL_BACKGROUND])
  })

  it('renders textlines with semantic solid blue styling regardless of label text', () => {
    const style = resolvePolygonRenderStyle(makePolygon({
      id: 'line-17',
      label: 'line-17',
      type: PolygonType.TEXTLINE
    }), {
      showPersistentFill: true
    })

    expect(style.strokePattern).toBe('solid')
    expect(style.strokeWidthMultiplier).toBe(0.8)
    expect(style.strokeColor).toEqual(RENDER_COLORS.SELECTED_BLUE)
    expect(style.persistentFill).toBeNull()
    expect(style.nodeColor).toEqual(RENDER_COLORS.SELECTED_BLUE)
  })

  it('overrides semantic styling with the invalid red treatment', () => {
    const style = resolvePolygonRenderStyle(makePolygon({
      id: 'line-invalid',
      label: 'line-invalid',
      type: PolygonType.TEXTLINE
    }), {
      invalid: true,
      showPersistentFill: true
    })

    expect(style.strokePattern).toBe('solid')
    expect(style.strokeColor).toEqual(RENDER_COLORS.INVALID_RED)
    expect(style.persistentFill).toBeNull()
    expect(style.nodeColor).toEqual(RENDER_COLORS.INVALID_RED)
  })

  it('renders unmatched region labels with a strong red conflict outline', () => {
    const style = resolvePolygonRenderStyle(makePolygon(), {
      labelConflict: true,
      showPersistentFill: true
    })

    expect(style.strokeColor).toEqual(RENDER_COLORS.LABEL_CONFLICT_RED)
    expect(style.strokePattern).toBe('solid')
    expect(style.strokeWidthMultiplier).toBe(1.6)
    expect(style.persistentFill).toBeNull()
    expect(style.nodeColor).toEqual(RENDER_COLORS.LABEL_CONFLICT_RED)
  })

  it('does not apply region label conflict styling to textlines', () => {
    const style = resolvePolygonRenderStyle(makePolygon({
      id: 'line-conflict',
      type: PolygonType.TEXTLINE
    }), {
      labelConflict: true
    })

    expect(style.strokeColor).toEqual(RENDER_COLORS.SELECTED_BLUE)
  })

  it('drops persistent fill for textlines while keeping it for regions', () => {
    const regionStyle = resolvePolygonRenderStyle(makePolygon(), {
      showPersistentFill: true
    })
    const textlineStyle = resolvePolygonRenderStyle(makePolygon({
      id: 'line-1',
      label: 'line-1',
      type: PolygonType.TEXTLINE
    }), {
      showPersistentFill: true
    })

    expect(regionStyle.persistentFill).not.toBeNull()
    expect(textlineStyle.persistentFill).toBeNull()
  })
})

describe('drawPolygonOutlineWithStyle', () => {
  it('dispatches textlines to the solid renderer in normal rendering', () => {
    const dashedLineRenderer = { drawDashedLine: vi.fn() }
    const thickLineRenderer = { drawThickLine: vi.fn() }
    const style = resolvePolygonRenderStyle(makePolygon({
      id: 'line-2',
      label: 'line-2',
      type: PolygonType.TEXTLINE
    }))

    drawPolygonOutlineWithStyle({
      points: makePolygon().points,
      style,
      baseLineWidth: 5,
      isClosed: true,
      aspectRatioScale: { scaleX: 1, scaleY: 1 },
      view: { zoom: 1, offsetX: 0, offsetY: 0 },
      dashedLineRenderer,
      thickLineRenderer
    })

    expect(thickLineRenderer.drawThickLine).toHaveBeenCalledOnce()
    expect(dashedLineRenderer.drawDashedLine).not.toHaveBeenCalled()
  })

  it('dispatches regions to the solid renderer', () => {
    const dashedLineRenderer = { drawDashedLine: vi.fn() }
    const thickLineRenderer = { drawThickLine: vi.fn() }
    const style = resolvePolygonRenderStyle(makePolygon())

    drawPolygonOutlineWithStyle({
      points: makePolygon().points,
      style,
      baseLineWidth: 5,
      isClosed: true,
      aspectRatioScale: { scaleX: 1, scaleY: 1 },
      view: { zoom: 1, offsetX: 0, offsetY: 0 },
      dashedLineRenderer,
      thickLineRenderer
    })

    expect(thickLineRenderer.drawThickLine).toHaveBeenCalledOnce()
    expect(dashedLineRenderer.drawDashedLine).not.toHaveBeenCalled()
  })

  it('keeps baseline-mode background textlines dashed with semantic styling', () => {
    const dashedLineRenderer = { drawDashedLine: vi.fn() }
    const thickLineRenderer = { drawThickLine: vi.fn() }
    const style = resolvePolygonRenderStyle(makePolygon({
      id: 'line-bg',
      label: 'line-bg',
      type: PolygonType.TEXTLINE
    }), {
      renderPhase: 'background'
    })

    drawPolygonOutlineWithStyle({
      points: makePolygon().points,
      style,
      baseLineWidth: 5,
      isClosed: true,
      aspectRatioScale: { scaleX: 1, scaleY: 1 },
      view: { zoom: 1, offsetX: 0, offsetY: 0 },
      dashedLineRenderer,
      thickLineRenderer
    })

    expect(dashedLineRenderer.drawDashedLine).toHaveBeenCalledOnce()
    expect(dashedLineRenderer.drawDashedLine.mock.calls[0]?.[1]).toEqual([
      RENDER_COLORS.SELECTED_BLUE[0],
      RENDER_COLORS.SELECTED_BLUE[1],
      RENDER_COLORS.SELECTED_BLUE[2],
      BACKGROUND_ELEMENT.LINE_ALPHA
    ])
    expect(thickLineRenderer.drawThickLine).not.toHaveBeenCalled()
  })
})
