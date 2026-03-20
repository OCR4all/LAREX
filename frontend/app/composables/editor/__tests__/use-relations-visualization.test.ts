import { describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import { useRelationsVisualization } from '../use-relations-visualization'
import type { Relation } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'

describe('useRelationsVisualization', () => {
  it('computes overlay segments only for relations with resolvable region endpoints', () => {
    const relations = ref<Relation[]>([
      {
        id: 'rel-a',
        type: 'link',
        sourceRegionRef: 'r1',
        targetRegionRef: 'r2'
      },
      {
        id: 'rel-missing',
        type: 'join',
        sourceRegionRef: 'r1',
        targetRegionRef: 'missing'
      }
    ])

    const polygons = computed<RenderablePolygon[]>(() => [
      {
        id: 'r1',
        type: 'region',
        points: [
          { x: 0, y: 0 },
          { x: 10, y: 0 },
          { x: 10, y: 10 },
          { x: 0, y: 10 }
        ]
      },
      {
        id: 'r2',
        type: 'region',
        points: [
          { x: 20, y: 20 },
          { x: 30, y: 20 },
          { x: 30, y: 30 },
          { x: 20, y: 30 }
        ]
      }
    ])

    const { renderData } = useRelationsVisualization(
      relations,
      polygons,
      ref<string | null>(null)
    )

    expect(renderData.value.segments).toHaveLength(1)
    expect(renderData.value.labels).toHaveLength(1)
    expect(renderData.value.labels[0]?.text).toBe('link • rel-a')
  })

  it('marks the selected relation label and keeps label data available independently of label visibility', () => {
    const relations = ref<Relation[]>([
      {
        id: 'rel-selected',
        type: 'join',
        sourceRegionRef: 'r1',
        targetRegionRef: 'r2'
      }
    ])

    const polygons = computed<RenderablePolygon[]>(() => [
      {
        id: 'r1',
        type: 'region',
        points: [
          { x: 0, y: 0 },
          { x: 10, y: 0 },
          { x: 10, y: 10 },
          { x: 0, y: 10 }
        ]
      },
      {
        id: 'r2',
        type: 'region',
        points: [
          { x: 20, y: 20 },
          { x: 30, y: 20 },
          { x: 30, y: 30 },
          { x: 20, y: 30 }
        ]
      }
    ])

    const { renderData } = useRelationsVisualization(
      relations,
      polygons,
      ref<string | null>('rel-selected')
    )

    expect(renderData.value.labels).toHaveLength(1)
    expect(renderData.value.labels[0]?.isSelected).toBe(true)
    expect(renderData.value.segments).toHaveLength(1)
  })
})
