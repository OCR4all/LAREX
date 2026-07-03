import type { RelationDraftState } from '@/utils/editor/relations'

export type UiMode = 'layout' | 'text'
export type LayoutViewMode = 'default' | 'textline' | 'baseline'
export type LineWidthPreset = 'thin' | 'light' | 'normal' | 'medium' | 'bold' | 'extraBold'
export type UiModeScope = 'global' | 'per-canvas'
export type ToolbarLayout = 'floating' | 'docked-top' | 'docked-bottom' | 'docked-left' | 'docked-right'
export type VirtualKeyboardMode = 'off' | 'floating'
export type TextItemLayout = 'side-by-side' | 'vertical'
export type TextModeSubmode = 'visual' | 'expert'
export type ConfidenceHeatmapMode = 'indices' | 'average'
export type RelationPickerMode = 'idle' | 'pick-source' | 'pick-target' | 'repick-source' | 'repick-target'

export interface ConfidenceHeatmapSettings {
  enabled: boolean
  mode: ConfidenceHeatmapMode
  selectedIndices: number[]
  logScale: boolean
  logScaleStrength: number
  fillOpacity: number
}

export interface ImageVariant {
  id: string
  url: string
  fileName?: string
  type?: string
  label: string
}

export interface ResolvedTag {
  id: string
  label: string
  color: string | null
}

export type PageIndexingStatus = 'NOT_APPLICABLE' | 'UNINDEXED' | 'INDEXING' | 'INDEXED'
export interface TextConfidenceStats {
  min: number
  max: number
  mean: number
  median: number
  count: number
}
export type AnnotationApiMode = 'PROJECT' | 'DATASET_LINK' | 'DATASET_COPY'

export interface AnnotationApiContext {
  mode: AnnotationApiMode
  basePath: string
  createAllowed: boolean
}

export interface PageComparisonCanvasState {
  id: string
  source: 'version' | 'action-result'
  side: 'current' | 'version'
  readOnly: true
  baseCanvasId: string
  pairedCanvasId?: string
  version?: {
    id: string
    versionNumber: number
    comment?: string | null
    created?: string
  }
}

export type PageComparisonDiffTone = 'added' | 'removed' | 'changed'

export interface PageComparisonDiffHighlight {
  tone: PageComparisonDiffTone
  kind: 'region' | 'textline' | 'baseline'
}

export interface XmlFile {
  id: string
  fileName: string
  schema: 'PAGE_XML' | 'ALTO_XML' | 'UNKNOWN'
  schemaVersion?: string
  variant?: string
}

export interface PageData {
  id: string
  projectId: string
  projectName?: string
  label: string
  imageVariants: ImageVariant[]
  xmlFiles: XmlFile[]
  thumbnail?: string
  tags?: string[]
  resolvedTags?: ResolvedTag[] | null
  sortOrder?: number | null
  textConfidence?: TextConfidenceStats | null
  locked?: boolean
  lockedReason?: string | null
  /** Available from page list API before enrichment */
  imageCount?: number
  /** Available from page list API before enrichment */
  xmlFileCount?: number
  indexingStatus?: PageIndexingStatus
  annotationContext?: AnnotationApiContext
}

export interface GlobalSettings {
  constrainToImage: boolean
  constrainToParent: boolean
  autoSelect: boolean
  /** Show a light label-colored fill behind region and textline polygons. */
  showPolygonLabelFill: boolean
  /** When creating regions/textlines, subtract overlaps from visible existing polygons. */
  preventOverlapOnCreate: boolean
  /** Minimum polygon area threshold for cut operations. Polygons below this area are auto-deleted. Default: 0.0001 (0.01% of normalized space) */
  cutMinAreaThreshold: number
  /** When moving elements, also move all children (textlines, baselines) */
  moveWithChildren: boolean
  /** Default line width preset for polygon and polyline outlines */
  defaultLineWidth: LineWidthPreset
}

export interface ReadingOrderOverlaySettings {
  /** Whether to show the reading order overlay */
  visible: boolean
  /** Show arrows between consecutive reading order items */
  showArrows: boolean
  /** Show dashed bounding boxes around groups */
  showGroupBounds: boolean
  /** Show order numbers at region centroids */
  showOrderNumbers: boolean
  /** Show ALL regions including nested ones (not just top-level) */
  showAllRegions: boolean
  /** Show labels next to order numbers and group bounds */
  showLabels: boolean
}

export interface RelationsOverlaySettings {
  visible: boolean
  showLabels: boolean
}

export interface CommentsOverlaySettings {
  visible: boolean
}

export interface RelationsEditorState {
  pickerMode: RelationPickerMode
  selectedRelationId: string | null
  pickerRegionId: string | null
  draft: RelationDraftState
}

export interface CanvasState {
  id: string
  projectId: string | null
  pageId: string | null
  imageVariantId: string | null
  imageSrc: string | null
  selectedRegionId: string | null
  selectedBaselineId: string | null
  drawingMode: string
  regionType: string
  hoveredRegionId: string | null
  hoveredBaselineId: string | null
  hoveredNodeIndex: number
  imageSize?: { width: number, height: number }
  /** Whether annotations are currently being loaded for this canvas */
  isLoadingAnnotations?: boolean
  /** The XML file ID that was loaded for this canvas (for saving back) */
  xmlFileId?: string
  /** Context for annotation API endpoints for this canvas */
  annotationContext?: AnnotationApiContext | null
  /** Whether annotations are currently being saved */
  isSavingAnnotations?: boolean
  /** Whether the annotations have unsaved changes */
  hasUnsavedChanges?: boolean
  /** Command history index at last save/load baseline */
  historyBaselineIndex?: number
  /** Current command history index */
  historyCurrentIndex?: number
  /** Read-only comparison metadata for transient version/action-result canvases */
  comparison?: PageComparisonCanvasState
  /** Per-element visual diff treatment for read-only comparison canvases */
  diffHighlights?: Record<string, PageComparisonDiffHighlight>
}
