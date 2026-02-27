/**
 * Common type definitions for the data models.
 */

/**
 * Represents a 2D vertex as an [x, y] tuple.
 */
export type Vertex = [number, number]

/**
 * Represents a 2D point as an {x, y} object.
 * Used for backward compatibility with existing code.
 */
export interface Point {
  x: number
  y: number
}

/**
 * Polygon type enumeration for different semantic meanings.
 */
export const PolygonType = {
  REGION: 'region',
  TEXTLINE: 'textline',
  BASELINE: 'baseline'
} as const

export type PolygonType = typeof PolygonType[keyof typeof PolygonType]

/**
 * Plain object representation of a polygon for serialization.
 */
export interface PolygonData {
  id: string
  label: string
  type: string
  points: Vertex[]
  parentId?: string
}

/**
 * Plain object representation of a polyline for serialization.
 */
export interface PolylineData {
  id: string
  label: string
  points: Vertex[]
  parentId?: string
}

/**
 * Plain object representation of text content variant (PAGE XML TextEquiv).
 * Used when working with plain objects instead of TextContentVariant class instances.
 */
export interface TextContentVariantData {
  unicode: string
  plainText?: string
  confidence?: number
  index?: number
  dataType?: string
  dataTypeDetails?: string
  comments?: string
}

/**
 * Represents an axis-aligned bounding box.
 */
export interface BoundingBox {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

/**
 * View state for canvas transformations (pan and zoom).
 */
export interface View {
  zoom: number
  offsetX: number
  offsetY: number
}

/**
 * Aspect ratio scaling factors for maintaining correct proportions.
 */
export interface AspectRatioScale {
  scaleX: number
  scaleY: number
}

/**
 * Image dimensions in pixels.
 */
export interface ImageSize {
  width: number
  height: number
}

/**
 * Information about a hovered edge on a polygon.
 */
export interface HoveredEdgeInfo {
  edgeIndex: number
  position: Point
}

/**
 * Information about a dragged node during editing.
 */
export interface DraggedNodeInfo {
  nodeIndex: number
  polygonIndex?: number
  polylineIndex?: number
  startPosition: Point
}

/**
 * Coordinate conversion function type.
 */
export type GetWorldCoordsFunction = (
  e: MouseEvent,
  canvas: HTMLCanvasElement,
  view: View,
  aspectRatioScale: AspectRatioScale
) => Point
