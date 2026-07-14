import { PolygonType, type PcGts } from '@/models/editor'
import type { LabelSet } from '@/models/editor/labels'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { BACKGROUND_ELEMENT, RENDER_ALPHA, RENDER_COLORS, type RGBA } from '@/utils/editor/editor-constants'
import { getStrokeColorForLabel } from '@/utils/editor/label-utils'

export type PolygonStrokePattern = 'solid' | 'dashed'
export type PolygonRenderPhase = 'default' | 'background'

export interface ResolvedPolygonRenderStyle {
  strokeColor: RGBA
  strokePattern: PolygonStrokePattern
  strokeWidthMultiplier: number
  persistentFill: RGBA | null
  nodeColor: RGBA
}

export interface ResolvePolygonRenderStyleOptions {
  document?: PcGts | null
  labelSet?: LabelSet | null
  showPersistentFill?: boolean
  invalid?: boolean
  labelConflict?: boolean
  renderPhase?: PolygonRenderPhase
}

const TEXTLINE_STROKE_COLOR: RGBA = RENDER_COLORS.SELECTED_BLUE

export function withAlpha(color: RGBA, alpha: number): RGBA {
  return [color[0], color[1], color[2], alpha]
}

function getRegionStrokeColor(
  polygon: RenderablePolygon,
  document?: PcGts | null,
  labelSet?: LabelSet | null
): RGBA {
  return getStrokeColorForLabel(
    polygon.label,
    document,
    labelSet,
    polygon.regionKind,
    polygon.regionSubtype,
    polygon.regionCustom
  )
}

export function resolvePolygonRenderStyle(
  polygon: RenderablePolygon,
  options: ResolvePolygonRenderStyleOptions = {}
): ResolvedPolygonRenderStyle {
  if (options.invalid) {
    return {
      strokeColor: RENDER_COLORS.INVALID_RED,
      strokePattern: 'solid',
      strokeWidthMultiplier: 1,
      persistentFill: null,
      nodeColor: RENDER_COLORS.INVALID_RED
    }
  }

  if (options.labelConflict && polygon.type === PolygonType.REGION) {
    return {
      strokeColor: RENDER_COLORS.LABEL_CONFLICT_RED,
      strokePattern: 'solid',
      strokeWidthMultiplier: 1.6,
      persistentFill: null,
      nodeColor: RENDER_COLORS.LABEL_CONFLICT_RED
    }
  }

  const renderPhase = options.renderPhase ?? 'default'
  const isTextline = polygon.type === PolygonType.TEXTLINE
  const baseStrokeColor = isTextline
    ? TEXTLINE_STROKE_COLOR
    : getRegionStrokeColor(polygon, options.document, options.labelSet)
  const persistentFill = !isTextline && options.showPersistentFill
    ? withAlpha(baseStrokeColor, RENDER_ALPHA.FILL_LABEL_BACKGROUND)
    : null

  if (renderPhase === 'background') {
    return {
      strokeColor: withAlpha(baseStrokeColor, BACKGROUND_ELEMENT.LINE_ALPHA),
      strokePattern: 'dashed',
      strokeWidthMultiplier: 0.7,
      persistentFill: null,
      nodeColor: baseStrokeColor
    }
  }

  return {
    strokeColor: baseStrokeColor,
    strokePattern: 'solid',
    strokeWidthMultiplier: isTextline ? 0.8 : 1,
    persistentFill,
    nodeColor: baseStrokeColor
  }
}
