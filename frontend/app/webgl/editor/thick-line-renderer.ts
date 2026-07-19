import { createProgram } from './core'
import { UniformStateCache } from './uniform-state-cache'
import type { Scale } from '@/utils/editor/webgl-utils'
import { normalize } from '@/utils/editor/geometry-utils'
import type { Point, View } from '@/models/editor'
import { WEBGL_BUFFER_LAYOUT, WEBGL_DEFAULTS, WEBGL_EPSILON, WEBGL_GLSL, WEBGL_LINE_GEOMETRY } from '@/webgl/editor/webgl-constants'
import { glslFloatLiteral } from '@/webgl/editor/glsl-utils'

export interface LineData {
  points: Point[]
  color: readonly number[]
  thickness: number
  isClosed: boolean
}

interface ThickLineVertices {
  positions: number[]
  normals: number[]
}

interface CachedLineGeometry {
  cacheKey: string
  vao: WebGLVertexArrayObject
  positionBuffer: WebGLBuffer
  normalBuffer: WebGLBuffer
  vertexCount: number
}

interface ThickLineUniforms {
  scale: WebGLUniformLocation | null
  offset: WebGLUniformLocation | null
  zoom: WebGLUniformLocation | null
  rotation: WebGLUniformLocation | null
  canvasAspect: WebGLUniformLocation | null
  color: WebGLUniformLocation | null
  thickness: WebGLUniformLocation | null
  resolution: WebGLUniformLocation | null
}

/**
 * Thick line renderer for WebGL - renders lines with configurable thickness
 */
export class ThickLineRenderer {
  private gl: WebGL2RenderingContext
  private uniformState: UniformStateCache
  private program: WebGLProgram | null = null
  private vao: WebGLVertexArrayObject | null = null
  private positionBuffer: WebGLBuffer | null = null
  private normalBuffer: WebGLBuffer | null = null
  private positionAttribute = -1
  private normalAttribute = -1
  private uniforms: ThickLineUniforms | null = null
  private geometryCache = new Map<string, CachedLineGeometry>()
  private initialized = false

  constructor(gl: WebGL2RenderingContext, uniformState = new UniformStateCache(gl)) {
    this.gl = gl
    this.uniformState = uniformState
  }

  /**
   * Initializes the thick line renderer
   */
  init(): void {
    if (this.initialized) return

    const vsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      in vec2 a_position;
      in vec2 a_normal;
      uniform vec2 u_scale;
      uniform vec2 u_offset;
      uniform float u_zoom;
      uniform vec2 u_rotation;
      uniform float u_canvasAspect;
      uniform float u_thickness;
      uniform vec2 u_resolution;

      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        vec2 scaled = pos * u_scale;
        vec2 clip = vec2(
          scaled.x * u_rotation.x - (scaled.y * u_rotation.y) / u_canvasAspect,
          scaled.x * u_rotation.y * u_canvasAspect + scaled.y * u_rotation.x
        );

        vec2 pixelSize = ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_PIXEL_SCALE)} / u_resolution;
        vec2 offset = a_normal * u_thickness * pixelSize * ${glslFloatLiteral(WEBGL_GLSL.HALF)};
        vec2 clipOffset = vec2(
          offset.x * u_rotation.x - (offset.y * u_rotation.y) / u_canvasAspect,
          offset.x * u_rotation.y * u_canvasAspect + offset.y * u_rotation.x
        );

        gl_Position = vec4(clip + clipOffset, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_Z)}, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_W)});
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
    this.cacheUniformLocations()
    this.initialized = true
  }

  private cacheUniformLocations(): void {
    if (!this.program) return
    this.uniforms = {
      scale: this.uniformState.getLocation(this.program, 'u_scale'),
      offset: this.uniformState.getLocation(this.program, 'u_offset'),
      zoom: this.uniformState.getLocation(this.program, 'u_zoom'),
      rotation: this.uniformState.getLocation(this.program, 'u_rotation'),
      canvasAspect: this.uniformState.getLocation(this.program, 'u_canvasAspect'),
      color: this.uniformState.getLocation(this.program, 'u_color'),
      thickness: this.uniformState.getLocation(this.program, 'u_thickness'),
      resolution: this.uniformState.getLocation(this.program, 'u_resolution')
    }
  }

  /**
   * Sets up vertex array and buffers
   */
  private setupBuffers(): void {
    if (!this.program) return

    this.vao = this.gl.createVertexArray()
    this.gl.bindVertexArray(this.vao)

    this.positionBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.positionAttribute = this.gl.getAttribLocation(this.program, 'a_position')
    this.gl.enableVertexAttribArray(this.positionAttribute)
    this.gl.vertexAttribPointer(
      this.positionAttribute,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.normalBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
    this.normalAttribute = this.gl.getAttribLocation(this.program, 'a_normal')
    this.gl.enableVertexAttribArray(this.normalAttribute)
    this.gl.vertexAttribPointer(
      this.normalAttribute,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.gl.bindVertexArray(null)
  }

  private setUniforms(
    color: readonly number[],
    thickness: number,
    aspectRatioScale: Scale,
    view: View
  ): void {
    const uniforms = this.uniforms
    if (!uniforms) return

    this.uniformState.uniform2f(uniforms.scale, aspectRatioScale.scaleX, aspectRatioScale.scaleY)
    this.uniformState.uniform2f(uniforms.offset, view.offsetX, view.offsetY)
    this.uniformState.uniform1f(uniforms.zoom, view.zoom)

    const rotationCos = Number.isFinite(aspectRatioScale.rotationCos) ? aspectRatioScale.rotationCos! : 1
    const rotationSin = Number.isFinite(aspectRatioScale.rotationSin) ? aspectRatioScale.rotationSin! : 0
    this.uniformState.uniform2f(uniforms.rotation, rotationCos, rotationSin)

    const fallbackAspect = this.gl.canvas.width > 0 && this.gl.canvas.height > 0
      ? this.gl.canvas.width / this.gl.canvas.height
      : 1
    const rotationAspect = Number.isFinite(aspectRatioScale.rotationAspect) && aspectRatioScale.rotationAspect! > 0
      ? aspectRatioScale.rotationAspect!
      : fallbackAspect
    this.uniformState.uniform1f(uniforms.canvasAspect, rotationAspect)

    this.uniformState.uniform4f(
      uniforms.color,
      color[0] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[1] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[2] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[3] ?? WEBGL_DEFAULTS.ALPHA_CHANNEL
    )
    this.uniformState.uniform1f(uniforms.thickness, thickness)
    this.uniformState.uniform2f(uniforms.resolution, this.gl.canvas.width, this.gl.canvas.height)
  }

  private deleteCachedGeometry(entry: CachedLineGeometry): void {
    this.gl.deleteVertexArray(entry.vao)
    this.gl.deleteBuffer(entry.positionBuffer)
    this.gl.deleteBuffer(entry.normalBuffer)
  }

  private getCachedGeometry(cacheKey: string, points: Point[], isClosed: boolean): CachedLineGeometry | null {
    const key = `${cacheKey}:${isClosed ? 'closed' : 'open'}`
    const cached = this.geometryCache.get(key)
    if (cached) return cached

    const { positions, normals } = this.generateThickLineVertices(points, isClosed)
    if (positions.length === 0) return null

    const vao = this.gl.createVertexArray()
    const positionBuffer = this.gl.createBuffer()
    const normalBuffer = this.gl.createBuffer()
    if (!vao || !positionBuffer || !normalBuffer) {
      if (vao) this.gl.deleteVertexArray(vao)
      if (positionBuffer) this.gl.deleteBuffer(positionBuffer)
      if (normalBuffer) this.gl.deleteBuffer(normalBuffer)
      return null
    }

    this.gl.bindVertexArray(vao)
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, positionBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.STATIC_DRAW)
    this.gl.enableVertexAttribArray(this.positionAttribute)
    this.gl.vertexAttribPointer(
      this.positionAttribute,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, normalBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.STATIC_DRAW)
    this.gl.enableVertexAttribArray(this.normalAttribute)
    this.gl.vertexAttribPointer(
      this.normalAttribute,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )
    this.gl.bindVertexArray(null)

    const entry = {
      cacheKey,
      vao,
      positionBuffer,
      normalBuffer,
      vertexCount: positions.length / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
    }
    this.geometryCache.set(key, entry)
    return entry
  }

  /**
   * Generates vertices and normals for thick line rendering
   * @param points - Array of points with x, y properties
   * @param isClosed - Whether the line should be closed (polygon)
   * @returns Object with positions and normals arrays
   */
  generateThickLineVertices(points: Point[], isClosed = false): ThickLineVertices {
    if (points.length < 2) {
      return { positions: [], normals: [] }
    }

    const positions: number[] = []
    const normals: number[] = []

    for (let i = 0; i < points.length - 1; i++) {
      const p1 = points[i]
      const p2 = points[i + 1]
      if (!p1 || !p2) continue

      const dx = p2.x - p1.x
      const dy = p2.y - p1.y
      const len = Math.sqrt(dx * dx + dy * dy)

      if (len < WEBGL_EPSILON.MIN_SEGMENT_LENGTH) continue // Skip zero-length segments

      const normal = normalize(-dy / len, dx / len)

      positions.push(p1.x, p1.y)
      normals.push(normal.x, normal.y)
      positions.push(p1.x, p1.y)
      normals.push(-normal.x, -normal.y)

      positions.push(p2.x, p2.y)
      normals.push(normal.x, normal.y)
      positions.push(p2.x, p2.y)
      normals.push(-normal.x, -normal.y)
    }

    if (isClosed && points.length > 2) {
      const p1 = points[points.length - 1]
      const p2 = points[0]
      if (!p1 || !p2) return { positions, normals }

      const dx = p2.x - p1.x
      const dy = p2.y - p1.y
      const len = Math.sqrt(dx * dx + dy * dy)

      if (len >= WEBGL_EPSILON.MIN_SEGMENT_LENGTH) {
        const normal = normalize(-dy / len, dx / len)

        positions.push(p1.x, p1.y)
        normals.push(normal.x, normal.y)
        positions.push(p1.x, p1.y)
        normals.push(-normal.x, -normal.y)

        positions.push(p2.x, p2.y)
        normals.push(normal.x, normal.y)
        positions.push(p2.x, p2.y)
        normals.push(-normal.x, -normal.y)
      }
    }

    return { positions, normals }
  }

  /**
   * Draws a thick line with the specified parameters
   * @param points - Array of points with x, y properties
   * @param color - Color array [r, g, b, a]
   * @param thickness - Line thickness in pixels
   * @param isClosed - Whether to close the line (for polygons)
   * @param aspectRatioScale - Scale object with scaleX, scaleY
   * @param view - View transformation with offsetX, offsetY, zoom
   */
  drawThickLine(
    points: Point[],
    color: readonly number[],
    thickness: number,
    isClosed: boolean,
    aspectRatioScale: Scale,
    view: View,
    cacheKey?: string
  ): void {
    if (!this.initialized || points.length < 2 || !this.program) return

    const cachedGeometry = cacheKey ? this.getCachedGeometry(cacheKey, points, isClosed) : null
    if (cachedGeometry) {
      this.gl.useProgram(this.program)
      this.gl.bindVertexArray(cachedGeometry.vao)
      this.setUniforms(color, thickness, aspectRatioScale, view)
      this.gl.drawArrays(this.gl.TRIANGLE_STRIP, 0, cachedGeometry.vertexCount)
      this.gl.bindVertexArray(null)
      return
    }

    const { positions, normals } = this.generateThickLineVertices(points, isClosed)

    if (positions.length === 0) return

    this.gl.useProgram(this.program)
    this.gl.bindVertexArray(this.vao)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.DYNAMIC_DRAW)

    this.setUniforms(color, thickness, aspectRatioScale, view)

    this.gl.drawArrays(
      this.gl.TRIANGLE_STRIP,
      0,
      positions.length / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
    )

    this.gl.bindVertexArray(null)
  }

  /**
   * Draws multiple thick lines with different colors
   * @param lineData - Array of line objects: { points, color, thickness, isClosed }
   * @param aspectRatioScale - Scale object with scaleX, scaleY
   * @param view - View transformation with offsetX, offsetY, zoom
   */
  drawMultipleThickLines(lineData: LineData[], aspectRatioScale: Scale, view: View): void {
    if (!this.initialized || !this.program) return

    this.gl.useProgram(this.program)
    this.gl.bindVertexArray(this.vao)

    for (const line of lineData) {
      const { positions, normals } = this.generateThickLineVertices(line.points, line.isClosed)

      if (positions.length === 0) continue

      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
      this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.DYNAMIC_DRAW)

      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
      this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.DYNAMIC_DRAW)

      this.setUniforms(line.color, line.thickness, aspectRatioScale, view)

      this.gl.drawArrays(
        this.gl.TRIANGLE_STRIP,
        0,
        positions.length / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
      )
    }

    this.gl.bindVertexArray(null)
  }

  invalidateGeometry(cacheKey: string): void {
    for (const [key, entry] of this.geometryCache) {
      if (entry.cacheKey === cacheKey) {
        this.deleteCachedGeometry(entry)
        this.geometryCache.delete(key)
      }
    }
  }

  pruneGeometryCache(activeKeys: Set<string>): void {
    for (const [key, entry] of this.geometryCache) {
      if (!activeKeys.has(entry.cacheKey)) {
        this.deleteCachedGeometry(entry)
        this.geometryCache.delete(key)
      }
    }
  }

  clearGeometryCache(): void {
    for (const entry of this.geometryCache.values()) {
      this.deleteCachedGeometry(entry)
    }
    this.geometryCache.clear()
  }

  /**
   * Cleanup function to release resources
   */
  cleanup(): void {
    this.clearGeometryCache()
    if (this.positionBuffer) {
      this.gl.deleteBuffer(this.positionBuffer)
      this.positionBuffer = null
    }

    if (this.normalBuffer) {
      this.gl.deleteBuffer(this.normalBuffer)
      this.normalBuffer = null
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
    this.uniforms = null
  }
}
