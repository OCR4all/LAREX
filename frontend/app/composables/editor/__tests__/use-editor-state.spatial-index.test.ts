import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createSpatialIndex } from '@/services/editor/spatial-index-service'
import { useEditorState } from '../use-editor-state'

describe('useEditorState spatial index synchronization', () => {
  it('updates only changed polygons while preserving array indices', async () => {
    const spatialIndex = createSpatialIndex()
    const rebuildSpy = vi.spyOn(spatialIndex, 'rebuildPolygonIndex')
    const { polygons } = useEditorState(spatialIndex)

    polygons.push(
      {
        id: 'first',
        points: [
          { x: 0, y: 0 },
          { x: 1, y: 0 },
          { x: 1, y: 1 },
          { x: 0, y: 1 }
        ]
      },
      {
        id: 'second',
        points: [
          { x: 10, y: 10 },
          { x: 11, y: 10 },
          { x: 11, y: 11 },
          { x: 10, y: 11 }
        ]
      }
    )
    await nextTick()

    expect(rebuildSpy).not.toHaveBeenCalled()
    expect(spatialIndex.queryPolygonsAtPoint({ x: 0.5, y: 0.5 })).toEqual([0])
    expect(spatialIndex.queryPolygonsAtPoint({ x: 10.5, y: 10.5 })).toEqual([1])

    polygons[0]!.points = polygons[0]!.points.map(point => ({
      x: point.x + 20,
      y: point.y + 20
    }))
    await nextTick()

    expect(spatialIndex.queryPolygonsAtPoint({ x: 0.5, y: 0.5 })).toEqual([])
    expect(spatialIndex.queryPolygonsAtPoint({ x: 20.5, y: 20.5 })).toEqual([0])

    polygons.reverse()
    await nextTick()

    expect(spatialIndex.queryPolygonsAtPoint({ x: 10.5, y: 10.5 })).toEqual([0])
    expect(spatialIndex.queryPolygonsAtPoint({ x: 20.5, y: 20.5 })).toEqual([1])
    expect(rebuildSpy).not.toHaveBeenCalled()
  })

  it('updates changed polylines incrementally', async () => {
    const spatialIndex = createSpatialIndex()
    const rebuildSpy = vi.spyOn(spatialIndex, 'rebuildPolylineIndex')
    const { polylines } = useEditorState(spatialIndex)

    polylines.push({
      id: 'baseline:first',
      points: [
        { x: 0, y: 0 },
        { x: 1, y: 0 }
      ]
    })
    await nextTick()

    expect(spatialIndex.queryPolylinesAtPoint({ x: 0.5, y: 0 })).toEqual([0])

    polylines[0]!.points[0] = { x: 10, y: 10 }
    polylines[0]!.points[1] = { x: 11, y: 10 }
    await nextTick()

    expect(spatialIndex.queryPolylinesAtPoint({ x: 0.5, y: 0 })).toEqual([])
    expect(spatialIndex.queryPolylinesAtPoint({ x: 10.5, y: 10 })).toEqual([0])
    expect(rebuildSpy).not.toHaveBeenCalled()
  })

  it('repopulates the index after a full editor-state reset', async () => {
    const spatialIndex = createSpatialIndex()
    const { actions, polygons } = useEditorState(spatialIndex)
    const polygon = {
      id: 'same-id',
      points: [
        { x: 0, y: 0 },
        { x: 1, y: 0 },
        { x: 1, y: 1 },
        { x: 0, y: 1 }
      ]
    }

    polygons.push(polygon)
    await nextTick()
    expect(spatialIndex.queryPolygonsAtPoint({ x: 0.5, y: 0.5 })).toEqual([0])

    actions.resetAll()
    polygons.push({
      id: polygon.id,
      points: polygon.points.map(point => ({ ...point }))
    })
    await nextTick()

    expect(spatialIndex.queryPolygonsAtPoint({ x: 0.5, y: 0.5 })).toEqual([0])
  })

  it('keeps polygon selection attached to its stable ID across reordering and clears it on deletion', async () => {
    const spatialIndex = createSpatialIndex()
    const { actions, polygons, selectedPolygonIndex, selectedPolygonIds } = useEditorState(spatialIndex)
    polygons.push(
      {
        id: 'first',
        points: [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }]
      },
      {
        id: 'selected',
        points: [{ x: 10, y: 10 }, { x: 11, y: 10 }, { x: 11, y: 11 }]
      }
    )
    await nextTick()
    actions.selectPolygonById('selected')

    polygons.reverse()
    await nextTick()

    expect(selectedPolygonIndex.value).toBe(0)
    expect(selectedPolygonIds.value).toEqual(['selected'])

    polygons.splice(0, 1)
    await nextTick()

    expect(selectedPolygonIndex.value).toBe(-1)
    expect(selectedPolygonIds.value).toEqual([])
  })

  it('keeps baseline selection attached to its stable ID across reordering and clears it on deletion', async () => {
    const spatialIndex = createSpatialIndex()
    const { actions, polylines, selectedPolylineIndex, selectedPolylineIds } = useEditorState(spatialIndex)
    polylines.push(
      {
        id: 'baseline:first',
        points: [{ x: 0, y: 0 }, { x: 1, y: 0 }]
      },
      {
        id: 'baseline:selected',
        points: [{ x: 10, y: 10 }, { x: 11, y: 10 }]
      }
    )
    await nextTick()
    actions.selectPolylineById('baseline:selected')

    polylines.reverse()
    await nextTick()

    expect(selectedPolylineIndex.value).toBe(0)
    expect(selectedPolylineIds.value).toEqual(['baseline:selected'])

    polylines.splice(0, 1)
    await nextTick()

    expect(selectedPolylineIndex.value).toBe(-1)
    expect(selectedPolylineIds.value).toEqual([])
  })
})
