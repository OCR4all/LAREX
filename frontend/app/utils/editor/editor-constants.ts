/**
 * Constants used across the editor for hit detection, rendering, and interaction.
 * Centralizing these values makes them easier to tune and maintain.
 */

/**
 * Hit detection thresholds (in world coordinates, -1 to 1)
 */
export const HIT_DETECTION = {
  /** Threshold for detecting node clicks/hovers */
  NODE_THRESHOLD: 0.005,

  /** Threshold for detecting edge/segment clicks for insertion */
  EDGE_THRESHOLD: 0.008,

  /** Threshold for baseline segment detection */
  BASELINE_SEGMENT_THRESHOLD: 0.02
} as const

/**
 * WebGL rendering sizes (in pixels)
 */
export const RENDER_SIZES = {
  /** Default point/node size for polygons */
  POLYGON_POINT_SIZE: 20.0,

  /** Point size for selected polygon nodes */
  SELECTED_POINT_SIZE: 25.0,

  /** Point size for dragged nodes */
  DRAGGED_POINT_SIZE: 30.0,

  /** Point size used for small preview markers */
  PREVIEW_POINT_SIZE: 10.0
} as const

/** Screen-space shadow tuning for raster images rendered in the editor canvas */
export const RASTER_IMAGE_SHADOW = {
  OFFSET_X: 22.0,
  OFFSET_Y: 34.0,
  BLUR_X: 34.0,
  BLUR_Y: 34.0,
  ALPHA: 0.32,
  MAX_RELATIVE_BLUR: 0.14,
  MAX_RELATIVE_OFFSET: 0.12
} as const

export type RGBA = readonly [number, number, number, number]

/** Line width presets (in pixels) - similar to CSS font-weight */
export const LINE_WIDTH_PRESETS = {
  thin: 2.0,
  light: 3.0,
  normal: 5.0,
  medium: 6.0,
  bold: 8.0,
  extraBold: 10.0
} as const

/** Line thickness values (in pixels) used by WebGL line rendering */
export const RENDER_THICKNESS = {
  /** Standard polygon outline thickness */
  POLYGON_OUTLINE: 5.0,

  /** Outline thickness for multi-selected polygons */
  POLYGON_OUTLINE_MULTI_SELECTED: 7.0,

  /** Standard polyline thickness */
  POLYLINE: 5.2,

  /** Thickness for polyline preview segment */
  POLYLINE_PREVIEW: 3.9,

  /** Thickness for hovered polyline highlight */
  POLYLINE_HOVER: 7.8,

  /** Thickness for generic preview outlines (e.g., current polygon, rectangle preview) */
  PREVIEW_OUTLINE: 5.0
} as const

/** Common alpha values used by renderer feedback/preview elements */
export const RENDER_ALPHA = {
  /** Alpha used for semi-transparent hover highlight */
  HOVER: 0.7,

  /** Alpha used for polygon/rectangle preview lines */
  PREVIEW_LINE: 0.8,

  /** Alpha used for selection overlay maximum opacity */
  SELECTION_OVERLAY_MAX: 0.7,

  /** Alpha used for selection overlay minimum opacity */
  SELECTION_OVERLAY_MIN: 0.4,

  /** Alpha for hover fill overlays */
  FILL_HOVER: 0.5,

  /** Alpha for optional label-colored background fills on regions and textlines */
  FILL_LABEL_BACKGROUND: 0.14,

  /** Alpha for invalid fill overlays */
  FILL_INVALID: 0.3,

  /** Alpha for multi-selected polygon fills (slightly more opaque than hover) */
  FILL_MULTI_SELECTED: 0.4
} as const

/** Fixed UI colors for interaction feedback (RGBA in 0..1) */
export const RENDER_COLORS = {
  OVERLAY_BLACK: [0.0, 0.0, 0.0, 1.0] as RGBA,

  INVALID_RED: [1.0, 0.2, 0.2, 1.0] as RGBA,
  LABEL_CONFLICT_RED: [0.95, 0.08, 0.12, 1.0] as RGBA,
  LABEL_CONFLICT_FILL: [0.95, 0.08, 0.12, 0.14] as RGBA,
  LABEL_CONFLICT_STRIPE: [1.0, 0.16, 0.2, 0.48] as RGBA,
  ACTIVE_YELLOW_POLYLINE: [1.0, 0.8, 0.2, 1.0] as RGBA,
  ACTIVE_YELLOW_POLYGON: [1.0, 0.8, 0.1, 1.0] as RGBA,
  SELECTED_BLUE: [0.2, 0.6, 1.0, 1.0] as RGBA,
  PREVIEW_PINK: [1.0, 0.4, 0.8, 1.0] as RGBA,

  HOVER_FILL_YELLOW: [1.0, 0.8, 0.1, 0.5] as RGBA,
  INVALID_FILL_RED: [1.0, 0.2, 0.2, 0.3] as RGBA,

  RECTANGLE_PREVIEW_YELLOW: [1.0, 0.8, 0.1, 0.8] as RGBA,
  POLYLINE_HOVER_YELLOW: [1.0, 0.8, 0.2, 0.7] as RGBA,
  POLYLINE_PREVIEW_INVALID_RED: [1.0, 0.2, 0.2, 0.7] as RGBA,
  POLYLINE_PREVIEW_YELLOW: [1.0, 0.8, 0.2, 0.7] as RGBA,
  POLYGON_PREVIEW_INVALID_RED: [1.0, 0.2, 0.2, 0.8] as RGBA,
  POLYGON_PREVIEW_YELLOW: [1.0, 0.8, 0.1, 0.8] as RGBA,

  CUT_PREVIEW_RED: [0.9, 0.2, 0.1, 1.0] as RGBA,
  CUT_PREVIEW_OUTLINE: [0.9, 0.3, 0.1, 0.9] as RGBA,
  CUT_PREVIEW_FILL: [0.9, 0.2, 0.1, 0.25] as RGBA,
  CUT_PREVIEW_INVALID: [0.5, 0.1, 0.1, 0.8] as RGBA
} as const

/**
 * Auto-parent indicator colors (for Textline/Baseline creation in view modes)
 * These provide visual feedback about parent assignment during drawing.
 */
export const AUTO_PARENT_INDICATOR = {
  /** Fill color for existing parent polygon (subtle green tint) */
  EXISTING_PARENT_FILL: [0.2, 0.7, 0.3, 0.15] as RGBA,

  /** Outline color for existing parent polygon (green) */
  EXISTING_PARENT_OUTLINE: [0.2, 0.7, 0.3, 0.6] as RGBA,

  /** Outline color for helper shape to be created (blue dashed) */
  NEW_PARENT_OUTLINE: [0.3, 0.5, 0.9, 0.6] as RGBA,

  /** Fill color for helper shape preview (subtle blue tint) */
  NEW_PARENT_FILL: [0.3, 0.5, 0.9, 0.1] as RGBA,

  /** Outline color for helper textline in baseline mode (cyan dashed) */
  NEW_TEXTLINE_OUTLINE: [0.2, 0.7, 0.8, 0.6] as RGBA,

  /** Line thickness for parent indicator outlines */
  LINE_THICKNESS: 4.0,

  /** Dash length for new parent preview */
  DASH_LENGTH: 12.0,

  /** Gap length for new parent preview */
  GAP_LENGTH: 8.0
} as const

export const RENDER_COLOR_TUNING = {
  HOVER_BRIGHTEN_MULTIPLIER: 1.3,
  CHANNEL_MAX: 1.0
} as const

/**
 * Background element rendering settings (for non-selectable context elements in view modes)
 * These are used to render Regions in Textline mode and Regions+Textlines in Baseline mode
 * as visual context that helps users understand the annotation structure.
 */
export const BACKGROUND_ELEMENT = {
  /** Length of dashed line segments in pixels */
  DASH_LENGTH: 10.0,

  /** Gap between dashed line segments in pixels */
  GAP_LENGTH: 6.0,

  /** Alpha (opacity) for background element outlines (0.0-1.0) */
  LINE_ALPHA: 0.5,

  /** Line thickness for background elements (slightly thinner than regular outlines) */
  LINE_THICKNESS: 3.5
} as const

/**
 * Zoom constraints
 */
export const ZOOM = {
  /** Minimum zoom level */
  MIN: 0.1,

  /** Maximum zoom level */
  MAX: 20
} as const

/**
 * View padding when focusing on elements (as fraction of viewport)
 */
export const VIEW_PADDING = {
  /** Padding around polygons when zooming to fit (40%) */
  POLYGON: 0.4,

  /** Padding around baselines when zooming to fit (70% for more context) */
  BASELINE: 0.7,

  /** Minimum size in pixels for zoom-to-fit calculations */
  MIN_SIZE_PIXELS: 200
} as const

/**
 * Timing constants (in milliseconds)
 */
export const TIMING = {
  /** Delay after drag completion before allowing clicks */
  DRAG_COMPLETION_DELAY: 50,

  /** Click timeout for double-click detection */
  CLICK_TIMEOUT: 200,

  /** Debounce delay for render queue (30 FPS) */
  RENDER_DEBOUNCE: 32,

  /** Target frame time for 60 FPS */
  TARGET_FRAME_TIME: 16
} as const

/**
 * Background color for canvas (RGBA in 0-255 range)
 */
export const CANVAS_BACKGROUND = [0, 0, 0, 0] as const
