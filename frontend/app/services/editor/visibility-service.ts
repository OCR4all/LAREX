/**
 * Centralized visibility service with memoization for hierarchical polygons/polylines.
 * Replaces scattered visibility logic across multiple files with a single source of truth.
 */

import type { Point } from '@/models/editor'
import type { ViewMode } from '@/types/editor/rendering'

export interface BaseElement {
  id: string
  parentId?: string
  points: Point[]
}

export interface Polygon extends BaseElement {
  type?: string
}

export type Polyline = BaseElement

export interface VisibilityContext {
  selectedPolygonIndex: number
  selectedPolylineIndex?: number
  allPolygons: Polygon[]
  allPolylines?: Polyline[]
  viewMode?: ViewMode // Optional view mode filter
  hiddenPolygonIds?: Set<string>
  hiddenPolylineIds?: Set<string>
  temporaryHoverPolygonId?: string | null
  temporaryHoverPolylineId?: string | null
}

export interface HierarchyInfo {
  root: Polygon
  depth: number
  visibleIds: Set<string>
  hoverableIds: Set<string>
  ancestorIds: Set<string>
  directChildIds: Set<string>
  branchMaxDepth: number
}

/** Polygon types from the outermost to the innermost hierarchy level. */
const POLYGON_TYPE_DEPTH: Record<string, number> = {
  region: 0,
  textline: 1,
  word: 2,
  glyph: 3
}

const LAYOUT_VIEW_MODES: ReadonlySet<string> = new Set<ViewMode>(['default', 'textline', 'word', 'glyph', 'baseline'])

/** Narrow an arbitrary view mode string to a known layout view mode. */
export function normalizeLayoutViewMode(raw: string | undefined): ViewMode | undefined {
  return raw && LAYOUT_VIEW_MODES.has(raw) ? raw as ViewMode : undefined
}

/**
 * The polygon type that is directly selectable in a layout view mode, or null
 * when the view mode doesn't focus a polygon level (default, baseline).
 */
export function getViewModePolygonType(viewMode: string | undefined): 'textline' | 'word' | 'glyph' | null {
  if (viewMode === 'textline' || viewMode === 'word' || viewMode === 'glyph') return viewMode
  return null
}

/**
 * Centralized service for managing visibility logic with caching
 */
export class VisibilityService {
  private hierarchyCache = new WeakMap<Polygon, HierarchyInfo>()

  private isPolygonHidden(polygonId: string, context: VisibilityContext): boolean {
    return context.hiddenPolygonIds?.has(polygonId) ?? false
  }

  private isPolylineHidden(polylineId: string, context: VisibilityContext): boolean {
    return context.hiddenPolylineIds?.has(polylineId) ?? false
  }

  /**
   * Check if a polygon should be rendered as a non-selectable background element.
   * Background elements provide visual context for the user without being interactive.
   *
   * In TEXTLINE mode: Regions are rendered as background (textlines are selectable)
   * In WORD mode: Regions and Textlines are rendered as background (words are selectable)
   * In GLYPH mode: Regions, Textlines and Words are rendered as background (glyphs are selectable)
   * In BASELINE mode: Regions and Textlines are rendered as background (baselines are selectable)
   *
   * Only applies when NOTHING is selected - once a selection is made, normal hierarchy applies.
   */
  shouldRenderAsBackground(polygon: Polygon, context: VisibilityContext): boolean {
    const { selectedPolygonIndex, selectedPolylineIndex, viewMode } = context

    if (this.isPolygonHidden(polygon.id, context)) return false

    const hasSelection = selectedPolygonIndex >= 0 || (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0)

    if (hasSelection || !viewMode || viewMode === 'default') {
      return false
    }

    const focusedType = viewMode === 'baseline' ? 'word' : getViewModePolygonType(viewMode)
    if (!focusedType) return false

    const depth = POLYGON_TYPE_DEPTH[polygon.type ?? 'region'] ?? 0
    return depth < POLYGON_TYPE_DEPTH[focusedType]!
  }

  /**
   * Check if a polygon should be visible
   */
  shouldShowPolygon(polygon: Polygon, context: VisibilityContext): boolean {
    const { selectedPolygonIndex, selectedPolylineIndex, allPolygons, allPolylines, viewMode } = context

    if (context.temporaryHoverPolygonId === polygon.id) return true

    if (this.isPolygonHidden(polygon.id, context)) return false

    const hasSelection = selectedPolygonIndex >= 0 || (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0)

    if (!hasSelection && viewMode && viewMode !== 'default') {
      const focusedType = getViewModePolygonType(viewMode)
      if (focusedType) {
        return polygon.type === focusedType
      }

      if (viewMode === 'baseline') {
        return false
      }
    }

    if (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0) {
      const selectedPolylines = allPolylines || []
      const selectedPolyline = selectedPolylines[selectedPolylineIndex]

      if (selectedPolyline && selectedPolyline.parentId) {
        const parentPolygon = allPolygons.find(p => p.id === selectedPolyline.parentId)
        if (!parentPolygon) return false

        const parentIndex = allPolygons.findIndex(p => p.id === parentPolygon.id)
        if (parentIndex >= 0) {
          const hierarchy = this.getHierarchy(parentPolygon, allPolygons)
          return hierarchy.visibleIds.has(polygon.id)
        }
      }

      return false
    }

    if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
      return !polygon.parentId
    }

    const selectedPolygon = allPolygons[selectedPolygonIndex]
    if (!selectedPolygon) return false

    const hierarchy = this.getHierarchy(selectedPolygon, allPolygons)
    return hierarchy.visibleIds.has(polygon.id)
  }

  /**
   * Check if a polygon should show hover feedback
   */
  shouldShowPolygonHover(polygon: Polygon, context: VisibilityContext): boolean {
    const { selectedPolygonIndex, selectedPolylineIndex, allPolygons, viewMode } = context

    if (this.isPolygonHidden(polygon.id, context)) return false

    const hasSelection = selectedPolygonIndex >= 0 || (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0)

    if (!hasSelection && viewMode && viewMode !== 'default') {
      const focusedType = getViewModePolygonType(viewMode)
      if (focusedType) {
        return polygon.type === focusedType
      }

      if (viewMode === 'baseline') {
        return false
      }
    }

    if (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0) {
      return false
    }

    if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
      return !polygon.parentId
    }

    const selectedPolygon = allPolygons[selectedPolygonIndex]
    if (!selectedPolygon) return false

    const hierarchy = this.getHierarchy(selectedPolygon, allPolygons)
    return hierarchy.hoverableIds.has(polygon.id)
  }

  /**
   * Check if a polyline should be visible
   */
  shouldShowPolyline(polyline: Polyline, context: VisibilityContext): boolean {
    const { selectedPolygonIndex, selectedPolylineIndex, allPolygons, allPolylines, viewMode } = context

    if (context.temporaryHoverPolylineId === polyline.id) return true

    if (this.isPolylineHidden(polyline.id, context)) return false

    const hasSelection = selectedPolygonIndex >= 0 || (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0)

    if (!hasSelection && viewMode === 'baseline') {
      return true
    }

    if (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0) {
      const selectedPolylines = allPolylines || []
      if (selectedPolylines[selectedPolylineIndex]?.id === polyline.id) {
        return true
      }
      const selectedPolyline = selectedPolylines[selectedPolylineIndex]
      if (selectedPolyline && polyline.parentId === selectedPolyline.parentId) {
        return true
      }
    }

    if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
      if (!polyline.parentId) {
        return true // Orphaned polyline
      }
      const parent = allPolygons.find(p => p.id === polyline.parentId)
      return parent ? !parent.parentId : false
    }

    const selectedPolygon = allPolygons[selectedPolygonIndex]
    if (!selectedPolygon) return false

    if (polyline.parentId === selectedPolygon.id) {
      return true
    }

    return false
  }

  /**
   * Check if a polyline should show hover feedback
   */
  shouldShowPolylineHover(polyline: Polyline, context: VisibilityContext): boolean {
    const { selectedPolygonIndex, selectedPolylineIndex, allPolygons, allPolylines, viewMode } = context

    if (this.isPolylineHidden(polyline.id, context)) return false

    const hasSelection = selectedPolygonIndex >= 0 || (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0)

    if (!hasSelection && viewMode === 'baseline') {
      return true
    }

    if (selectedPolylineIndex !== undefined && selectedPolylineIndex >= 0) {
      const selectedPolylines = allPolylines || []
      const selectedPolyline = selectedPolylines[selectedPolylineIndex]
      return selectedPolyline?.id === polyline.id
    }

    if (selectedPolygonIndex >= 0 && selectedPolygonIndex < allPolygons.length) {
      const selectedPolygon = allPolygons[selectedPolygonIndex]
      if (selectedPolygon && polyline.parentId === selectedPolygon.id) {
        return true
      }
    }

    if (selectedPolygonIndex < 0) {
      if (!polyline.parentId) {
        return true // Orphaned polyline
      }
      const parent = allPolygons.find(p => p.id === polyline.parentId)
      return parent ? !parent.parentId : false
    }

    return false
  }

  /**
   * Check if a non-selected polygon should be visible (for rendering)
   */
  /**
   * Check if a non-selected polygon should be visible (for rendering outlines)
   * Uses the same hierarchy visibility logic as shouldShowPolygon, but excludes the selected polygon itself
   */
  shouldShowNonSelectedPolygon(polygon: Polygon, context: VisibilityContext): boolean {
    const { selectedPolygonIndex, allPolygons, viewMode } = context

    if (context.temporaryHoverPolygonId === polygon.id) return true

    if (this.isPolygonHidden(polygon.id, context)) return false

    const hasSelection = selectedPolygonIndex >= 0 && selectedPolygonIndex < allPolygons.length

    if (!hasSelection && viewMode && viewMode !== 'default') {
      const focusedType = getViewModePolygonType(viewMode)
      if (focusedType) {
        return polygon.type === focusedType
      }

      if (viewMode === 'baseline') {
        return false
      }
    }

    if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
      return !polygon.parentId
    }

    const selectedPolygon = allPolygons[selectedPolygonIndex]
    if (!selectedPolygon) return !polygon.parentId

    if (polygon.id === selectedPolygon.id) {
      return false
    }

    const hierarchy = this.getHierarchy(selectedPolygon, allPolygons)
    return hierarchy.visibleIds.has(polygon.id)
  }

  /**
   * Check if a polygon should be selectable via clicking (more restrictive than visibility)
   * Only direct children of the selected polygon are selectable.
   * Clicking on anything else (ancestors, siblings, outside) should go up one level.
   */
  shouldBeSelectablePolygon(polygon: Polygon, context: VisibilityContext): boolean {
    const { selectedPolygonIndex, allPolygons, viewMode } = context

    if (this.isPolygonHidden(polygon.id, context)) return false

    const hasSelection = selectedPolygonIndex >= 0 && selectedPolygonIndex < allPolygons.length

    if (!hasSelection && viewMode && viewMode !== 'default') {
      const focusedType = getViewModePolygonType(viewMode)
      if (focusedType) {
        return polygon.type === focusedType
      }

      if (viewMode === 'baseline') {
        return false
      }
    }

    if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
      return !polygon.parentId
    }

    const selectedPolygon = allPolygons[selectedPolygonIndex]
    if (!selectedPolygon) return !polygon.parentId

    if (polygon.id === selectedPolygon.id) {
      return false
    }

    return polygon.parentId === selectedPolygon.id
  }

  /**
   * Get hierarchy information with caching
   */
  private getHierarchy(selectedPolygon: Polygon, allPolygons: Polygon[]): HierarchyInfo {
    if (this.hierarchyCache.has(selectedPolygon)) {
      return this.hierarchyCache.get(selectedPolygon)!
    }

    const hierarchy = this.buildHierarchy(selectedPolygon, allPolygons)

    this.hierarchyCache.set(selectedPolygon, hierarchy)

    return hierarchy
  }

  /**
   * Build hierarchy information for a selected polygon
   */
  private buildHierarchy(selectedPolygon: Polygon, allPolygons: Polygon[]): HierarchyInfo {
    const visibleIds = new Set<string>()
    const hoverableIds = new Set<string>()
    const ancestorIds = new Set<string>()
    const directChildIds = new Set<string>()

    // Pages with word/glyph annotations can hold thousands of polygons, so
    // resolve parents through an index instead of scanning the array.
    const byId = new Map<string, Polygon>()
    const childrenById = new Map<string, Polygon[]>()
    for (const polygon of allPolygons) {
      byId.set(polygon.id, polygon)
      if (!polygon.parentId) continue
      const siblings = childrenById.get(polygon.parentId)
      if (siblings) siblings.push(polygon)
      else childrenById.set(polygon.parentId, [polygon])
    }

    const root = this.getRootOfHierarchy(selectedPolygon, byId)
    const depth = this.getElementDepth(selectedPolygon, byId)
    const branchMaxDepth = this.getBranchMaxDepth(selectedPolygon, byId, childrenById)

    for (const polygon of allPolygons) {
      if (this.isPolygonVisibleInHierarchy(polygon, selectedPolygon, root, byId, depth)) {
        visibleIds.add(polygon.id)
      }

      if (this.isPolygonHoverableInHierarchy(polygon, selectedPolygon)) {
        hoverableIds.add(polygon.id)
      }
    }

    let current: Polygon = selectedPolygon
    while (current.parentId) {
      ancestorIds.add(current.parentId)
      const parent = byId.get(current.parentId)
      if (!parent) break
      current = parent
    }

    for (const child of childrenById.get(selectedPolygon.id) ?? []) {
      directChildIds.add(child.id)
    }

    return {
      root,
      depth,
      visibleIds,
      hoverableIds,
      ancestorIds,
      directChildIds,
      branchMaxDepth
    }
  }

  /**
   * Check if a polygon should be visible in the hierarchy
   */
  private isPolygonVisibleInHierarchy(
    polygon: Polygon,
    selectedPolygon: Polygon,
    selectedRoot: Polygon,
    byId: Map<string, Polygon>,
    selectedDepth: number
  ): boolean {
    const selectedId = selectedPolygon.id
    const polygonRoot = this.getRootOfHierarchy(polygon, byId)

    const inSameTree = polygonRoot.id === selectedRoot.id

    if (inSameTree) {
      if (polygon.id === selectedId) {
        return true
      }

      if (this.isAncestorOf(polygon, selectedPolygon, byId)) {
        return true
      }

      if (polygon.parentId === selectedId) {
        return true
      }

      // Same-tree elements up to the selected level stay visible (siblings, cousins, ...).
      return this.getElementDepth(polygon, byId) <= selectedDepth
    } else {
      return !polygon.parentId
    }
  }

  /**
   * Check if a polygon should be hoverable in the hierarchy
   */
  private isPolygonHoverableInHierarchy(
    polygon: Polygon,
    selectedPolygon: Polygon
  ): boolean {
    if (polygon.id === selectedPolygon.id) {
      return true
    }

    return polygon.parentId === selectedPolygon.id
  }

  /**
   * Get the root polygon of a hierarchy
   */
  private getRootOfHierarchy(polygon: Polygon, byId: Map<string, Polygon>): Polygon {
    let current = polygon
    while (current.parentId) {
      const parent = byId.get(current.parentId)
      if (!parent) break
      current = parent
    }
    return current
  }

  /**
   * Get the depth of an element in the hierarchy
   */
  private getElementDepth(element: BaseElement, byId: Map<string, Polygon>): number {
    let depth = 0
    let current: BaseElement = element

    while (current.parentId) {
      depth++
      const parent = byId.get(current.parentId)
      if (!parent) break
      current = parent
    }

    return depth
  }

  /**
   * Get the maximum depth of a branch
   */
  private getBranchMaxDepth(polygon: Polygon, byId: Map<string, Polygon>, childrenById: Map<string, Polygon[]>): number {
    const baseDepth = this.getElementDepth(polygon, byId)
    let maxDepth = baseDepth

    const findDeepestDescendant = (current: Polygon, currentDepth: number): void => {
      for (const child of childrenById.get(current.id) ?? []) {
        maxDepth = Math.max(maxDepth, currentDepth + 1)
        findDeepestDescendant(child, currentDepth + 1)
      }
    }

    findDeepestDescendant(polygon, baseDepth)
    return maxDepth
  }

  /**
   * Check if one polygon is an ancestor of another
   */
  private isAncestorOf(
    ancestor: BaseElement,
    descendant: BaseElement,
    byId: Map<string, Polygon>
  ): boolean {
    if (ancestor.id === descendant.id) {
      return true
    }

    let current: BaseElement = descendant
    while (current.parentId) {
      if (current.parentId === ancestor.id) {
        return true
      }
      const parent = byId.get(current.parentId)
      if (!parent) break
      current = parent
    }

    return false
  }

  /**
   * Clear the entire cache (use sparingly - prefer invalidate() for targeted invalidation)
   */
  clearCache(): void {
    this.hierarchyCache = new WeakMap()
  }

  /**
   * Invalidate cache entries for a specific polygon and its ancestors.
   * This is more efficient than clearing the entire cache when only a single
   * polygon or its hierarchy is affected.
   *
   * Use cases:
   * - Polygon points changed
   * - Polygon parent changed
   * - Polygon deleted
   * - Child added/removed
   *
   * @param polygonId - ID of the polygon that was modified
   * @param polygons - Array of all polygons
   */
  invalidate(polygonId: string, polygons: Polygon[]): void {
    const polygon = polygons.find(p => p.id === polygonId)
    if (!polygon) return

    this.hierarchyCache.delete(polygon)

    let current = polygon
    while (current.parentId) {
      const parent = polygons.find(p => p.id === current.parentId)
      if (!parent) break

      this.hierarchyCache.delete(parent)
      current = parent
    }
  }

  /**
   * Invalidate cache entries for multiple polygons and their ancestors.
   * More efficient than calling invalidate() multiple times.
   *
   * @param polygonIds - Array of polygon IDs that were modified
   * @param polygons - Array of all polygons
   */
  invalidateMultiple(polygonIds: string[], polygons: Polygon[]): void {
    const toInvalidate = new Set<Polygon>()

    for (const polygonId of polygonIds) {
      const polygon = polygons.find(p => p.id === polygonId)
      if (!polygon) continue

      toInvalidate.add(polygon)

      let current = polygon
      while (current.parentId) {
        const parent = polygons.find(p => p.id === current.parentId)
        if (!parent) break

        toInvalidate.add(parent)
        current = parent
      }
    }

    for (const polygon of toInvalidate) {
      this.hierarchyCache.delete(polygon)
    }
  }

  /**
   * Invalidate cache when a polygon's parent changes.
   * This invalidates both the old and new parent hierarchies.
   *
   * @param polygonId - ID of the polygon whose parent changed
   * @param oldParentId - Previous parent ID (undefined if was root)
   * @param newParentId - New parent ID (undefined if becoming root)
   * @param polygons - Array of all polygons
   */
  invalidateParentChange(
    polygonId: string,
    oldParentId: string | undefined,
    newParentId: string | undefined,
    polygons: Polygon[]
  ): void {
    const polygon = polygons.find(p => p.id === polygonId)
    if (!polygon) return

    this.hierarchyCache.delete(polygon)

    if (oldParentId) {
      let current: Polygon | undefined = polygons.find(p => p.id === oldParentId)
      while (current) {
        this.hierarchyCache.delete(current)
        const parentId = current.parentId
        if (!parentId) break
        current = polygons.find(p => p.id === parentId)
      }
    }

    if (newParentId) {
      let current: Polygon | undefined = polygons.find(p => p.id === newParentId)
      while (current) {
        this.hierarchyCache.delete(current)
        const parentId = current.parentId
        if (!parentId) break
        current = polygons.find(p => p.id === parentId)
      }
    }
  }

  /**
   * Get hierarchy chain from root to selected polygon
   */
  getHierarchyChain(_allPolygons: Polygon[], selectedPolygonIndex: number): number[] {
    const allPolygons = _allPolygons
    const chain: number[] = []

    if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
      return chain
    }

    let currentIndex = selectedPolygonIndex

    while (currentIndex >= 0) {
      chain.unshift(currentIndex)

      const current = allPolygons[currentIndex]
      if (!current || !current.parentId) break

      const parentIndex = allPolygons.findIndex(p => p.id === current.parentId)
      if (parentIndex < 0) break

      currentIndex = parentIndex
    }

    return chain
  }

  /**
   * Get maximum hierarchy depth
   */
  getMaxHierarchyDepth(allPolygons: Polygon[], selectedPolygonIndex: number): number {
    if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
      return 0
    }

    const selectedPolygon = allPolygons[selectedPolygonIndex]
    if (!selectedPolygon) return 0

    return this.getElementDepth(selectedPolygon, new Map(allPolygons.map(p => [p.id, p])))
  }
}

export const visibilityService = new VisibilityService()
