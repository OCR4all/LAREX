import type { Point, TextContentVariantData } from '@/models/editor'
import type { RegionKind } from '@/models/editor/region'
import type { ConfidenceHeatmapSettings, PageComparisonDiffHighlight } from '@/stores/editor/types'
import type { ArrowSegment, ReadingOrderRenderData } from '@/webgl/editor/reading-order-renderer'

export interface DraggedNodeInfo {
  isDragging: boolean
  nodeIndex?: number
  polygonIndex?: number
  polylineIndex?: number

  originalPoint?: Point | null
  originalPoints?: Point[]
  isNewlyInsertedNode?: boolean
}

export interface PreviewPoint {
  x: number | null
  y: number | null
}

export type ViewMode = 'default' | 'textline' | 'baseline'

export interface RenderablePolygon {
  id: string
  points: Point[]
  parentId?: string
  label?: string
  type?: string
  comments?: string
  /** PAGE XML region kind (TextRegion, ImageRegion, etc.) - only for regions */
  regionKind?: RegionKind
  /** Region subtype (paragraph, heading, etc.) - only for regions with subtypes */
  regionSubtype?: string
  /** Raw PAGE XML @custom for regions - used for label reverse mapping */
  regionCustom?: string
  /** Text content variants for text lines */
  textContentVariants?: TextContentVariantData[]
  /** Element-level confidence (PAGE @conf) */
  confidence?: number
}

export interface RenderablePolyline {
  id: string
  points: Point[]
  parentId?: string
  label?: string
  type?: string
  comments?: string
  /** Element-level confidence (PAGE @conf) */
  confidence?: number
}

export interface CommentOverlayLabel {
  id: string
  position: Point
  text: string
}

export interface RelationOverlayLabel {
  id: string
  relationId?: string
  sourceRegionRef?: string
  targetRegionRef?: string
  sourcePosition?: Point
  targetPosition?: Point
  position: Point
  text: string
  isSelected: boolean
  isDraft?: boolean
}

export interface RelationRenderData {
  segments: ArrowSegment[]
  labels: RelationOverlayLabel[]
}

export interface ActionProcessingRenderTarget {
  page: boolean
  polygonIds: string[]
}

/**
 * Contract between the editor renderer (Vue state) and the WebGL renderer.
 * Keep this stable to avoid accidental behavioral regressions.
 */
export interface WebGLRenderState {
  polygons: RenderablePolygon[]
  polylines: RenderablePolyline[]

  hoveredPolygonIndex: Ref<number>
  selectedPolygonIndex: Ref<number>
  hoveredPolylineIndex: Ref<number>
  selectedPolylineIndex: Ref<number>

  selectedPolygonIds: Ref<string[]>
  selectedPolylineIds: Ref<string[]>

  hiddenPolygonIds: Ref<string[]>
  hiddenPolylineIds: Ref<string[]>

  hoveredNodeIndex: Ref<number>
  previewNodePosition: Point | null
  draggedNodeInfo?: DraggedNodeInfo

  hoveredPolylineNodeIndex?: Ref<number>
  polylinePreviewNodePosition?: Point | null
  polylineDraggedNodeInfo?: DraggedNodeInfo

  currentPolygonPoints: Point[]
  currentPolylinePoints: Point[]
  previewPoint: PreviewPoint
  polylinePreviewPoint?: PreviewPoint
  rectanglePreviewPoints?: Point[]

  cutLinePoints?: Point[]
  cutPolygonPoints?: Point[]
  cutRectanglePoints?: Point[]
  cutPreviewPoint?: PreviewPoint
  cutMode?: 'line' | 'polygon' | 'rectangle'
  isCutDrawingActive?: boolean

  isInvalidPosition: Ref<boolean>

  moveState?: {
    isMoving: boolean
    elementId: string | null
    isInvalid: boolean
  }

  viewMode?: ViewMode

  readingOrderData?: ReadingOrderRenderData
  showReadingOrderOverlay?: boolean
  relationData?: RelationRenderData
  showRelationsOverlay?: boolean

  autoParentPreview?: AutoParentPreview

  bufferPreview?: {
    polygonId: string
    points: Point[]
  }

  confidenceHeatmap?: ConfidenceHeatmapSettings
  actionProcessingTargets?: ActionProcessingRenderTarget | null
  diffHighlights?: Record<string, PageComparisonDiffHighlight>
}

/**
 * Preview information for auto-parent assignment during Textline/Baseline creation.
 * Used to show visual feedback about which parent the new element will be assigned to,
 * or what helper shape will be created.
 */
export interface AutoParentPreview {
  /** The detected or to-be-created parent polygon */
  parentPolygon: RenderablePolygon | null
  /** Whether the parent already exists (true) or will be created (false) */
  isExisting: boolean
  /** Preview of helper shape to be created (bounding rectangle) */
  helperShapePoints?: Point[]
  /** For baseline mode: preview of the textline that will be created */
  helperTextlinePoints?: Point[]
  /** Level of auto-creation needed: 'none' | 'textline' | 'region' | 'both' */
  creationLevel: 'none' | 'textline' | 'region' | 'both'
}
