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

  it('draws relations whose endpoints are text lines, words or glyphs', () => {
    const square = (x: number) => [{ x, y: 0 }, { x: x + 10, y: 0 }, { x: x + 10, y: 10 }, { x, y: 10 }]
    const relations = ref<Relation[]>([
      { id: 'line-word', type: 'link', sourceRegionRef: 'l1', targetRegionRef: 'w1' },
      { id: 'glyph-region', type: 'join', sourceRegionRef: 'g1', targetRegionRef: 'r1' }
    ])
    const polygons = computed<RenderablePolygon[]>(() => [
      { id: 'r1', type: 'region', points: square(0) },
      { id: 'l1', type: 'textline', parentId: 'r1', points: square(20) },
      { id: 'w1', type: 'word', parentId: 'l1', points: square(40) },
      { id: 'g1', type: 'glyph', parentId: 'w1', points: square(60) }
    ])

    const { renderData } = useRelationsVisualization(relations, polygons, ref<string | null>(null))

    expect(renderData.value.labels.map(label => label.relationId)).toEqual(['line-word', 'glyph-region'])
    expect(renderData.value.segments[0]).toMatchObject({ from: { x: 25, y: 5 }, to: { x: 45, y: 5 } })
  })

  it('gives link and join labels distinct icons and leaves other types without one', () => {
    const square = (x: number) => [{ x, y: 0 }, { x: x + 10, y: 0 }, { x: x + 10, y: 10 }, { x, y: 10 }]
    const endpoints = { sourceRegionRef: 'r1', targetRegionRef: 'r2' }
    const relations = ref<Relation[]>([
      { id: 'a', type: 'link', ...endpoints },
      { id: 'b', type: 'join', ...endpoints },
      { id: 'c', type: 'custom', ...endpoints },
      { id: 'd', ...endpoints }
    ])
    const polygons = computed<RenderablePolygon[]>(() => [
      { id: 'r1', type: 'region', points: square(0) },
      { id: 'r2', type: 'region', points: square(20) }
    ])

    const { renderData } = useRelationsVisualization(
      relations,
      polygons,
      ref<string | null>(null),
      ref({ id: '', type: 'join', ...endpoints, labels: [] })
    )

    expect(renderData.value.labels.map(label => [label.id, label.icon])).toEqual([
      ['a', 'i-lucide-link-2'],
      ['b', 'i-lucide-puzzle'],
      ['c', undefined],
      ['d', undefined],
      ['draft-source', undefined],
      ['draft-relation', 'i-lucide-puzzle']
    ])
  })
})
