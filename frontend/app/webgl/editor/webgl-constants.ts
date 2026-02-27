/**
 * WebGL-specific constants.
 *
 * This module is intentionally import-free to avoid circular dependencies.
 * Keep low-level rendering layout/perf/shader constants here.
 */

export const WEBGL_GLSL = {
  VERSION: 300,
  ES_SUFFIX: 'es',

  CLIPSPACE_Z: 0.0,
  CLIPSPACE_W: 1.0,

  /** Converts pixel units to clip space: 2.0 / resolution */
  CLIPSPACE_PIXEL_SCALE: 2.0,
  HALF: 0.5
} as const

export const WEBGL_BUFFER_LAYOUT = {
  FLOAT_BYTES: 4,

  VEC2_COMPONENTS: 2,
  VEC4_COMPONENTS: 4,

  /** Stride in bytes for a packed vec2 Float32Array */
  VEC2_STRIDE_BYTES: 8,

  NO_STRIDE_BYTES: 0,
  NO_OFFSET_BYTES: 0
} as const

export const WEBGL_DRAW_COUNTS = {
  FULLSCREEN_TRIANGLE_FAN_VERTICES: 4
} as const

export const WEBGL_STENCIL = {
  MASK_ALL_BITS: 0xFF,

  /** Stencil reference for "outer" mask */
  REF_OUTER: 1,

  /** Stencil reference for "inner" cutout mask */
  REF_INNER: 2,

  REF_CLEAR: 0
} as const

export const WEBGL_BATCH = {
  /** Max number of line segments to buffer before a batch flush */
  LINE_MAX_SEGMENTS: 5000,

  /** Auto-flush threshold (number of lines queued) */
  LINE_AUTO_FLUSH_LINES: 100,

  /** Max number of fill vertices to buffer before a batch flush */
  FILL_MAX_VERTICES: 10000
} as const

export const WEBGL_LINE_GEOMETRY = {
  /** Thick-line quad vertices per line segment */
  VERTICES_PER_SEGMENT: 4,

  POSITION_COMPONENTS: 2,
  NORMAL_COMPONENTS: 2,
  COLOR_COMPONENTS: 4
} as const

export const WEBGL_EPSILON = {
  /** Skip segments shorter than this length */
  MIN_SEGMENT_LENGTH: 0.0001
} as const

export const WEBGL_TEXTURE = {
  RESERVED_MAIN_SLOT: 0,
  FIRST_DYNAMIC_SLOT: 1,
  SLOT_NOT_FOUND: -1,
  BASE_MIP_LEVEL: 0
} as const

export const WEBGL_PROGRAM = {
  ATTRIBUTE_NOT_FOUND: -1
} as const

export const WEBGL_DEFAULTS = {
  COLOR_CHANNEL: 0,
  ALPHA_CHANNEL: 1
} as const

export const WEBGL_CACHE = {
  /** Point sampling cutoff used when hashing long point lists */
  HASH_SAMPLE_LIMIT: 10,

  /** Divisor used when selecting the midpoint sample */
  HASH_MIDPOINT_DIVISOR: 2,

  /** Scale used to map float coords to integer space in hashing */
  HASH_COORD_SCALE: 1_000_000,

  /** FNV-1a 32-bit prime */
  FNV_PRIME: 0x01000193,

  /** Unsigned shift amount for forcing unsigned 32-bit */
  UNSIGNED_SHIFT: 0
} as const

export const WEBGL_RESOURCE_POOL = {
  /** Growth factor for typed array cache reallocation */
  TYPED_ARRAY_GROWTH_FACTOR: 1.5
} as const

export const WEBGL_CORE = {
  VIEWPORT_ORIGIN_X: 0,
  VIEWPORT_ORIGIN_Y: 0,
  DEFAULT_DEVICE_PIXEL_RATIO: 1,
  DEFAULT_CLEAR_COLOR: [0.0, 0.0, 0.0, 0.0] as const
} as const

export const WEBGL_GEOMETRY = {
  MIN_POLYGON_POINTS: 3,
  MIN_TRIANGLE_INDEX_COUNT: 3,
  MIN_RECTANGLE_POINTS: 4,

  /** Two-triangle indices for a rectangle with 4 points */
  RECTANGLE_TRIANGLE_INDICES: [0, 1, 3, 1, 2, 3] as const
} as const

export const WEBGL_FILL_GEOMETRY = {
  /** XY components per vertex in fill buffers */
  POSITION_COMPONENTS: 2,
  /** RGBA components per vertex in fill buffers */
  COLOR_COMPONENTS: 4,
  /** Conservative multiplier for index buffer preallocation */
  INDICES_MULTIPLIER: 3
} as const
