import type { AspectRatioScale, Point, View } from '@/models/editor'
import { BACKGROUND_ELEMENT, type RGBA } from '@/utils/editor/editor-constants'
import type { PolygonStrokePattern, ResolvedPolygonRenderStyle } from '@/webgl/editor/polygon-style-resolver'

export interface PolygonOutlineRenderers {
  thickLineRenderer?: {
    drawThickLine: (
      points: Point[],
      color: readonly number[],
      thickness: number,
      isClosed: boolean,
      aspectRatioScale: AspectRatioScale,
      view: View
    ) => void
  } | null
  dashedLineRenderer?: {
    drawDashedLine: (
      points: Point[],
      color: readonly number[],
      thickness: number,
      isClosed: boolean,
      dashLength: number,
      gapLength: number,
      aspectRatioScale: AspectRatioScale,
      view: View
    ) => void
  } | null
}

export interface DrawPolygonOutlineOptions extends PolygonOutlineRenderers {
  points: Point[]
  style: ResolvedPolygonRenderStyle
  baseLineWidth: number
  isClosed: boolean
  aspectRatioScale: AspectRatioScale
  view: View
  dashLength?: number
  gapLength?: number
}

export function getOutlineRendererKind(style: Pick<ResolvedPolygonRenderStyle, 'strokePattern'>): PolygonStrokePattern {
  return style.strokePattern
}

export function drawPolygonOutlineWithStyle(options: DrawPolygonOutlineOptions): void {
  const thickness = options.baseLineWidth * options.style.strokeWidthMultiplier
  const rendererKind = getOutlineRendererKind(options.style)

  if (rendererKind === 'dashed') {
    options.dashedLineRenderer?.drawDashedLine(
      options.points,
      options.style.strokeColor,
      thickness,
      options.isClosed,
      options.dashLength ?? BACKGROUND_ELEMENT.DASH_LENGTH,
      options.gapLength ?? BACKGROUND_ELEMENT.GAP_LENGTH,
      options.aspectRatioScale,
      options.view
    )
    return
  }

  options.thickLineRenderer?.drawThickLine(
    options.points,
    options.style.strokeColor as RGBA,
    thickness,
    options.isClosed,
    options.aspectRatioScale,
    options.view
  )
}
