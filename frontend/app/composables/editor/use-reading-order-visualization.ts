/**
 * Reading Order Visualization Composable
 *
 * Computes the visual data (arrows, group bounds, order numbers) from the reading order
 * data model and region positions.
 */

import type { Ref } from 'vue'
import type { ReadingOrder, ReadingOrderNode, ReadingOrderGroup, RegionRef, Point } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import type { ArrowSegment, GroupBounds, OrderNumber, ReadingOrderRenderData } from '@/webgl/editor/reading-order-renderer'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'

const ARROW_COLOR: [number, number, number, number] = [0.2, 0.6, 0.9, 0.85] // Blue
const ORDERED_GROUP_COLOR: [number, number, number, number] = [0.0, 0.8, 0.4, 0.9] // Bright green
const UNORDERED_GROUP_COLOR: [number, number, number, number] = [0.9, 0.3, 0.6, 0.9] // Bright magenta/pink
const ORDER_NUMBER_COLOR: [number, number, number, number] = [0.1, 0.1, 0.1, 0.9] // Dark gray

export interface ReadingOrderVisualizationOptions {
  showArrows: boolean
  showGroupBounds: boolean
  showOrderNumbers: boolean
  showAllRegions: boolean // When true, shows ALL regions including nested ones
}

export interface UseReadingOrderVisualizationReturn {
  /** Computed render data for WebGL */
  renderData: ComputedRef<ReadingOrderRenderData>

  /** IDs of all regions that are in the reading order */
  includedRegionIds: ComputedRef<Set<string>>

  /** IDs of all regions that could be added to the reading order */
  availableRegionIds: ComputedRef<Set<string>>
}

/**
 * Type guard for ReadingOrderGroup
 */
function isGroup(node: ReadingOrderNode): node is ReadingOrderGroup {
  return 'elements' in node && Array.isArray((node as ReadingOrderGroup).elements)
}

/**
 * Type guard for ordered groups
 */
function isOrderedGroup(node: ReadingOrderNode): boolean {
  return isGroup(node) && (node.kind === 'OrderedGroup' || node.kind === 'OrderedGroupIndexed')
}

/**
 * Calculate centroid from polygon points
 */
function getCentroid(points: Point[]): Point {
  if (points.length === 0) return { x: 0, y: 0 }

  let sumX = 0
  let sumY = 0

  for (const p of points) {
    sumX += p.x
    sumY += p.y
  }

  return {
    x: sumX / points.length,
    y: sumY / points.length
  }
}

/**
 * Calculate bounding box from polygon points
 */
function getBoundingBox(points: Point[]): { minX: number, minY: number, maxX: number, maxY: number } {
  if (points.length === 0) {
    return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
  }

  let minX = points[0]!.x
  let minY = points[0]!.y
  let maxX = points[0]!.x
  let maxY = points[0]!.y

  for (const p of points) {
    minX = Math.min(minX, p.x)
    minY = Math.min(minY, p.y)
    maxX = Math.max(maxX, p.x)
    maxY = Math.max(maxY, p.y)
  }

  return { minX, minY, maxX, maxY }
}

/**
 * Convert bounding box to 4 corner points
 */
function bboxToPoints(bbox: { minX: number, minY: number, maxX: number, maxY: number }): Point[] {
  return [
    { x: bbox.minX, y: bbox.minY },
    { x: bbox.maxX, y: bbox.minY },
    { x: bbox.maxX, y: bbox.maxY },
    { x: bbox.minX, y: bbox.maxY }
  ]
}

/**
 * Merge multiple bounding boxes into one
 */
function mergeBoundingBoxes(boxes: { minX: number, minY: number, maxX: number, maxY: number }[]): { minX: number, minY: number, maxX: number, maxY: number } {
  if (boxes.length === 0) {
    return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
  }

  let minX = boxes[0]!.minX
  let minY = boxes[0]!.minY
  let maxX = boxes[0]!.maxX
  let maxY = boxes[0]!.maxY

  for (let i = 1; i < boxes.length; i++) {
    const box = boxes[i]!
    minX = Math.min(minX, box.minX)
    minY = Math.min(minY, box.minY)
    maxX = Math.max(maxX, box.maxX)
    maxY = Math.max(maxY, box.maxY)
  }

  return { minX, minY, maxX, maxY }
}

/**
 * Main composable for reading order visualization
 */
export function useReadingOrderVisualization(
  readingOrder: Ref<ReadingOrder | undefined>,
  polygons: Ref<RenderablePolygon[]>,
  options: Ref<ReadingOrderVisualizationOptions>,
  hiddenPolygonIds?: Ref<string[]>
): UseReadingOrderVisualizationReturn {
  const editorUiStore = useEditorUiStore()

  /**
   * Build a map from region ID to polygon for fast lookup
   */
  const polygonMap = computed<Map<string, RenderablePolygon>>(() => {
    const map = new Map<string, RenderablePolygon>()
    for (const polygon of polygons.value) {
      map.set(polygon.id, polygon)
    }
    return map
  })

  /**
   * Collect all region IDs currently in the reading order
   */
  const includedRegionIds = computed<Set<string>>(() => {
    const ids = new Set<string>()

    if (!readingOrder.value) return ids

    function collect(node: ReadingOrderNode): void {
      if (isGroup(node)) {
        for (const child of node.elements) {
          collect(child)
        }
      } else {
        ids.add((node as RegionRef).regionRef)
      }
    }

    for (const element of readingOrder.value.root.elements) {
      collect(element)
    }

    return ids
  })

  /**
   * Get all region IDs that could be added to reading order
   * If showAllRegions is true, includes nested regions too
   */
  const availableRegionIds = computed<Set<string>>(() => {
    const available = new Set<string>()
    const included = includedRegionIds.value

    for (const polygon of polygons.value) {
      if (included.has(polygon.id)) continue

      if (!options.value.showAllRegions && polygon.parentId) continue

      available.add(polygon.id)
    }

    return available
  })

  /**
   * Get centroid for a region by its ID
   */
  function getRegionCentroid(regionId: string): Point | null {
    const polygon = polygonMap.value.get(regionId)
    if (!polygon || polygon.points.length === 0) return null
    return getCentroid(polygon.points)
  }

  /**
   * Get bounding box for a region by its ID
   */
  function getRegionBoundingBox(regionId: string): { minX: number, minY: number, maxX: number, maxY: number } | null {
    const polygon = polygonMap.value.get(regionId)
    if (!polygon || polygon.points.length === 0) return null
    return getBoundingBox(polygon.points)
  }

  /**
   * Compute arrow segments from reading order traversal
   */
  function computeArrowSegments(): ArrowSegment[] {
    if (!readingOrder.value || !options.value.showArrows) return []

    const arrows: ArrowSegment[] = []

    /**
     * Traverse the reading order tree and collect arrows between consecutive items
     * For ordered groups: draw arrows in sequence
     * For unordered groups: no arrows between children (just within nested ordered groups)
     */
    function traverse(elements: ReadingOrderNode[], isOrdered: boolean): RegionRef | null {
      let prevRef: RegionRef | null = null
      let lastRef: RegionRef | null = null

      for (const node of elements) {
        if (isGroup(node)) {
          const groupIsOrdered = isOrderedGroup(node)
          const groupLastRef = traverse(node.elements, groupIsOrdered)

          if (isOrdered && prevRef && node.elements.length > 0) {
            const firstRef = findFirstRegionRef(node)
            if (firstRef) {
              const from = getRegionCentroid(prevRef.regionRef)
              const to = getRegionCentroid(firstRef.regionRef)
              if (from && to) {
                arrows.push({ from, to, color: ARROW_COLOR })
              }
            }
          }

          if (groupLastRef) {
            prevRef = groupLastRef
            lastRef = groupLastRef
          }
        } else {
          const ref = node as RegionRef

          if (isOrdered && prevRef) {
            const from = getRegionCentroid(prevRef.regionRef)
            const to = getRegionCentroid(ref.regionRef)
            if (from && to) {
              arrows.push({ from, to, color: ARROW_COLOR })
            }
          }

          prevRef = ref
          lastRef = ref
        }
      }

      return lastRef
    }

    const rootIsOrdered = isOrderedGroup(readingOrder.value.root)
    traverse(readingOrder.value.root.elements, rootIsOrdered)

    return arrows
  }

  /**
   * Find the first RegionRef in a node (for connecting arrows into groups)
   */
  function findFirstRegionRef(node: ReadingOrderNode): RegionRef | null {
    if (!isGroup(node)) {
      return node as RegionRef
    }

    for (const child of node.elements) {
      const found = findFirstRegionRef(child)
      if (found) return found
    }

    return null
  }

  /**
   * Compute group bounding boxes
   */
  function computeGroupBounds(): GroupBounds[] {
    if (!readingOrder.value || !options.value.showGroupBounds) return []

    const bounds: GroupBounds[] = []

    function traverse(node: ReadingOrderNode, depth: number): void {
      if (!isGroup(node)) return

      const memberBoxes: { minX: number, minY: number, maxX: number, maxY: number }[] = []

      function collectMemberBoxes(n: ReadingOrderNode): void {
        if (isGroup(n)) {
          for (const child of n.elements) {
            collectMemberBoxes(child)
          }
        } else {
          const ref = n as RegionRef
          const bbox = getRegionBoundingBox(ref.regionRef)
          if (bbox) {
            memberBoxes.push(bbox)
          }
        }
      }

      collectMemberBoxes(node)

      if (memberBoxes.length > 0) {
        const merged = mergeBoundingBoxes(memberBoxes)
        const padding = 0.02 // Small padding in world coordinates
        merged.minX -= padding
        merged.minY -= padding
        merged.maxX += padding
        merged.maxY += padding

        const isOrdered = isOrderedGroup(node)
        bounds.push({
          points: bboxToPoints(merged),
          color: isOrdered ? ORDERED_GROUP_COLOR : UNORDERED_GROUP_COLOR,
          isOrdered,
          label: isOrdered ? 'Ordered' : 'Unordered'
        })
      }

      for (const child of node.elements) {
        if (isGroup(child)) {
          traverse(child, depth + 1)
        }
      }
    }

    for (const child of readingOrder.value.root.elements) {
      if (isGroup(child)) {
        traverse(child, 0)
      }
    }

    return bounds
  }

  /**
   * Compute nesting depth for a polygon by traversing parentId chain
   */
  function getPolygonDepth(polygonId: string): number {
    let depth = 0
    let currentId: string | undefined = polygonId
    while (currentId) {
      const polygon = polygonMap.value.get(currentId)
      if (!polygon?.parentId) break
      depth++
      currentId = polygon.parentId
    }
    return depth
  }

  /**
   * Compute order numbers for each region in reading order
   */
  function computeOrderNumbers(): OrderNumber[] {
    if (!readingOrder.value || !options.value.showOrderNumbers) return []

    const numbers: OrderNumber[] = []
    const hiddenSet = new Set(hiddenPolygonIds?.value ?? [])
    let orderIndex = 1

    function traverse(node: ReadingOrderNode): void {
      if (isGroup(node)) {
        for (const child of node.elements) {
          traverse(child)
        }
      } else {
        const ref = node as RegionRef
        const polygon = polygonMap.value.get(ref.regionRef)
        const position = getRegionCentroid(ref.regionRef)
        if (position) {
          const depth = getPolygonDepth(ref.regionRef)
          const labelText = polygon?.label ? `${ref.regionRef} (${polygon.label})` : ref.regionRef
          numbers.push({
            position,
            number: orderIndex,
            color: ORDER_NUMBER_COLOR,
            isNested: depth > 0,
            isHidden: hiddenSet.has(ref.regionRef),
            depth,
            label: labelText
          })
          orderIndex++
        }
      }
    }

    for (const element of readingOrder.value.root.elements) {
      traverse(element)
    }

    return numbers
  }

  /**
   * Reactive trigger for deep reading-order changes.
   */
  const readingOrderVersion = computed(() => {
    if (!readingOrder.value) return 0
    return JSON.stringify(readingOrder.value)
  })

  /**
   * Main computed that combines all visualization data
   */
  const renderData = computed<ReadingOrderRenderData>(() => {
    void readingOrderVersion.value
    void editorUiStore.readingOrderVersion

    return {
      arrows: computeArrowSegments(),
      groupBounds: computeGroupBounds(),
      orderNumbers: computeOrderNumbers()
    }
  })

  return {
    renderData,
    includedRegionIds,
    availableRegionIds
  }
}
