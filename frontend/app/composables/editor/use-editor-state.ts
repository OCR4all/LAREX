import type { SpatialIndexService } from '@/services/editor/spatial-index-service'
import { reactive, ref, watch } from 'vue'

export interface EditorPolygon {
  id: string
  points: Array<{ x: number, y: number }>
  type?: string
  parentId?: string
  label?: string
  comments?: string
}

export interface EditorPolyline {
  id: string
  points: Array<{ x: number, y: number }>
  type?: string
  parentPolygonId?: string
  parentId?: string
  label?: string
  comments?: string
}

export interface EditorState {
  polygons: EditorPolygon[]
  polylines: EditorPolyline[]
  spatialIndex: SpatialIndexService

  selectedPolygonIndex: Ref<number>
  selectedPolylineIndex: Ref<number>
  selectedPolygonIds: Ref<string[]>
  selectedPolylineIds: Ref<string[]>

  hoveredPolygonId: Ref<string | null>
  hoveredPolylineId: Ref<string | null>

  canvasDimensions: Ref<{ width: number, height: number }>
}

export interface EditorStateActions {
  setSelectedPolygonIndex: (index: number) => void
  setSelectedPolylineIndex: (index: number) => void
  selectPolygonById: (id: string) => number
  selectPolylineById: (id: string) => number
  clearSelectionSet: () => void
  replacePolygonSelection: (polygonIds: string[]) => void
  replacePolylineSelection: (polylineIds: string[]) => void
  addPolygonSelection: (polygonIds: string[]) => void
  addPolylineSelection: (polylineIds: string[]) => void
  togglePolygonSelection: (polygonId: string) => void
  togglePolylineSelection: (polylineId: string) => void
  clearSelection: () => void

  setHoveredPolygonId: (id: string | null) => void
  setHoveredPolylineId: (id: string | null) => void

  updateCanvasDimensions: (width: number, height: number) => void

  clearHoverAndSelectionStates: () => void
  resetAll: () => void
}

interface SpatialShapeSnapshot {
  index: number
  coordinates: number[]
}

function snapshotCoordinates(points: Array<{ x: number, y: number }>): number[] {
  const coordinates = new Array<number>(points.length * 2)
  for (let index = 0; index < points.length; index++) {
    const point = points[index]
    if (!point) continue
    coordinates[index * 2] = point.x
    coordinates[index * 2 + 1] = point.y
  }
  return coordinates
}

function coordinatesChanged(
  snapshot: SpatialShapeSnapshot,
  points: Array<{ x: number, y: number }>
): boolean {
  if (snapshot.coordinates.length !== points.length * 2) return true
  for (let index = 0; index < points.length; index++) {
    const point = points[index]
    if (!point) return true
    if (
      snapshot.coordinates[index * 2] !== point.x
      || snapshot.coordinates[index * 2 + 1] !== point.y
    ) {
      return true
    }
  }
  return false
}

/**
 * Composable for managing all editor state including shapes, selections, and spatial indexing.
 * This centralizes state management and provides a clean API for state updates.
 */
export function useEditorState(spatialIndex: SpatialIndexService) {
  const polygons = reactive<EditorPolygon[]>([])
  const polylines = reactive<EditorPolyline[]>([])

  const selectedPolygonIndex = ref<number>(-1)
  const selectedPolylineIndex = ref<number>(-1)
  const selectedPolygonIds = ref<string[]>([])
  const selectedPolylineIds = ref<string[]>([])

  const hoveredPolygonId = ref<string | null>(null)
  const hoveredPolylineId = ref<string | null>(null)

  const canvasDimensions = ref({ width: 0, height: 0 })

  let polygonIndexSnapshots = new Map<string, SpatialShapeSnapshot>()
  let polylineIndexSnapshots = new Map<string, SpatialShapeSnapshot>()

  watch(polygons, () => {
    const nextSnapshots = new Map<string, SpatialShapeSnapshot>()

    for (let index = 0; index < polygons.length; index++) {
      const polygon = polygons[index]
      if (!polygon) continue
      const previous = polygonIndexSnapshots.get(polygon.id)
      const changed = !previous || previous.index !== index || coordinatesChanged(previous, polygon.points)

      if (!previous) {
        if (spatialIndex.hasPolygon(polygon.id)) {
          spatialIndex.updatePolygon(polygon, index)
        } else {
          spatialIndex.insertPolygon(polygon, index)
        }
      } else if (changed) {
        spatialIndex.updatePolygon(polygon, index)
      }

      nextSnapshots.set(
        polygon.id,
        changed ? { index, coordinates: snapshotCoordinates(polygon.points) } : previous
      )
    }

    for (const polygonId of polygonIndexSnapshots.keys()) {
      if (!nextSnapshots.has(polygonId)) {
        spatialIndex.removePolygon(polygonId)
      }
    }

    polygonIndexSnapshots = nextSnapshots
  }, { deep: true })

  watch(polylines, () => {
    const nextSnapshots = new Map<string, SpatialShapeSnapshot>()

    for (let index = 0; index < polylines.length; index++) {
      const polyline = polylines[index]
      if (!polyline) continue
      const previous = polylineIndexSnapshots.get(polyline.id)
      const changed = !previous || previous.index !== index || coordinatesChanged(previous, polyline.points)

      if (!previous) {
        if (spatialIndex.hasPolyline(polyline.id)) {
          spatialIndex.updatePolyline(polyline, index)
        } else {
          spatialIndex.insertPolyline(polyline, index)
        }
      } else if (changed) {
        spatialIndex.updatePolyline(polyline, index)
      }

      nextSnapshots.set(
        polyline.id,
        changed ? { index, coordinates: snapshotCoordinates(polyline.points) } : previous
      )
    }

    for (const polylineId of polylineIndexSnapshots.keys()) {
      if (!nextSnapshots.has(polylineId)) {
        spatialIndex.removePolyline(polylineId)
      }
    }

    polylineIndexSnapshots = nextSnapshots
  }, { deep: true })

  watch(() => polygons.map(polygon => polygon.id), (ids, previousIds) => {
    const selectedId = selectedPolygonIds.value.length === 1
      ? selectedPolygonIds.value[0]
      : previousIds[selectedPolygonIndex.value]
    if (selectedId) {
      selectedPolygonIndex.value = ids.indexOf(selectedId)
    } else if (selectedPolygonIndex.value >= ids.length) {
      selectedPolygonIndex.value = -1
    }

    if (hoveredPolygonId.value && !polygons.some(p => p.id === hoveredPolygonId.value)) {
      hoveredPolygonId.value = null
    }

    if (selectedPolygonIds.value.length > 0) {
      const existing = new Set(ids)
      selectedPolygonIds.value = selectedPolygonIds.value.filter(id => existing.has(id))
    }
  })

  watch(() => polylines.map(polyline => polyline.id), (ids, previousIds) => {
    const selectedId = selectedPolylineIds.value.length === 1
      ? selectedPolylineIds.value[0]
      : previousIds[selectedPolylineIndex.value]
    if (selectedId) {
      selectedPolylineIndex.value = ids.indexOf(selectedId)
    } else if (selectedPolylineIndex.value >= ids.length) {
      selectedPolylineIndex.value = -1
    }

    if (hoveredPolylineId.value && !polylines.some(p => p.id === hoveredPolylineId.value)) {
      hoveredPolylineId.value = null
    }

    if (selectedPolylineIds.value.length > 0) {
      const existing = new Set(ids)
      selectedPolylineIds.value = selectedPolylineIds.value.filter(id => existing.has(id))
    }
  })

  function setSelectedPolygonIndex(index: number): void {
    selectedPolygonIndex.value = index
  }

  function setSelectedPolylineIndex(index: number): void {
    selectedPolylineIndex.value = index
  }

  function selectPolygonById(id: string): number {
    const index = polygons.findIndex(p => p.id === id)
    selectedPolygonIndex.value = index >= 0 ? index : -1
    selectedPolylineIndex.value = -1
    selectedPolygonIds.value = index >= 0 ? [id] : []
    selectedPolylineIds.value = []
    return selectedPolygonIndex.value
  }

  function selectPolylineById(id: string): number {
    const index = polylines.findIndex(p => p.id === id)
    selectedPolylineIndex.value = index >= 0 ? index : -1
    selectedPolygonIndex.value = -1
    selectedPolylineIds.value = index >= 0 ? [id] : []
    selectedPolygonIds.value = []
    return selectedPolylineIndex.value
  }

  function unique(ids: string[]): string[] {
    return Array.from(new Set(ids.filter(Boolean)))
  }

  function normalizePolygonIds(ids: string[]): string[] {
    const selected = new Set(unique(ids))
    if (selected.size <= 1) return Array.from(selected)

    const parentById = new Map<string, string | undefined>()
    for (const poly of polygons) {
      parentById.set(poly.id, poly.parentId)
    }

    const toRemove = new Set<string>()
    for (const id of selected) {
      const visited = new Set<string>()
      let parent = parentById.get(id)
      while (parent) {
        if (visited.has(parent)) break
        visited.add(parent)
        if (selected.has(parent)) {
          toRemove.add(parent)
        }
        parent = parentById.get(parent)
      }
    }

    for (const id of toRemove) {
      selected.delete(id)
    }
    return Array.from(selected)
  }

  function clearSelectionSet(): void {
    selectedPolygonIds.value = []
    selectedPolylineIds.value = []
  }

  function replacePolygonSelection(polygonIds: string[]): void {
    selectedPolylineIds.value = []
    selectedPolygonIds.value = normalizePolygonIds(polygonIds)
  }

  function replacePolylineSelection(polylineIds: string[]): void {
    selectedPolygonIds.value = []
    selectedPolylineIds.value = unique(polylineIds)
  }

  function addPolygonSelection(polygonIds: string[]): void {
    selectedPolylineIds.value = []
    selectedPolygonIds.value = normalizePolygonIds([...selectedPolygonIds.value, ...polygonIds])
  }

  function addPolylineSelection(polylineIds: string[]): void {
    selectedPolygonIds.value = []
    selectedPolylineIds.value = unique([...selectedPolylineIds.value, ...polylineIds])
  }

  function togglePolygonSelection(polygonId: string): void {
    selectedPolylineIds.value = []
    const set = new Set(selectedPolygonIds.value)
    if (set.has(polygonId)) {
      set.delete(polygonId)
    } else {
      set.add(polygonId)
    }
    selectedPolygonIds.value = normalizePolygonIds(Array.from(set))
  }

  function togglePolylineSelection(polylineId: string): void {
    selectedPolygonIds.value = []
    const set = new Set(selectedPolylineIds.value)
    if (set.has(polylineId)) {
      set.delete(polylineId)
    } else {
      set.add(polylineId)
    }
    selectedPolylineIds.value = Array.from(set)
  }

  function clearSelection(): void {
    selectedPolygonIndex.value = -1
    selectedPolylineIndex.value = -1
    clearSelectionSet()
  }

  function setHoveredPolygonId(id: string | null): void {
    hoveredPolygonId.value = id
  }

  function setHoveredPolylineId(id: string | null): void {
    hoveredPolylineId.value = id
  }

  function updateCanvasDimensions(width: number, height: number): void {
    canvasDimensions.value = { width, height }
  }

  function clearHoverAndSelectionStates(): void {
    if (selectedPolygonIndex.value >= 0 && !polygons[selectedPolygonIndex.value]) {
      selectedPolygonIndex.value = -1
    }

    hoveredPolygonId.value = null
    hoveredPolylineId.value = null
  }

  function resetAll(): void {
    polygons.length = 0
    polylines.length = 0
    polygonIndexSnapshots = new Map()
    polylineIndexSnapshots = new Map()
    selectedPolygonIndex.value = -1
    selectedPolylineIndex.value = -1
    selectedPolygonIds.value = []
    selectedPolylineIds.value = []
    hoveredPolygonId.value = null
    hoveredPolylineId.value = null
    spatialIndex.clear()
  }

  const state: EditorState = {
    polygons,
    polylines,
    spatialIndex,
    selectedPolygonIndex,
    selectedPolylineIndex,
    selectedPolygonIds,
    selectedPolylineIds,
    hoveredPolygonId,
    hoveredPolylineId,
    canvasDimensions
  }

  const actions: EditorStateActions = {
    setSelectedPolygonIndex,
    setSelectedPolylineIndex,
    selectPolygonById,
    selectPolylineById,
    clearSelectionSet,
    replacePolygonSelection,
    replacePolylineSelection,
    addPolygonSelection,
    addPolylineSelection,
    togglePolygonSelection,
    togglePolylineSelection,
    clearSelection,
    setHoveredPolygonId,
    setHoveredPolylineId,
    updateCanvasDimensions,
    clearHoverAndSelectionStates,
    resetAll
  }

  return {
    state,
    actions,
    polygons,
    polylines,
    spatialIndex,
    selectedPolygonIndex,
    selectedPolylineIndex,
    selectedPolygonIds,
    selectedPolylineIds,
    hoveredPolygonId,
    hoveredPolylineId,
    canvasDimensions
  }
}
