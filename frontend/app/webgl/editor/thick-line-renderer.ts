import { createProgram } from './core'
import { setTransformUniforms, setColorUniform } from '@/utils/editor/webgl-utils'
import type { Scale } from '@/utils/editor/webgl-utils'
import { normalize } from '@/utils/editor/geometry-utils'
import type { Point, View } from '@/models/editor'
import { WEBGL_BUFFER_LAYOUT, WEBGL_DEFAULTS, WEBGL_EPSILON, WEBGL_GLSL, WEBGL_LINE_GEOMETRY } from '@/webgl/editor/webgl-constants'
import { glslFloatLiteral } from '@/webgl/editor/glsl-utils'

export interface LineData {
  points: Point[]
  color: number[]
  thickness: number
  isClosed: boolean
}

interface ThickLineVertices {
  positions: number[]
  normals: number[]
}

/**
 * Thick line renderer for WebGL - renders lines with configurable thickness
 */
export class ThickLineRenderer {
  private gl: WebGL2RenderingContext
  private program: WebGLProgram | null = null
  private vao: WebGLVertexArrayObject | null = null
  private positionBuffer: WebGLBuffer | null = null
  private normalBuffer: WebGLBuffer | null = null
  private initialized = false

  constructor(gl: WebGL2RenderingContext) {
    this.gl = gl
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
    this.initialized = true
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
    const aPosLine = this.gl.getAttribLocation(this.program, 'a_position')
    this.gl.enableVertexAttribArray(aPosLine)
    this.gl.vertexAttribPointer(
      aPosLine,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.normalBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
    const aNormLine = this.gl.getAttribLocation(this.program, 'a_normal')
    this.gl.enableVertexAttribArray(aNormLine)
    this.gl.vertexAttribPointer(
      aNormLine,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.gl.bindVertexArray(null)
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
    color: number[],
    thickness: number,
    isClosed: boolean,
    aspectRatioScale: Scale,
    view: View
  ): void {
    if (!this.initialized || points.length < 2 || !this.program) return

    const { positions, normals } = this.generateThickLineVertices(points, isClosed)

    if (positions.length === 0) return

    this.gl.useProgram(this.program)
    this.gl.bindVertexArray(this.vao)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.DYNAMIC_DRAW)

    setTransformUniforms(this.gl, this.program, aspectRatioScale, view, view.zoom)
    const rgba: [number, number, number, number] = [
      color[0] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[1] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[2] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
      color[3] ?? WEBGL_DEFAULTS.ALPHA_CHANNEL
    ]
    setColorUniform(this.gl, this.program, rgba)

    const thicknessLocation = this.gl.getUniformLocation(this.program, 'u_thickness')
    if (thicknessLocation) this.gl.uniform1f(thicknessLocation, thickness)

    const resolutionLocation = this.gl.getUniformLocation(this.program, 'u_resolution')
    if (resolutionLocation) {
      const canvas = this.gl.canvas as HTMLCanvasElement
      this.gl.uniform2f(resolutionLocation, canvas.width, canvas.height)
    }

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

    setTransformUniforms(this.gl, this.program, aspectRatioScale, view, view.zoom)

    const thicknessLocation = this.gl.getUniformLocation(this.program, 'u_thickness')
    const resolutionLocation = this.gl.getUniformLocation(this.program, 'u_resolution')
    this.gl.uniform2f(resolutionLocation, this.gl.canvas.width, this.gl.canvas.height)

    for (const line of lineData) {
      const { positions, normals } = this.generateThickLineVertices(line.points, line.isClosed)

      if (positions.length === 0) continue

      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
      this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.DYNAMIC_DRAW)

      this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
      this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.DYNAMIC_DRAW)

      this.gl.uniform1f(thicknessLocation, line.thickness)
      const lineRgba: [number, number, number, number] = [
        line.color[0] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
        line.color[1] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
        line.color[2] ?? WEBGL_DEFAULTS.COLOR_CHANNEL,
        line.color[3] ?? WEBGL_DEFAULTS.ALPHA_CHANNEL
      ]
      setColorUniform(this.gl, this.program, lineRgba)

      this.gl.drawArrays(
        this.gl.TRIANGLE_STRIP,
        0,
        positions.length / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
      )
    }

    this.gl.bindVertexArray(null)
  }

  /**
   * Cleanup function to release resources
   */
  cleanup(): void {
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
  }
}
