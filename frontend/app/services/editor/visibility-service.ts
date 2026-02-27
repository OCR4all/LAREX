/**
 * Centralized visibility service with memoization for hierarchical polygons/polylines.
 * Replaces scattered visibility logic across multiple files with a single source of truth.
 */

import type { Point } from '@/models/editor'

export interface BaseElement {
  id: string
  parentId?: string
  points: Point[]
}

export interface Polygon extends BaseElement {
  type?: string
}

export interface Polyline extends BaseElement {
}

export interface VisibilityContext {
  selectedPolygonIndex: number
  selectedPolylineIndex?: number
  allPolygons: Polygon[]
  allPolylines?: Polyline[]
  viewMode?: 'default' | 'textline' | 'baseline' // Optional view mode filter
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

    if (viewMode === 'textline') {
      return polygon.type === 'region'
    }

    if (viewMode === 'baseline') {
      return polygon.type === 'region' || polygon.type === 'textline'
    }

    return false
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
      if (viewMode === 'textline') {
        return polygon.type === 'textline'
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
      if (viewMode === 'textline') {
        return polygon.type === 'textline'
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
      if (viewMode === 'textline') {
        return polygon.type === 'textline'
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
      if (viewMode === 'textline') {
        return polygon.type === 'textline'
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

    const root = this.getRootOfHierarchy(selectedPolygon, allPolygons)
    const depth = this.getElementDepth(selectedPolygon, allPolygons)
    const branchMaxDepth = this.getBranchMaxDepth(selectedPolygon, allPolygons)

    for (const polygon of allPolygons) {
      if (this.isPolygonVisibleInHierarchy(polygon, selectedPolygon, allPolygons, depth)) {
        visibleIds.add(polygon.id)
      }

      if (this.isPolygonHoverableInHierarchy(polygon, selectedPolygon)) {
        hoverableIds.add(polygon.id)
      }
    }

    let current: Polygon = selectedPolygon
    while (current.parentId) {
      ancestorIds.add(current.parentId)
      const parent = allPolygons.find(p => p.id === current.parentId)
      if (!parent) break
      current = parent
    }

    for (const polygon of allPolygons) {
      if (polygon.parentId === selectedPolygon.id) {
        directChildIds.add(polygon.id)
      }
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
    allPolygons: Polygon[],
    selectedDepth: number
  ): boolean {
    const selectedId = selectedPolygon.id
    const selectedRoot = this.getRootOfHierarchy(selectedPolygon, allPolygons)
    const polygonRoot = this.getRootOfHierarchy(polygon, allPolygons)
    const polygonDepth = this.getElementDepth(polygon, allPolygons)

    const inSameTree = polygonRoot.id === selectedRoot.id

    if (inSameTree) {
      if (polygon.id === selectedId) {
        return true
      }

      if (this.isAncestorOf(polygon, selectedPolygon, allPolygons)) {
        return true
      }

      if (polygon.parentId === selectedId) {
        return true
      }

      if (polygonDepth <= selectedDepth) {
        const branchMaxDepth = this.getBranchMaxDepth(polygon, allPolygons)
        return polygonDepth <= Math.min(selectedDepth, branchMaxDepth)
      }

      return false
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
  private getRootOfHierarchy(polygon: Polygon, allPolygons: Polygon[]): Polygon {
    let current = polygon
    while (current.parentId) {
      const parent = allPolygons.find(p => p.id === current.parentId)
      if (!parent) break
      current = parent
    }
    return current
  }

  /**
   * Get the depth of an element in the hierarchy
   */
  private getElementDepth(element: BaseElement, allPolygons: Polygon[]): number {
    let depth = 0
    let current: BaseElement = element

    while (current.parentId) {
      depth++
      const parent = allPolygons.find(p => p.id === current.parentId)
      if (!parent) break
      current = parent
    }

    return depth
  }

  /**
   * Get the maximum depth of a branch
   */
  private getBranchMaxDepth(polygon: Polygon, allPolygons: Polygon[]): number {
    let maxDepth = this.getElementDepth(polygon, allPolygons)

    const findDeepestDescendant = (current: Polygon): void => {
      const children = allPolygons.filter(p => p.parentId === current.id)
      for (const child of children) {
        const childDepth = this.getElementDepth(child, allPolygons)
        maxDepth = Math.max(maxDepth, childDepth)
        findDeepestDescendant(child)
      }
    }

    findDeepestDescendant(polygon)
    return maxDepth
  }

  /**
   * Check if one polygon is an ancestor of another
   */
  private isAncestorOf(
    ancestor: BaseElement,
    descendant: BaseElement,
    allPolygons: Polygon[]
  ): boolean {
    if (ancestor.id === descendant.id) {
      return true
    }

    let current: BaseElement = descendant
    while (current.parentId) {
      if (current.parentId === ancestor.id) {
        return true
      }
      const parent = allPolygons.find(p => p.id === current.parentId)
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

    return this.getElementDepth(selectedPolygon, allPolygons)
  }
}

export const visibilityService = new VisibilityService()
