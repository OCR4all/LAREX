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
  regionColorCache?: Map<string, RGBA>
}

/** Fixed stroke colors for text elements and page frames; regions are colored by their label instead. */
const TEXT_ELEMENT_STROKE_COLORS: Partial<Record<string, RGBA>> = {
  [PolygonType.TEXTLINE]: RENDER_COLORS.SELECTED_BLUE,
  [PolygonType.WORD]: RENDER_COLORS.WORD_TEAL,
  [PolygonType.GLYPH]: RENDER_COLORS.GLYPH_ORANGE,
  [PolygonType.BORDER]: RENDER_COLORS.BORDER_CYAN,
  [PolygonType.PRINT_SPACE]: RENDER_COLORS.PRINT_SPACE_GREEN
}

export function withAlpha(color: RGBA, alpha: number): RGBA {
  return [color[0], color[1], color[2], alpha]
}

function getRegionStrokeColor(
  polygon: RenderablePolygon,
  document?: PcGts | null,
  labelSet?: LabelSet | null,
  cache?: Map<string, RGBA>
): RGBA {
  if (!cache) return lookupRegionStrokeColor(polygon, document, labelSet)
  const key = `${polygon.label}\u0000${polygon.regionKind}\u0000${polygon.regionSubtype}\u0000${polygon.regionCustom}`
  let color = cache.get(key)
  if (!color) {
    color = lookupRegionStrokeColor(polygon, document, labelSet)
    cache.set(key, color)
  }
  return color
}

function lookupRegionStrokeColor(
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

function strokeWidthMultiplier(polygon: RenderablePolygon, isTextElement: boolean): number {
  if (isTextElement) return 0.8
  if (polygon.regionKind === 'TableRegion') return 1.5
  if (polygon.tableCell) return 0.75
  return 1
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
  const textElementStrokeColor = polygon.type ? TEXT_ELEMENT_STROKE_COLORS[polygon.type] : undefined
  const isTextElement = !!textElementStrokeColor
  const baseStrokeColor = textElementStrokeColor
    ?? getRegionStrokeColor(polygon, options.document, options.labelSet, options.regionColorCache)
  const persistentFill = !isTextElement && options.showPersistentFill
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
    strokeWidthMultiplier: strokeWidthMultiplier(polygon, isTextElement),
    persistentFill,
    nodeColor: baseStrokeColor
  }
}
