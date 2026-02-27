/**
 * Visibility utilities for parent-child polygon and polyline relationships.
 * Provides consistent visibility logic across rendering, hit detection, and interaction.
 */

import type { Point } from '@/models/editor'
import type { Polygon, Polyline } from '@/services/editor/visibility-service'

type Element = Polygon | Polyline

export function shouldPolygonBeVisible(
  polygon: Polygon,
  allPolygons: Polygon[],
  selectedPolygonIndex: number,
  _allPolylines: Polyline[] = []
): boolean {
  if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
    return !polygon.parentId
  }

  const selectedPolygon = allPolygons[selectedPolygonIndex]
  if (!selectedPolygon) {
    return !polygon.parentId
  }
  const selectedId = selectedPolygon.id

  const selectedRoot = getRootOfHierarchy(selectedPolygon, allPolygons)
  const selectedDepth = getElementDepth(selectedPolygon, allPolygons)

  const polygonRoot = getRootOfHierarchy(polygon, allPolygons)
  const polygonDepth = getElementDepth(polygon, allPolygons)

  const inSameTree = polygonRoot.id === selectedRoot.id

  if (inSameTree) {
    if (polygon.id === selectedId) {
      return true
    }

    if (isAncestorOf(polygon, allPolygons, selectedPolygonIndex)) {
      return true
    }

    if (polygon.parentId === selectedId) {
      return true
    }

    if (polygonDepth <= selectedDepth) {
      const branchMaxDepth = getBranchMaxDepth(polygon, allPolygons)
      return polygonDepth <= Math.min(selectedDepth, branchMaxDepth)
    }

    return false
  } else {
    return !polygon.parentId
  }
}

/**
 * Check if a polygon should show hover feedback based on hierarchical relationships.
 * Works "downwards-only" - only direct children of selected element show hover, never parents or siblings.
 * Supports arbitrary levels of nesting.
 *
 * @param polygon - The polygon to check
 * @param allPolygons - Array of all polygons in the scene
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @returns True if polygon should show hover feedback
 */
export function shouldPolygonShowHover(
  polygon: Polygon,
  allPolygons: Polygon[],
  selectedPolygonIndex: number
): boolean {
  if (selectedPolygonIndex < 0) {
    return !polygon.parentId
  }

  const selectedPolygon = allPolygons[selectedPolygonIndex]
  if (!selectedPolygon) return false

  if (polygon.id === selectedPolygon.id) {
    return true
  }

  return isDirectChildOf(polygon, allPolygons, selectedPolygonIndex)
}

/**
 * Check if an element is a direct child of the selected element.
 * Only checks immediate parent-child relationships, not deep descendants.
 *
 * @param element - The element to check (polygon or polyline)
 * @param allPolygons - Array of all polygons in the scene
 * @param selectedPolygonIndex - Index of currently selected polygon
 * @returns True if element is a direct child of the selected polygon
 */
function isDirectChildOf(
  element: Element,
  allPolygons: Polygon[],
  selectedPolygonIndex: number
): boolean {
  if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
    return false
  }

  const selectedPolygon = allPolygons[selectedPolygonIndex]
  if (!selectedPolygon) return false
  const selectedId = selectedPolygon.id

  if (element.id === selectedId) {
    return true
  }

  return element.parentId === selectedId
}

/**
 * Check if an element is an ancestor of the selected element.
 * Shows the complete parent chain when a child is selected.
 *
 * @param element - The element to check (polygon or polyline)
 * @param allPolygons - Array of all polygons in the scene
 * @param selectedPolygonIndex - Index of currently selected polygon
 * @returns True if element is an ancestor of the selected polygon
 */
function isAncestorOf(
  element: Element,
  allPolygons: Polygon[],
  selectedPolygonIndex: number
): boolean {
  if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
    return false
  }

  const selectedPolygon = allPolygons[selectedPolygonIndex]
  if (!selectedPolygon) return false
  const selectedId = selectedPolygon.id

  if (element.id === selectedId) {
    return true
  }

  let currentElement: Polygon = selectedPolygon
  while (currentElement.parentId) {
    if (currentElement.parentId === element.id) {
      return true // Found this element as an ancestor
    }

    const parent = allPolygons.find(p => p.id === currentElement.parentId)
    if (!parent) {
      break // Parent not found, stop walking
    }

    currentElement = parent
  }

  return false // This element is not an ancestor of the selected element
}

/**
 * Get the hierarchy depth (level) of an element.
 * Level 0 = top-level (no parent), Level 1 = child of top-level, etc.
 *
 * @param element - The element to check (polygon or polyline)
 * @param allPolygons - Array of all polygons in the scene (for parent lookup)
 * @returns Hierarchy depth of the element
 */
export function getElementDepth(element: Element, allPolygons: Polygon[]): number {
  let depth = 0
  let currentElement: Element = element

  while (currentElement.parentId) {
    depth++
    const parent = allPolygons.find(p => p.id === currentElement.parentId)
    if (!parent) break
    currentElement = parent
  }

  return depth
}

/**
 * Check if a polyline should be visible/interactive based on parent-child relationships.
 * Uses new hierarchical visibility logic that shows all elements at current level + ancestors + direct children.
 * Supports arbitrary levels of nesting.
 *
 * @param polyline - The polyline to check
 * @param allPolygons - Array of all polygons in the scene (for parent lookup)
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @param allPolylines - Array of all polylines in the scene (for level detection)
 * @returns True if polyline should be visible/interactive
 */
export function shouldPolylineBeVisible(
  polyline: Polyline,
  allPolygons: Polygon[],
  selectedPolygonIndex: number,
  allPolylines: Polyline[] = []
): boolean {
  return shouldPolygonBeVisible(polyline, allPolygons, selectedPolygonIndex, allPolylines)
}

/**
 * Check if a polyline should show hover feedback based on hierarchical relationships.
 * Works "downwards-only" - only direct children of selected element show hover, never parents or siblings.
 * Supports arbitrary levels of nesting.
 *
 * @param polyline - The polyline to check
 * @param allPolygons - Array of all polygons in the scene (for parent lookup)
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @returns True if polyline should show hover feedback
 */
export function shouldPolylineShowHover(
  polyline: Polyline,
  allPolygons: Polygon[],
  selectedPolygonIndex: number
): boolean {
  if (selectedPolygonIndex < 0) {
    return !polyline.parentId
  }

  const selectedPolygon = allPolygons[selectedPolygonIndex]
  if (!selectedPolygon) return false

  return isDirectChildOf(polyline, allPolygons, selectedPolygonIndex)
}

/**
 * Get polygon at point that should show hover feedback.
 * Uses "downwards-only" hover logic - only children of selected element show hover.
 *
 * @param polygons - Array of all polygons
 * @param point - Point to check
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @param isPointInPolygonFn - Function to check if point is in polygon
 * @returns Index of hoverable polygon at point, or -1 if none
 */

/**
 * Get polyline at point that is actually visible/selectable.
 * Wrapper around hit detection that respects visibility rules.
 *
 * @param polylines - Array of all polylines
 * @param polygons - Array of all polygons (for parent lookup)
 * @param point - Point to check
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @param isPointNearPolylineFn - Function to check if point is near polyline
 * @param threshold - Distance threshold for polyline detection
 * @returns Index of visible polyline at point, or -1 if none
 */
export function getVisiblePolylineAtPoint(
  polylines: Polyline[],
  polygons: Polygon[],
  point: Point,
  selectedPolygonIndex: number,
  isPointNearPolylineFn: (point: Point, points: Point[], threshold: number) => boolean,
  threshold: number = 0.02
): number {
  for (let i = polylines.length - 1; i >= 0; i--) {
    const polyline = polylines[i]
    if (!polyline) continue

    if (!shouldPolylineBeVisible(polyline, polygons, selectedPolygonIndex)) {
      continue
    }

    if (isPointNearPolylineFn(point, polyline.points, threshold)) {
      return i
    }
  }
  return -1 // No visible polyline found
}

/**
 * Get polyline at point that should show hover feedback.
 * Uses "downwards-only" hover logic - only children of selected element show hover.
 *
 * @param polylines - Array of all polylines
 * @param polygons - Array of all polygons (for parent lookup)
 * @param point - Point to check
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @param isPointNearPolylineFn - Function to check if point is near polyline
 * @param threshold - Distance threshold for polyline detection
 * @returns Index of hoverable polyline at point, or -1 if none
 */

/**
 * Calculate opacity for a hierarchy level using logarithmic scaling.
 * Creates smooth gradient between levels that works for arbitrary nesting depths.
 *
 * @param level - Hierarchy level (0 = root, 1 = child, etc.)
 * @param maxLevel - Maximum level in the current hierarchy
 * @param baseOpacity - Starting opacity for root level (0.0 to 1.0)
 * @param minOpacity - Minimum opacity for deepest levels (0.0 to 1.0)
 * @returns Opacity value between 0.0 and 1.0
 */
export function calculateOpacityForLevel(
  level: number,
  maxLevel: number,
  baseOpacity: number = 0.7,
  minOpacity: number = 0.4
): number {
  if (maxLevel <= 0) return baseOpacity
  if (level >= maxLevel) return minOpacity

  const ratio = level / maxLevel
  const logRatio = Math.log(ratio + 1) / Math.log(2) // Log base 2 scaling
  const opacity = baseOpacity - (logRatio * (baseOpacity - minOpacity))

  return Math.max(minOpacity, Math.min(baseOpacity, opacity))
}

/**
 * Get the complete hierarchy chain from root to the selected polygon.
 * Returns array of polygon indices representing each level in the hierarchy.
 *
 * @param allPolygons - Array of all polygons in the scene
 * @param selectedPolygonIndex - Index of currently selected polygon
 * @returns Array of polygon indices from root to selected (inclusive)
 */
export function getHierarchyChain(
  allPolygons: Polygon[],
  selectedPolygonIndex: number
): number[] {
  const hierarchyChain: number[] = []

  if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
    return hierarchyChain
  }

  let currentPolygonIndex = selectedPolygonIndex

  while (currentPolygonIndex >= 0) {
    hierarchyChain.unshift(currentPolygonIndex) // Add to beginning of array

    const currentPolygon = allPolygons[currentPolygonIndex]
    if (!currentPolygon || !currentPolygon.parentId) {
      break // Reached root level
    }

    const parentIndex = allPolygons.findIndex(p => p.id === currentPolygon.parentId)
    if (parentIndex < 0) {
      break // Parent not found
    }

    currentPolygonIndex = parentIndex
  }

  return hierarchyChain
}

/**
 * Get the maximum hierarchy depth in the current visible set.
 * Used to calculate opacity scaling for multi-level overlays.
 *
 * @param allPolygons - Array of all polygons in the scene
 * @param selectedPolygonIndex - Index of currently selected polygon
 * @returns Maximum hierarchy depth in the visible hierarchy
 */
export function getMaxHierarchyDepth(
  allPolygons: Polygon[],
  selectedPolygonIndex: number
): number {
  if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
    return 0
  }

  const selectedPolygon = allPolygons[selectedPolygonIndex]
  if (!selectedPolygon) return 0
  return getElementDepth(selectedPolygon, allPolygons)
}

/**
 * Check if a polygon should be visible in non-selected rendering context.
 * Shows all root-level polygons + selected hierarchy (ancestors + siblings + direct children only).
 *
 * @param polygon - The polygon to check
 * @param allPolygons - Array of all polygons in the scene
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @returns True if the polygon should be visible
 */
export function shouldNonSelectedPolygonBeVisible(
  polygon: Polygon,
  allPolygons: Polygon[],
  selectedPolygonIndex: number = -1
): boolean {
  if (selectedPolygonIndex < 0 || selectedPolygonIndex >= allPolygons.length) {
    return !polygon.parentId
  }

  const selectedPolygon = allPolygons[selectedPolygonIndex]
  if (!selectedPolygon) return !polygon.parentId

  if (polygon.id === selectedPolygon.id) {
    return false
  }

  return shouldPolygonBeVisible(polygon, allPolygons, selectedPolygonIndex)
}
/**
 * Get the maximum depth of a branch from the root to the deepest descendant.
 * Used to determine the deepest visible level for shallower branches.
 *
 * @param polygon - The polygon to get branch depth for
 * @param allPolygons - Array of all polygons
 * @returns Maximum depth of the branch
 */
function getBranchMaxDepth(polygon: Polygon, allPolygons: Polygon[]): number {
  let maxDepth = getElementDepth(polygon, allPolygons)

  function findDeepestDescendant(currentPolygon: Polygon): void {
    const children = allPolygons.filter(p => p.parentId === currentPolygon.id)
    for (const child of children) {
      const childDepth = getElementDepth(child, allPolygons)
      maxDepth = Math.max(maxDepth, childDepth)
      findDeepestDescendant(child) // Recursively check children
    }
  }

  findDeepestDescendant(polygon)
  return maxDepth
}

/**
 * Get the root polygon of a hierarchy (walk up the parent chain).
 *
 * @param polygon - The polygon to find the root for
 * @param allPolygons - Array of all polygons
 * @returns The root polygon of the hierarchy
 */
export function getRootOfHierarchy(
  polygon: Polygon,
  allPolygons: Polygon[]
): Polygon {
  let currentElement = polygon

  while (currentElement.parentId) {
    const parent = allPolygons.find(p => p.id === currentElement.parentId)
    if (!parent) {
      break // Parent not found, stop walking
    }
    currentElement = parent
  }

  return currentElement
}

/**
 * Check if one polygon is in the hierarchy of another.
 *
 * @param ancestor - The potential ancestor polygon
 * @param descendant - The potential descendant polygon
 * @param allPolygons - Array of all polygons
 * @returns True if descendant is in ancestor's hierarchy
 */
export function isInHierarchy(
  ancestor: Polygon,
  descendant: Polygon,
  allPolygons: Polygon[]
): boolean {
  if (ancestor.id === descendant.id) {
    return true
  }

  if (descendant.parentId) {
    const parent = allPolygons.find(p => p.id === descendant.parentId)
    if (parent) {
      return isInHierarchy(ancestor, parent, allPolygons)
    }
  }

  return false
}
