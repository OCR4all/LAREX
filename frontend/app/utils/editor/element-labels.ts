import type { LabelSet } from '@/models/editor/labels'
import { PolygonType } from '@/models/editor'
import type { Point } from '@/models/editor'
import type { ElementOverlayLabel, RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import { findRegionLabelDefinitionForRegion } from '@/utils/editor/page-label-mapping'
import { getRegionColor } from '@/utils/editor/region-colors'

const DEFAULT_ELEMENT_LABEL_COLOR = '#475569'
const DEFAULT_LINE_LABEL_COLOR = '#3B82F6'

function getLineLabelColor(labelSet?: LabelSet | null): string {
  return labelSet?.labels.find(label => label.scope === 'line')?.color || DEFAULT_LINE_LABEL_COLOR
}

function averagePoint(points: Point[]): Point | null {
  if (points.length === 0) return null

  const total = points.reduce(
    (sum, point) => ({ x: sum.x + point.x, y: sum.y + point.y }),
    { x: 0, y: 0 }
  )

  return {
    x: total.x / points.length,
    y: total.y / points.length
  }
}

function normalizedLabelToken(value: string | undefined): string {
  return value?.trim().toLocaleLowerCase() ?? ''
}

/** Return the point halfway along a polyline, rather than halfway through its vertex list. */
function polylineMidpoint(points: Point[]): Point | null {
  if (points.length === 0) return null
  if (points.length === 1) return points[0] ?? null

  const lengths: number[] = []
  let totalLength = 0

  for (let index = 1; index < points.length; index++) {
    const from = points[index - 1]
    const to = points[index]
    if (!from || !to) continue
    const length = Math.hypot(to.x - from.x, to.y - from.y)
    lengths.push(length)
    totalLength += length
  }

  if (totalLength === 0) return points[0] ?? null

  const targetLength = totalLength / 2
  let traversed = 0
  for (let index = 0; index < lengths.length; index++) {
    const length = lengths[index] ?? 0
    if (traversed + length < targetLength) {
      traversed += length
      continue
    }

    const from = points[index]
    const to = points[index + 1]
    if (!from || !to || length === 0) return from ?? null
    const ratio = (targetLength - traversed) / length
    return {
      x: from.x + (to.x - from.x) * ratio,
      y: from.y + (to.y - from.y) * ratio
    }
  }

  return points.at(-1) ?? null
}

export function createPolygonElementLabel(
  polygon: RenderablePolygon,
  labelSet?: LabelSet | null
): ElementOverlayLabel | null {
  const position = averagePoint(polygon.points)
  if (!position) return null

  if (polygon.type === PolygonType.TEXTLINE) {
    return {
      id: polygon.id,
      position,
      label: polygon.label || polygon.id,
      elementType: 'TextLine',
      backgroundColor: getLineLabelColor(labelSet)
    }
  }

  const mappedLabel = findRegionLabelDefinitionForRegion(labelSet?.labels, polygon)
  const label = mappedLabel?.name || polygon.label || polygon.id
  const normalizedLabel = normalizedLabelToken(label)
  const exactTypes = [polygon.regionKind, polygon.regionSubtype]
    .filter((value): value is string => Boolean(value && normalizedLabelToken(value) !== normalizedLabel))

  return {
    id: polygon.id,
    position,
    label,
    elementType: exactTypes.join(' · ') || undefined,
    backgroundColor: mappedLabel?.color
      || (polygon.regionKind ? getRegionColor(polygon.regionKind, polygon.regionSubtype) : DEFAULT_ELEMENT_LABEL_COLOR)
  }
}

export function createPolylineElementLabel(
  polyline: RenderablePolyline,
  parentPolygon?: RenderablePolygon,
  labelSet?: LabelSet | null
): ElementOverlayLabel | null {
  const position = polylineMidpoint(polyline.points)
  if (!position) return null

  const isBaseline = polyline.type === PolygonType.BASELINE || polyline.type === 'baseline'
  const fallbackLabel = isBaseline ? parentPolygon?.label || parentPolygon?.id : undefined

  return {
    id: polyline.id,
    position,
    label: fallbackLabel || polyline.label || polyline.id,
    elementType: isBaseline ? 'Baseline' : 'Polyline',
    backgroundColor: getLineLabelColor(labelSet)
  }
}
