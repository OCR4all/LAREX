import type { SpatialIndexService } from '@/services/editor/spatial-index-service'

export interface EditorPolygon {
  id: string
  points: Array<{ x: number, y: number }>
  type?: string
  parentId?: string
  label?: string
}

export interface EditorPolyline {
  id: string
  points: Array<{ x: number, y: number }>
  type?: string
  parentPolygonId?: string
  parentId?: string
  label?: string
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

  watch(polygons, () => {
    spatialIndex.rebuildPolygonIndex(polygons)
  }, { deep: true })

  watch(polylines, () => {
    spatialIndex.rebuildPolylineIndex(polylines)
  }, { deep: true })

  watch(polygons, () => {
    if (selectedPolygonIndex.value >= 0 && !polygons[selectedPolygonIndex.value]) {
      selectedPolygonIndex.value = -1
    }

    if (hoveredPolygonId.value && !polygons.some(p => p.id === hoveredPolygonId.value)) {
      hoveredPolygonId.value = null
    }

    if (selectedPolygonIds.value.length > 0) {
      const existing = new Set(polygons.map(p => p.id))
      selectedPolygonIds.value = selectedPolygonIds.value.filter(id => existing.has(id))
    }
  }, { deep: true })

  watch(polylines, () => {
    if (selectedPolylineIndex.value >= 0 && !polylines[selectedPolylineIndex.value]) {
      selectedPolylineIndex.value = -1
    }

    if (hoveredPolylineId.value && !polylines.some(p => p.id === hoveredPolylineId.value)) {
      hoveredPolylineId.value = null
    }

    if (selectedPolylineIds.value.length > 0) {
      const existing = new Set(polylines.map(p => p.id))
      selectedPolylineIds.value = selectedPolylineIds.value.filter(id => existing.has(id))
    }
  }, { deep: true })

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
