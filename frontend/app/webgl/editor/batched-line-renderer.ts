import type { ResourcePool } from './resource-pool'
import { normalize } from '@/utils/editor/geometry-utils'
import type { AspectRatioScale, Point, View } from '@/models/editor'
import { WEBGL_BATCH, WEBGL_BUFFER_LAYOUT, WEBGL_EPSILON, WEBGL_LINE_GEOMETRY } from '@/webgl/editor/webgl-constants'

interface LineSegment {
  points: Point[]
  color: [number, number, number, number]
  thickness: number
  isClosed: boolean
}

export class BatchedLineRenderer {
  private gl: WebGL2RenderingContext
  private pool: ResourcePool
  private program: WebGLProgram
  private vao: WebGLVertexArrayObject
  private positionBuffer: WebGLBuffer
  private normalBuffer: WebGLBuffer
  private colorBuffer: WebGLBuffer

  private batchedLines: LineSegment[] = []
  private maxBatchSize = WEBGL_BATCH.LINE_MAX_SEGMENTS // line segments

  private vertexData: Float32Array
  private normalData: Float32Array
  private colorData: Float32Array

  constructor(gl: WebGL2RenderingContext, program: WebGLProgram, pool: ResourcePool) {
    this.gl = gl
    this.program = program
    this.pool = pool

    this.vertexData = new Float32Array(
      this.maxBatchSize * WEBGL_LINE_GEOMETRY.VERTICES_PER_SEGMENT * WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
    )
    this.normalData = new Float32Array(
      this.maxBatchSize * WEBGL_LINE_GEOMETRY.VERTICES_PER_SEGMENT * WEBGL_LINE_GEOMETRY.NORMAL_COMPONENTS
    )
    this.colorData = new Float32Array(
      this.maxBatchSize * WEBGL_LINE_GEOMETRY.VERTICES_PER_SEGMENT * WEBGL_LINE_GEOMETRY.COLOR_COMPONENTS
    )

    this.vao = gl.createVertexArray()!
    this.positionBuffer = gl.createBuffer()!
    this.normalBuffer = gl.createBuffer()!
    this.colorBuffer = gl.createBuffer()!

    this.setupVAO()
  }

  private setupVAO(): void {
    this.gl.bindVertexArray(this.vao)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    const aPos = this.gl.getAttribLocation(this.program, 'a_position')
    this.gl.enableVertexAttribArray(aPos)
    this.gl.vertexAttribPointer(
      aPos,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
    const aNormal = this.gl.getAttribLocation(this.program, 'a_normal')
    this.gl.enableVertexAttribArray(aNormal)
    this.gl.vertexAttribPointer(
      aNormal,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.colorBuffer)
    const aColor = this.gl.getAttribLocation(this.program, 'a_color')
    if (aColor >= 0) { // Only if shader supports it
      this.gl.enableVertexAttribArray(aColor)
      this.gl.vertexAttribPointer(
        aColor,
        WEBGL_BUFFER_LAYOUT.VEC4_COMPONENTS,
        this.gl.FLOAT,
        false,
        WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
        WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
      )
    }

    this.gl.bindVertexArray(null)
  }

  /**
     * Add line to batch
     */
  addLine(
    points: Point[],
    color: [number, number, number, number],
    thickness: number,
    isClosed: boolean
  ): void {
    this.batchedLines.push({ points, color, thickness, isClosed })

    if (this.batchedLines.length >= WEBGL_BATCH.LINE_AUTO_FLUSH_LINES) {
      this.flushBatch()
    }
  }

  /**
     * Flush all batched lines to GPU
     */
  flushBatch(scale?: AspectRatioScale, view?: View): void {
    if (this.batchedLines.length === 0) return

    let vertexOffset = 0

    for (const line of this.batchedLines) {
      const { positions, normals, colors } = this.generateThickLineVertices(
        line.points,
        line.color,
        line.isClosed
      )

      for (let i = 0; i < positions.length; i++) {
        this.vertexData[vertexOffset + i] = positions[i]!
        this.normalData[vertexOffset + i] = normals[i]!
      }

      for (let i = 0; i < colors.length; i++) {
        const vertexCount = vertexOffset / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
        const colorOffset = vertexCount * WEBGL_LINE_GEOMETRY.COLOR_COMPONENTS
        this.colorData[colorOffset + i] = colors[i]!
      }

      vertexOffset += positions.length
    }

    this.gl.bindVertexArray(this.vao)
    this.gl.useProgram(this.program)

    if (scale && view) {
      this.setTransformUniforms(scale, view)
    }

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      this.vertexData.subarray(0, vertexOffset),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      this.normalData.subarray(0, vertexOffset),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.colorBuffer)
    const vertexCount = vertexOffset / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
    const colorFloatCount = vertexCount * WEBGL_LINE_GEOMETRY.COLOR_COMPONENTS
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      this.colorData.subarray(0, colorFloatCount),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.drawArrays(this.gl.TRIANGLE_STRIP, 0, vertexCount)

    this.gl.bindVertexArray(null)

    this.batchedLines = []
  }

  /**
     * Generate vertices for thick line with per-vertex colors
     */
  private generateThickLineVertices(
    points: Point[],
    color: [number, number, number, number],
    isClosed: boolean
  ): { positions: number[], normals: number[], colors: number[] } {
    if (points.length < 2) {
      return { positions: [], normals: [], colors: [] }
    }

    const positions: number[] = []
    const normals: number[] = []
    const colors: number[] = []

    const addSegment = (p1: Point, p2: Point) => {
      const dx = p2.x - p1.x
      const dy = p2.y - p1.y
      const len = Math.sqrt(dx * dx + dy * dy)

      if (len < WEBGL_EPSILON.MIN_SEGMENT_LENGTH) return

      const normal = normalize(-dy / len, dx / len)

      positions.push(p1.x, p1.y, p1.x, p1.y)
      normals.push(normal.x, normal.y, -normal.x, -normal.y)
      colors.push(...color, ...color)

      positions.push(p2.x, p2.y, p2.x, p2.y)
      normals.push(normal.x, normal.y, -normal.x, -normal.y)
      colors.push(...color, ...color)
    }

    for (let i = 0; i < points.length - 1; i++) {
      const p1 = points[i]
      const p2 = points[i + 1]
      if (!p1 || !p2) continue
      addSegment(p1, p2)
    }

    if (isClosed && points.length > 2) {
      const last = points[points.length - 1]
      const first = points[0]
      if (last && first) {
        addSegment(last, first)
      }
    }

    return { positions, normals, colors }
  }

  /**
     * Draw single line immediately (non-batched)
     */
  drawLine(
    points: Point[],
    color: [number, number, number, number],
    thickness: number,
    isClosed: boolean,
    scale: AspectRatioScale,
    view: View
  ): void {
    const { positions, normals } = this.generateThickLineVertices(points, color, isClosed)

    if (positions.length === 0) return

    this.gl.bindVertexArray(this.vao)
    this.gl.useProgram(this.program)

    this.setTransformUniforms(scale, view)
    const thicknessLocation = this.gl.getUniformLocation(this.program, 'u_thickness')
    if (thicknessLocation) this.gl.uniform1f(thicknessLocation, thickness)

    const canvas = this.gl.canvas as HTMLCanvasElement
    const resolutionLocation = this.gl.getUniformLocation(this.program, 'u_resolution')
    if (resolutionLocation) this.gl.uniform2f(resolutionLocation, canvas.width, canvas.height)

    const colorLocation = this.gl.getUniformLocation(this.program, 'u_color')
    if (colorLocation) this.gl.uniform4f(colorLocation, color[0], color[1], color[2], color[3])

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      new Float32Array(positions),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.normalBuffer)
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      new Float32Array(normals),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.drawArrays(
      this.gl.TRIANGLE_STRIP,
      0,
      positions.length / WEBGL_LINE_GEOMETRY.POSITION_COMPONENTS
    )
    this.gl.bindVertexArray(null)
  }

  private setTransformUniforms(scale: AspectRatioScale, view: View): void {
    const scaleLocation = this.gl.getUniformLocation(this.program, 'u_scale')
    const offsetLocation = this.gl.getUniformLocation(this.program, 'u_offset')
    const zoomLocation = this.gl.getUniformLocation(this.program, 'u_zoom')
    if (!scaleLocation || !offsetLocation || !zoomLocation) return

    this.gl.uniform2f(scaleLocation, scale.scaleX, scale.scaleY)
    this.gl.uniform2f(offsetLocation, view.offsetX, view.offsetY)
    this.gl.uniform1f(zoomLocation, view.zoom)
  }

  cleanup(): void {
    this.gl.deleteVertexArray(this.vao)
    this.gl.deleteBuffer(this.positionBuffer)
    this.gl.deleteBuffer(this.normalBuffer)
    this.gl.deleteBuffer(this.colorBuffer)
  }
}
