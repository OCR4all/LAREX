import { computed, type Ref } from 'vue'
import type { Point, Relation } from '@/models/editor'
import type { RenderablePolygon, RelationOverlayLabel, RelationRenderData } from '@/types/editor/rendering'
import type { ArrowSegment } from '@/webgl/editor/reading-order-renderer'
import type { RelationDraftState, RelationPickerMode } from '@/utils/editor/relations'

const DEFAULT_RELATION_COLOR: [number, number, number, number] = [0.94, 0.48, 0.12, 0.78]
const SELECTED_RELATION_COLOR: [number, number, number, number] = [0.96, 0.74, 0.16, 1]
const DRAFT_RELATION_COLOR: [number, number, number, number] = [0.16, 0.52, 0.96, 0.88]

function getCentroid(points: Point[]): Point {
  if (points.length === 0) return { x: 0, y: 0 }

  let sumX = 0
  let sumY = 0

  for (const point of points) {
    sumX += point.x
    sumY += point.y
  }

  return {
    x: sumX / points.length,
    y: sumY / points.length
  }
}

function getMidpoint(from: Point, to: Point): Point {
  return {
    x: (from.x + to.x) / 2,
    y: (from.y + to.y) / 2
  }
}

function getRelationLabel(relation: Relation): string {
  const relationType = relation.type?.trim()
  const relationId = relation.id?.trim()

  if (relationType && relationId) return `${relationType} • ${relationId}`
  if (relationType) return relationType
  if (relationId) return relationId
  return 'Relation'
}

export function useRelationsVisualization(
  relations: Ref<Relation[] | undefined>,
  polygons: Ref<RenderablePolygon[]>,
  selectedRelationId: Ref<string | null>,
  draftRelation: Ref<RelationDraftState> = computed(() => ({
    id: '',
    type: 'link',
    sourceRegionRef: '',
    targetRegionRef: '',
    custom: '',
    comments: '',
    labels: []
  })),
  pickerMode: Ref<RelationPickerMode> = computed(() => 'idle')
) {
  const polygonMap = computed(() => {
    const map = new Map<string, RenderablePolygon>()
    for (const polygon of polygons.value) {
      map.set(polygon.id, polygon)
    }
    return map
  })

  const renderData = computed<RelationRenderData>(() => {
    const segments: ArrowSegment[] = []
    const labels: RelationOverlayLabel[] = []

    for (const relation of relations.value ?? []) {
      const sourceId = relation.sourceRegionRef?.trim()
      const targetId = relation.targetRegionRef?.trim()
      if (!sourceId || !targetId) continue

      const sourcePolygon = polygonMap.value.get(sourceId)
      const targetPolygon = polygonMap.value.get(targetId)
      if (!sourcePolygon || !targetPolygon) continue

      const from = getCentroid(sourcePolygon.points)
      const to = getCentroid(targetPolygon.points)
      const isSelected = Boolean(selectedRelationId.value && relation.id === selectedRelationId.value)

      segments.push({
        from,
        to,
        color: isSelected ? SELECTED_RELATION_COLOR : DEFAULT_RELATION_COLOR
      })

      labels.push({
        id: relation.id ?? `${sourceId}:${targetId}`,
        relationId: relation.id ?? '',
        sourceRegionRef: sourceId,
        targetRegionRef: targetId,
        sourcePosition: from,
        targetPosition: to,
        position: getMidpoint(from, to),
        text: getRelationLabel(relation),
        isSelected
      })
    }

    const draftSourceId = draftRelation.value.sourceRegionRef?.trim()
    const draftTargetId = draftRelation.value.targetRegionRef?.trim()
    const draftType = draftRelation.value.type?.trim() || 'link'

    if (draftSourceId) {
      const sourcePolygon = polygonMap.value.get(draftSourceId)
      if (sourcePolygon) {
        const sourcePosition = getCentroid(sourcePolygon.points)

        labels.push({
          id: 'draft-source',
          sourceRegionRef: draftSourceId,
          sourcePosition,
          position: sourcePosition,
          text: pickerMode.value === 'pick-target' ? 'Source picked' : 'Source',
          isSelected: true,
          isDraft: true
        })

        if (draftTargetId) {
          const targetPolygon = polygonMap.value.get(draftTargetId)
          if (targetPolygon) {
            const targetPosition = getCentroid(targetPolygon.points)
            segments.push({
              from: sourcePosition,
              to: targetPosition,
              color: DRAFT_RELATION_COLOR
            })

            labels.push({
              id: 'draft-relation',
              sourceRegionRef: draftSourceId,
              targetRegionRef: draftTargetId,
              sourcePosition,
              targetPosition,
              position: getMidpoint(sourcePosition, targetPosition),
              text: `Draft ${draftType}`,
              isSelected: true,
              isDraft: true
            })
          }
        }
      }
    }

    return {
      segments,
      labels
    }
  })

  return {
    renderData
  }
}
