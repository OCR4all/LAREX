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

export interface LineBatchBuffers {
  /** Interleaved position, normal, color and thickness per vertex */
  vertices: Float32Array
  indices: Uint32Array
}

interface LineBatch {
  lines: LineData[]
  geometryVersion: number
  indexCount: number
}

const BATCH_VERTEX_COMPONENTS = WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
  + WEBGL_LINE_GEOMETRY.NORMAL_COMPONENTS
  + WEBGL_LINE_GEOMETRY.COLOR_COMPONENTS
  + 1

function isSameLine(a: LineData, b: LineData): boolean {
  return a.points === b.points
    && a.isClosed === b.isClosed
    && a.thickness === b.thickness
    && a.color[0] === b.color[0]
    && a.color[1] === b.color[1]
    && a.color[2] === b.color[2]
    && a.color[3] === b.color[3]
}

/**
 * The same shaders draw one line (color and thickness as uniforms) or a batch
 * of lines (color and thickness per vertex).
 */
function createLineProgram(gl: WebGL2RenderingContext, batched: boolean): WebGLProgram {
  const vsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      in vec2 a_position;
      in vec2 a_normal;
      ${batched ? 'in vec4 a_color; in float a_thickness; out vec4 v_color;' : 'uniform float u_thickness;'}
      uniform vec2 u_scale;
      uniform vec2 u_offset;
      uniform float u_zoom;
      uniform vec2 u_rotation;
      uniform float u_canvasAspect;
      uniform vec2 u_resolution;

      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        vec2 scaled = pos * u_scale;
        vec2 clip = vec2(
          scaled.x * u_rotation.x - (scaled.y * u_rotation.y) / u_canvasAspect,
          scaled.x * u_rotation.y * u_canvasAspect + scaled.y * u_rotation.x
        );

        vec2 pixelSize = ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_PIXEL_SCALE)} / u_resolution;
        vec2 offset = a_normal * ${batched ? 'a_thickness' : 'u_thickness'} * pixelSize * ${glslFloatLiteral(WEBGL_GLSL.HALF)};
        vec2 clipOffset = vec2(
          offset.x * u_rotation.x - (offset.y * u_rotation.y) / u_canvasAspect,
          offset.x * u_rotation.y * u_canvasAspect + offset.y * u_rotation.x
        );

        gl_Position = vec4(clip + clipOffset, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_Z)}, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_W)});
        ${batched ? 'v_color = a_color;' : ''}
      }`

  const fsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      precision mediump float;
      ${batched ? 'in vec4 v_color;' : 'uniform vec4 u_color;'}
      out vec4 outColor;

      void main() {
        outColor = ${batched ? 'v_color' : 'u_color'};
      }`

  return createProgram(gl, vsSource, fsSource)
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
  private geometryVersion = 0
  private batchProgram: WebGLProgram | null = null
  private batchVao: WebGLVertexArrayObject | null = null
  private batchVertexBuffer: WebGLBuffer | null = null
  private batchIndexBuffer: WebGLBuffer | null = null
  private batchUniforms: ThickLineUniforms | null = null
  private batch: LineBatch | null = null
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

    this.program = createLineProgram(this.gl, false)
    this.batchProgram = createLineProgram(this.gl, true)
    this.setupBuffers()
    this.setupBatchBuffers()
    this.uniforms = this.getUniformLocations(this.program)
    this.batchUniforms = this.getUniformLocations(this.batchProgram)
    this.initialized = true
  }

  private getUniformLocations(program: WebGLProgram): ThickLineUniforms {
    return {
      scale: this.uniformState.getLocation(program, 'u_scale'),
      offset: this.uniformState.getLocation(program, 'u_offset'),
      zoom: this.uniformState.getLocation(program, 'u_zoom'),
      rotation: this.uniformState.getLocation(program, 'u_rotation'),
      canvasAspect: this.uniformState.getLocation(program, 'u_canvasAspect'),
      color: this.uniformState.getLocation(program, 'u_color'),
      thickness: this.uniformState.getLocation(program, 'u_thickness'),
      resolution: this.uniformState.getLocation(program, 'u_resolution')
    }
  }

  private setupBatchBuffers(): void {
    if (!this.batchProgram) return
    const gl = this.gl
    const stride = BATCH_VERTEX_COMPONENTS * WEBGL_BUFFER_LAYOUT.FLOAT_BYTES

    this.batchVao = gl.createVertexArray()
    this.batchVertexBuffer = gl.createBuffer()
    this.batchIndexBuffer = gl.createBuffer()
    gl.bindVertexArray(this.batchVao)
    gl.bindBuffer(gl.ARRAY_BUFFER, this.batchVertexBuffer)
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.batchIndexBuffer)

    let offset = 0
    const attributes: Array<[string, number]> = [
      ['a_position', WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS],
      ['a_normal', WEBGL_LINE_GEOMETRY.NORMAL_COMPONENTS],
      ['a_color', WEBGL_LINE_GEOMETRY.COLOR_COMPONENTS],
      ['a_thickness', 1]
    ]
    for (const [name, components] of attributes) {
      const location = gl.getAttribLocation(this.batchProgram, name)
      gl.enableVertexAttribArray(location)
      gl.vertexAttribPointer(location, components, gl.FLOAT, false, stride, offset * WEBGL_BUFFER_LAYOUT.FLOAT_BYTES)
      offset += components
    }

    gl.bindVertexArray(null)
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

    this.setTransformUniforms(uniforms, aspectRatioScale, view)
    this.uniformState.uniform4f(
      uniforms.color,
      color[0] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[1] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[2] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[3] ?? WEBGL_DEFAULTS.ALPHA_CHANNEL
    )
    this.uniformState.uniform1f(uniforms.thickness, thickness)
  }

  private setTransformUniforms(uniforms: ThickLineUniforms, aspectRatioScale: Scale, view: View): void {
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
   * Expand lines into one vertex and index buffer. Each line keeps the
   * triangles its triangle strip would produce, so a batch renders the same
   * pixels as drawing the lines one by one.
   */
  buildLineBatchBuffers(lines: LineData[]): LineBatchBuffers {
    const vertices: number[] = []
    const indices: number[] = []

    for (const line of lines) {
      const { positions, normals } = this.generateThickLineVertices(line.points, line.isClosed)
      const firstVertex = vertices.length / BATCH_VERTEX_COMPONENTS
      const vertexCount = positions.length / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS

      for (let vertex = 0; vertex < vertexCount; vertex++) {
        vertices.push(
          positions[vertex * 2]!, positions[vertex * 2 + 1]!,
          normals[vertex * 2]!, normals[vertex * 2 + 1]!,
          line.color[0] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
          line.color[1] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
          line.color[2] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
          line.color[3] ?? WEBGL_DEFAULTS.ALPHA_CHANNEL,
          line.thickness
        )
      }
      for (let vertex = 0; vertex + 2 < vertexCount; vertex++) {
        indices.push(firstVertex + vertex, firstVertex + vertex + 1, firstVertex + vertex + 2)
      }
    }

    return { vertices: new Float32Array(vertices), indices: new Uint32Array(indices) }
  }

  /**
   * Draws many thick lines in one draw call. The buffers are reused until a
   * line's points array, color, thickness or closing changes, or geometry is
   * invalidated.
   * @param lines - Lines to draw, in paint order
   * @param aspectRatioScale - Scale object with scaleX, scaleY
   * @param view - View transformation with offsetX, offsetY, zoom
   */
  drawMultipleThickLines(lines: LineData[], aspectRatioScale: Scale, view: View): void {
    if (!this.initialized || !this.batchProgram || !this.batchUniforms || lines.length === 0) return
    const gl = this.gl

    gl.useProgram(this.batchProgram)
    gl.bindVertexArray(this.batchVao)

    const batch = this.batch
    const isCurrent = batch !== null
      && batch.geometryVersion === this.geometryVersion
      && batch.lines.length === lines.length
      && lines.every((line, index) => isSameLine(line, batch.lines[index]!))
    if (!isCurrent) {
      const { vertices, indices } = this.buildLineBatchBuffers(lines)
      gl.bindBuffer(gl.ARRAY_BUFFER, this.batchVertexBuffer)
      gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.DYNAMIC_DRAW)
      gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indices, gl.DYNAMIC_DRAW)
      this.batch = { lines, geometryVersion: this.geometryVersion, indexCount: indices.length }
    }

    this.setTransformUniforms(this.batchUniforms, aspectRatioScale, view)
    gl.drawElements(gl.TRIANGLES, this.batch!.indexCount, gl.UNSIGNED_INT, 0)
    gl.bindVertexArray(null)
  }

  invalidateGeometry(cacheKey: string): void {
    this.geometryVersion++
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
    this.geometryVersion++
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

    this.gl.deleteBuffer(this.batchVertexBuffer)
    this.gl.deleteBuffer(this.batchIndexBuffer)
    this.gl.deleteVertexArray(this.batchVao)
    this.gl.deleteProgram(this.batchProgram)
    this.batchVertexBuffer = null
    this.batchIndexBuffer = null
    this.batchVao = null
    this.batchProgram = null
    this.batchUniforms = null
    this.batch = null

    this.initialized = false
    this.uniforms = null
  }
}
