import { createProgram } from './core'
import { UniformStateCache } from './uniform-state-cache'
import type { Scale } from '@/utils/editor/webgl-utils'
import { normalize } from '@/utils/editor/geometry-utils'
import type { Point, View } from '@/models/editor'
import { WEBGL_BUFFER_LAYOUT, WEBGL_DEFAULTS, WEBGL_EPSILON, WEBGL_GLSL, WEBGL_LINE_GEOMETRY } from '@/webgl/editor/webgl-constants'
import { glslFloatLiteral } from '@/webgl/editor/glsl-utils'

export interface DashedLineData {
  points: Point[]
  color: readonly number[]
  thickness: number
  isClosed: boolean
}

interface DashedLineVertices {
  positions: number[]
  normals: number[]
  uvs: number[] // UV coordinates for dash pattern
}

interface CachedDashedGeometry {
  cacheKey: string
  vao: WebGLVertexArrayObject
  positionBuffer: WebGLBuffer
  normalBuffer: WebGLBuffer
  uvBuffer: WebGLBuffer
  vertexCount: number
  zoom: number
}

interface DashedLineUniforms {
  scale: WebGLUniformLocation | null
  offset: WebGLUniformLocation | null
  zoom: WebGLUniformLocation | null
  rotation: WebGLUniformLocation | null
  canvasAspect: WebGLUniformLocation | null
  color: WebGLUniformLocation | null
  thickness: WebGLUniformLocation | null
  resolution: WebGLUniformLocation | null
  dashLength: WebGLUniformLocation | null
  gapLength: WebGLUniformLocation | null
}

/**
 * Dashed line renderer for WebGL - renders lines with configurable dash pattern
 * Used for rendering non-selectable background elements in view modes.
 */
export class DashedLineRenderer {
  private gl: WebGL2RenderingContext
  private uniformState: UniformStateCache
  private program: WebGLProgram | null = null
  private vao: WebGLVertexArrayObject | null = null
  private positionBuffer: WebGLBuffer | null = null
  private normalBuffer: WebGLBuffer | null = null
  private uvBuffer: WebGLBuffer | null = null
  private positionAttribute = -1
  private normalAttribute = -1
  private uvAttribute = -1
  private uniforms: DashedLineUniforms | null = null
  private geometryCache = new Map<string, CachedDashedGeometry>()
  private initialized = false

  constructor(gl: WebGL2RenderingContext, uniformState = new UniformStateCache(gl)) {
    this.gl = gl
    this.uniformState = uniformState
  }

  /**
   * Initializes the dashed line renderer
   */
  init(): void {
    if (this.initialized) return

    const vsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      in vec2 a_position;
      in vec2 a_normal;
      in float a_uv;
      
      uniform vec2 u_scale;
      uniform vec2 u_offset;
      uniform float u_zoom;
      uniform vec2 u_rotation;
      uniform float u_canvasAspect;
      uniform float u_thickness;
      uniform vec2 u_resolution;
      
      out float v_uv;

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
        v_uv = a_uv;
      }`

    const fsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      precision mediump float;
      
      uniform vec4 u_color;
      uniform float u_dashLength;
      uniform float u_gapLength;
      
      in float v_uv;
      out vec4 outColor;

      void main() {
        float patternLength = u_dashLength + u_gapLength;
        float patternPos = mod(v_uv, patternLength);
        
        if (patternPos > u_dashLength) {
          discard;
        }
        
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
      resolution: this.uniformState.getLocation(this.program, 'u_resolution'),
      dashLength: this.uniformState.getLocation(this.program, 'u_dashLength'),
      gapLength: this.uniformState.getLocation(this.program, 'u_gapLength')
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

    this.uvBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.uvBuffer)
    this.uvAttribute = this.gl.getAttribLocation(this.program, 'a_uv')
    this.gl.enableVertexAttribArray(this.uvAttribute)
    this.gl.vertexAttribPointer(
      this.uvAttribute,
      1, // Single float for UV
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
    dashLength: number,
    gapLength: number,
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
    this.uniformState.uniform1f(uniforms.dashLength, dashLength)
    this.uniformState.uniform1f(uniforms.gapLength, gapLength)
  }

  private deleteCachedGeometry(entry: CachedDashedGeometry): void {
    this.gl.deleteVertexArray(entry.vao)
    this.gl.deleteBuffer(entry.positionBuffer)
    this.gl.deleteBuffer(entry.normalBuffer)
    this.gl.deleteBuffer(entry.uvBuffer)
  }

  private getCachedGeometry(
    cacheKey: string,
    points: Point[],
    isClosed: boolean,
    zoom: number
  ): CachedDashedGeometry | null {
    const key = `${cacheKey}:${isClosed ? 'closed' : 'open'}`
    const cached = this.geometryCache.get(key)
    if (cached && cached.zoom === zoom) return cached
    if (cached) {
      this.deleteCachedGeometry(cached)
      this.geometryCache.delete(key)
    }

    const { positions, normals, uvs } = this.generateDashedLineVertices(points, isClosed, zoom)
    if (positions.length === 0) return null

    const vao = this.gl.createVertexArray()
    const positionBuffer = this.gl.createBuffer()
    const normalBuffer = this.gl.createBuffer()
    const uvBuffer = this.gl.createBuffer()
    if (!vao || !positionBuffer || !normalBuffer || !uvBuffer) {
      if (vao) this.gl.deleteVertexArray(vao)
      if (positionBuffer) this.gl.deleteBuffer(positionBuffer)
      if (normalBuffer) this.gl.deleteBuffer(normalBuffer)
      if (uvBuffer) this.gl.deleteBuffer(uvBuffer)
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

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, uvBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(uvs), this.gl.STATIC_DRAW)
    this.gl.enableVertexAttribArray(this.uvAttribute)
    this.gl.vertexAttribPointer(
      this.uvAttribute,
      1,
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
      uvBuffer,
      vertexCount: positions.length / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS,
      zoom
    }
    this.geometryCache.set(key, entry)
    return entry
  }

  /**
   * Generates vertices, normals, and UVs for dashed line rendering
   * @param points - Array of points with x, y properties
   * @param isClosed - Whether the line should be closed (polygon)
   * @param zoom - Current zoom level for scaling dash length
   * @returns Object with positions, normals, and uvs arrays
   */
  generateDashedLineVertices(points: Point[], isClosed = false, zoom = 1): DashedLineVertices {
    if (points.length < 2) {
      return { positions: [], normals: [], uvs: [] }
    }

    const positions: number[] = []
    const normals: number[] = []
    const uvs: number[] = []

    let accumulatedLength = 0

    for (let i = 0; i < points.length - 1; i++) {
      const p1 = points[i]
      const p2 = points[i + 1]
      if (!p1 || !p2) continue

      const dx = p2.x - p1.x
      const dy = p2.y - p1.y
      const len = Math.sqrt(dx * dx + dy * dy)

      if (len < WEBGL_EPSILON.MIN_SEGMENT_LENGTH) continue

      const normal = normalize(-dy / len, dx / len)

      const scaledLen = len * zoom * 500 // Scale factor to make dashes visible

      positions.push(p1.x, p1.y)
      normals.push(normal.x, normal.y)
      uvs.push(accumulatedLength)

      positions.push(p1.x, p1.y)
      normals.push(-normal.x, -normal.y)
      uvs.push(accumulatedLength)

      positions.push(p2.x, p2.y)
      normals.push(normal.x, normal.y)
      uvs.push(accumulatedLength + scaledLen)

      positions.push(p2.x, p2.y)
      normals.push(-normal.x, -normal.y)
      uvs.push(accumulatedLength + scaledLen)

      accumulatedLength += scaledLen
    }

    if (isClosed && points.length > 2) {
      const p1 = points[points.length - 1]
      const p2 = points[0]
      if (!p1 || !p2) return { positions, normals, uvs }

      const dx = p2.x - p1.x
      const dy = p2.y - p1.y
      const len = Math.sqrt(dx * dx + dy * dy)

      if (len >= WEBGL_EPSILON.MIN_SEGMENT_LENGTH) {
        const normal = normalize(-dy / len, dx / len)
        const scaledLen = len * zoom * 500

        positions.push(p1.x, p1.y)
        normals.push(normal.x, normal.y)
        uvs.push(accumulatedLength)

        positions.push(p1.x, p1.y)
        normals.push(-normal.x, -normal.y)
        uvs.push(accumulatedLength)

        positions.push(p2.x, p2.y)
        normals.push(normal.x, normal.y)
        uvs.push(accumulatedLength + scaledLen)

        positions.push(p2.x, p2.y)
        normals.push(-normal.x, -normal.y)
        uvs.push(accumulatedLength + scaledLen)
      }
    }

    return { positions, normals, uvs }
  }

  /**
   * Draws a dashed line with the specified parameters
   * @param points - Array of points with x, y properties
   * @param color - Color array [r, g, b, a]
   * @param thickness - Line thickness in pixels
   * @param isClosed - Whether to close the line (for polygons)
   * @param dashLength - Length of dash segments in pixels
   * @param gapLength - Length of gap between dashes in pixels
   * @param aspectRatioScale - Scale object with scaleX, scaleY
   * @param view - View transformation with offsetX, offsetY, zoom
   */
  drawDashedLine(
    points: Point[],
    color: readonly number[],
    thickness: number,
    isClosed: boolean,
    dashLength: number,
    gapLength: number,
    aspectRatioScale: Scale,
    view: View,
    cacheKey?: string
  ): void {
    if (!this.initialized || points.length < 2 || !this.program) return

    const cachedGeometry = cacheKey
      ? this.getCachedGeometry(cacheKey, points, isClosed, view.zoom)
      : null
    if (cachedGeometry) {
      this.gl.useProgram(this.program)
      this.gl.bindVertexArray(cachedGeometry.vao)
      this.setUniforms(color, thickness, dashLength, gapLength, aspectRatioScale, view)
      this.gl.drawArrays(this.gl.TRIANGLE_STRIP, 0, cachedGeometry.vertexCount)
      this.gl.bindVertexArray(null)
      return
    }

    const { positions, normals, uvs } = this.generateDashedLineVertices(points, isClosed, view.zoom)

    if (positions.length === 0) return

    this.gl.useProgram(this.program)
    this.gl.bindVertexArray(this.vao)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.uvBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(uvs), this.gl.DYNAMIC_DRAW)

    this.setUniforms(color, thickness, dashLength, gapLength, aspectRatioScale, view)

    this.gl.drawArrays(
      this.gl.TRIANGLE_STRIP,
      0,
      positions.length / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
    )

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

    if (this.uvBuffer) {
      this.gl.deleteBuffer(this.uvBuffer)
      this.uvBuffer = null
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
