import { createProgram } from './core'
import {
  setTransformUniforms,
  setColorUniform,
  enableBlending,
  disableBlending,
  setupStencilBuffer,
  disableStencilBuffer
} from '@/utils/editor/webgl-utils'
import type { Scale } from '@/utils/editor/webgl-utils'
import { createFullscreenRect, triangulateToVertices } from '@/utils/editor/geometry-utils'
import type { Point, View } from '@/models/editor'
import { calculateOpacityForLevel } from '@/utils/editor/visibility-utils'
import { visibilityService } from '@/services/editor/visibility-service'
import type { Polygon as VisibilityPolygon } from '@/services/editor/visibility-service'
import { RENDER_ALPHA, RENDER_COLORS } from '@/utils/editor/editor-constants'
import { WEBGL_BUFFER_LAYOUT, WEBGL_DEFAULTS, WEBGL_DRAW_COUNTS, WEBGL_GLSL, WEBGL_GEOMETRY, WEBGL_STENCIL } from '@/webgl/editor/webgl-constants'
import { glslFloatLiteral } from '@/webgl/editor/glsl-utils'

export interface Polygon {
  id: string
  points: Point[]
  parentId?: string
}

export interface SelectedPolygonIndex {
  value: number
}

/**
 * Selection overlay system for hierarchical selection visualization
 */
export class SelectionOverlayRenderer {
  private gl: WebGL2RenderingContext
  private program: WebGLProgram | null = null
  private vao: WebGLVertexArrayObject | null = null
  private buffer: WebGLBuffer | null = null
  private initialized = false

  constructor(gl: WebGL2RenderingContext) {
    this.gl = gl
  }

  /**
   * Initializes the selection overlay renderer
   */
  init(): void {
    if (this.initialized) return

    const vsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      in vec2 a_position;
      uniform vec2 u_scale;
      uniform vec2 u_offset;
      uniform float u_zoom;
      uniform bool u_skipTransform;

      void main() {
        vec2 pos = a_position;
        if (!u_skipTransform) {
          pos = (a_position * u_zoom) + u_offset;
          pos *= u_scale;
        }
        gl_Position = vec4(pos, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_Z)}, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_W)});
      }`

    const fsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      precision mediump float;
      uniform vec4 u_color;
      out vec4 outColor;

      void main() {
        outColor = u_color;
      }`

    this.program = createProgram(this.gl, vsSource, fsSource)
    this.setupBuffers()
    this.initialized = true
  }

  /**
   * Sets up vertex array and buffer for overlay rendering
   */
  private setupBuffers(): void {
    if (!this.program) return

    this.vao = this.gl.createVertexArray()
    this.gl.bindVertexArray(this.vao)

    this.buffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffer)

    const aPosFill = this.gl.getAttribLocation(this.program, 'a_position')
    this.gl.enableVertexAttribArray(aPosFill)
    this.gl.vertexAttribPointer(
      aPosFill,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.VEC2_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.gl.bindVertexArray(null)
  }

  /**
   * Renders multi-level selection overlay for hierarchical polygons
   * @param polygons - Array of polygon objects
   * @param selectedPolygonIndex - Ref containing selected polygon index
   * @param aspectRatioScale - Scale object with scaleX, scaleY
   * @param view - View transformation with offsetX, offsetY, zoom
   * @param triangulatePolygon - Function to triangulate polygon points
   */
  renderSelectionOverlay(
    polygons: Polygon[],
    selectedPolygonIndex: SelectedPolygonIndex,
    aspectRatioScale: Scale | { value: Scale },
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (selectedPolygonIndex.value < 0 || selectedPolygonIndex.value >= polygons.length) return
    if (!this.program) return

    const hierarchyChain = visibilityService.getHierarchyChain(polygons as VisibilityPolygon[], selectedPolygonIndex.value)
    const maxDepth = visibilityService.getMaxHierarchyDepth(polygons as VisibilityPolygon[], selectedPolygonIndex.value)

    if (hierarchyChain.length === 0) return

    enableBlending(this.gl)

    setupStencilBuffer(this.gl)

    this.gl.clear(this.gl.STENCIL_BUFFER_BIT)

    this.gl.useProgram(this.program)
    this.gl.bindVertexArray(this.vao)

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    setTransformUniforms(this.gl, this.program, scale, view, view.zoom)

    this.gl.uniform1i(this.gl.getUniformLocation(this.program, 'u_skipTransform'), 0)

    const fullscreenRect = createFullscreenRect()
    const fullscreenVertices = new Float32Array(fullscreenRect.flatMap(p => [p.x, p.y]))

    const rootIndex = hierarchyChain[0]
    if (rootIndex !== undefined) {
      const rootPolygon = polygons[rootIndex]
      if (rootPolygon && rootPolygon.points.length >= WEBGL_GEOMETRY.MIN_POLYGON_POINTS) {
        this.renderRootRing(rootPolygon, fullscreenVertices, triangulatePolygon, 0, maxDepth)
      }
    }

    for (let levelIndex = 1; levelIndex < hierarchyChain.length; levelIndex++) {
      const polyIndex = hierarchyChain[levelIndex]
      const parentIndex = hierarchyChain[levelIndex - 1]
      if (polyIndex === undefined || parentIndex === undefined) continue

      const polygon = polygons[polyIndex]
      if (!polygon || polygon.points.length < WEBGL_GEOMETRY.MIN_POLYGON_POINTS) continue

      const parentPolygon = polygons[parentIndex]
      if (parentPolygon && parentPolygon.points.length >= WEBGL_GEOMETRY.MIN_POLYGON_POINTS) {
        this.renderRing(polygon, parentPolygon, fullscreenVertices, triangulatePolygon, levelIndex, maxDepth)
      }
    }

    this.gl.colorMask(true, true, true, true)
    this.gl.stencilFunc(this.gl.ALWAYS, WEBGL_STENCIL.REF_CLEAR, WEBGL_STENCIL.MASK_ALL_BITS)
    this.gl.stencilOp(this.gl.KEEP, this.gl.KEEP, this.gl.KEEP)
    disableStencilBuffer(this.gl)
    disableBlending(this.gl)
  }

  /**
   * Renders the outermost ring (outside root polygon)
   * @param rootPolygon - The root polygon
   * @param fullscreenVertices - Vertices for fullscreen rectangle
   * @param triangulatePolygon - Function to triangulate polygon points
   * @param level - Current hierarchy level
   * @param maxDepth - Maximum hierarchy depth
   */
  private renderRootRing(
    rootPolygon: Polygon,
    fullscreenVertices: Float32Array,
    triangulatePolygon: (points: Point[]) => number[],
    level: number,
    maxDepth: number
  ): void {
    if (!this.program || !this.buffer) return

    this.gl.clear(this.gl.STENCIL_BUFFER_BIT)
    this.gl.stencilFunc(this.gl.ALWAYS, WEBGL_STENCIL.REF_OUTER, WEBGL_STENCIL.MASK_ALL_BITS)
    this.gl.stencilOp(this.gl.KEEP, this.gl.KEEP, this.gl.REPLACE)
    this.gl.colorMask(false, false, false, false)

    const triangleIndices = triangulatePolygon(rootPolygon.points)
    if (triangleIndices.length >= WEBGL_GEOMETRY.MIN_TRIANGLE_INDEX_COUNT) {
      const fillVertices = triangulateToVertices(rootPolygon.points, triangleIndices)
      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffer)
      this.gl.bufferData(this.gl.ARRAY_BUFFER, fillVertices, this.gl.DYNAMIC_DRAW)
      this.gl.drawArrays(this.gl.TRIANGLES, 0, fillVertices.length / WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS)
    }

    this.gl.colorMask(true, true, true, true)
    this.gl.stencilFunc(this.gl.EQUAL, WEBGL_STENCIL.REF_CLEAR, WEBGL_STENCIL.MASK_ALL_BITS)
    this.gl.stencilOp(this.gl.KEEP, this.gl.KEEP, this.gl.KEEP)

    const rootOpacity = calculateOpacityForLevel(level, maxDepth, RENDER_ALPHA.SELECTION_OVERLAY_MAX, RENDER_ALPHA.SELECTION_OVERLAY_MIN)
    const overlayBlack = RENDER_COLORS.OVERLAY_BLACK
    setColorUniform(this.gl, this.program, [overlayBlack[0], overlayBlack[1], overlayBlack[2], rootOpacity])

    this.gl.uniform1i(this.gl.getUniformLocation(this.program, 'u_skipTransform'), 1)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, fullscreenVertices, this.gl.DYNAMIC_DRAW)
    this.gl.drawArrays(this.gl.TRIANGLE_FAN, 0, WEBGL_DRAW_COUNTS.FULLSCREEN_TRIANGLE_FAN_VERTICES)

    this.gl.uniform1i(this.gl.getUniformLocation(this.program, 'u_skipTransform'), 0)
  }

  /**
   * Renders a ring between a parent and child polygon
   * @param polygon - The child (inner) polygon
   * @param parentPolygon - The parent (outer) polygon
   * @param fullscreenVertices - Vertices for fullscreen rectangle
   * @param triangulatePolygon - Function to triangulate polygon points
   * @param levelIndex - Current level index
   * @param maxDepth - Maximum hierarchy depth
   */
  private renderRing(
    polygon: Polygon,
    parentPolygon: Polygon,
    fullscreenVertices: Float32Array,
    triangulatePolygon: (points: Point[]) => number[],
    levelIndex: number,
    maxDepth: number
  ): void {
    if (!this.program || !this.buffer) return

    this.gl.clear(this.gl.STENCIL_BUFFER_BIT)

    this.gl.stencilFunc(this.gl.ALWAYS, WEBGL_STENCIL.REF_OUTER, WEBGL_STENCIL.MASK_ALL_BITS)
    this.gl.stencilOp(this.gl.KEEP, this.gl.KEEP, this.gl.REPLACE)
    this.gl.colorMask(false, false, false, false)

    const parentTriangles = triangulatePolygon(parentPolygon.points)
    if (parentTriangles.length >= WEBGL_GEOMETRY.MIN_TRIANGLE_INDEX_COUNT) {
      const parentVertices = triangulateToVertices(parentPolygon.points, parentTriangles)
      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffer)
      this.gl.bufferData(this.gl.ARRAY_BUFFER, parentVertices, this.gl.DYNAMIC_DRAW)
      this.gl.drawArrays(this.gl.TRIANGLES, 0, parentVertices.length / WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS)
    }

    this.gl.stencilFunc(this.gl.ALWAYS, WEBGL_STENCIL.REF_INNER, WEBGL_STENCIL.MASK_ALL_BITS)
    this.gl.stencilOp(this.gl.KEEP, this.gl.KEEP, this.gl.REPLACE)

    const triangleIndices = triangulatePolygon(polygon.points)
    if (triangleIndices.length >= WEBGL_GEOMETRY.MIN_TRIANGLE_INDEX_COUNT) {
      const fillVertices = triangulateToVertices(polygon.points, triangleIndices)
      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffer)
      this.gl.bufferData(this.gl.ARRAY_BUFFER, fillVertices, this.gl.DYNAMIC_DRAW)
      this.gl.drawArrays(this.gl.TRIANGLES, 0, fillVertices.length / WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS)
    }

    this.gl.colorMask(true, true, true, true)
    this.gl.stencilFunc(this.gl.EQUAL, WEBGL_STENCIL.REF_OUTER, WEBGL_STENCIL.MASK_ALL_BITS)
    this.gl.stencilOp(this.gl.KEEP, this.gl.KEEP, this.gl.KEEP)

    const ringOpacity = calculateOpacityForLevel(levelIndex, maxDepth, RENDER_ALPHA.SELECTION_OVERLAY_MAX, RENDER_ALPHA.SELECTION_OVERLAY_MIN)
    const overlayBlack = RENDER_COLORS.OVERLAY_BLACK
    setColorUniform(this.gl, this.program, [overlayBlack[0], overlayBlack[1], overlayBlack[2], ringOpacity])

    this.gl.uniform1i(this.gl.getUniformLocation(this.program, 'u_skipTransform'), 1)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, fullscreenVertices, this.gl.DYNAMIC_DRAW)
    this.gl.drawArrays(this.gl.TRIANGLE_FAN, 0, WEBGL_DRAW_COUNTS.FULLSCREEN_TRIANGLE_FAN_VERTICES)

    this.gl.uniform1i(this.gl.getUniformLocation(this.program, 'u_skipTransform'), 0)
  }

  /**
   * Renders a simple single-level overlay (for backward compatibility)
   * @param polygon - The polygon to overlay
   * @param color - Overlay color [r, g, b, a]
   * @param aspectRatioScale - Scale object with scaleX, scaleY
   * @param view - View transformation with offsetX, offsetY, zoom
   * @param triangulatePolygon - Function to triangulate polygon points
   */
  renderSimpleOverlay(
    polygon: Polygon,
    color: number[],
    aspectRatioScale: Scale | { value: Scale },
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!polygon || polygon.points.length < WEBGL_GEOMETRY.MIN_POLYGON_POINTS || !this.program || !this.buffer) return

    enableBlending(this.gl)

    this.gl.useProgram(this.program)
    this.gl.bindVertexArray(this.vao)
    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    setTransformUniforms(this.gl, this.program, scale, view, view.zoom)

    this.gl.uniform1i(this.gl.getUniformLocation(this.program, 'u_skipTransform'), 0)
    const rgba: [number, number, number, number] = [
      color[0] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[1] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[2] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[3] ?? WEBGL_DEFAULTS.ALPHA_CHANNEL
    ]
    setColorUniform(this.gl, this.program, rgba)

    const triangleIndices = triangulatePolygon(polygon.points)
    if (triangleIndices.length >= WEBGL_GEOMETRY.MIN_TRIANGLE_INDEX_COUNT) {
      const fillVertices = triangulateToVertices(polygon.points, triangleIndices)
      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffer)
      this.gl.bufferData(this.gl.ARRAY_BUFFER, fillVertices, this.gl.DYNAMIC_DRAW)
      this.gl.drawArrays(this.gl.TRIANGLES, 0, fillVertices.length / WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS)
    }

    this.gl.bindVertexArray(null)
    disableBlending(this.gl)
  }

  /**
   * Cleanup function to release resources
   */
  cleanup(): void {
    if (this.buffer) {
      this.gl.deleteBuffer(this.buffer)
      this.buffer = null
    }

    if (this.vao) {
      this.gl.deleteVertexArray(this.vao)
      this.vao = null
    }

    if (this.program) {
      this.gl.deleteProgram(this.program)
      this.program = null
    }

    this.initialized = false
  }
}
