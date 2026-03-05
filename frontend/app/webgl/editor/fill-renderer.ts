import type { ResourcePool } from './resource-pool'
import type { Point, View } from '@/models/editor'
import type { Scale } from '@/utils/editor/webgl-utils'
import { RENDER_COLORS } from '@/utils/editor/editor-constants'
import { WEBGL_BATCH, WEBGL_BUFFER_LAYOUT, WEBGL_FILL_GEOMETRY, WEBGL_GEOMETRY } from '@/webgl/editor/webgl-constants'

interface PolygonFillData {
  polygon: Point[]
  color: [number, number, number, number]
  triangleIndices: number[]
}

export class FillRenderer {
  private gl: WebGL2RenderingContext
  private pool: ResourcePool
  private program: WebGLProgram
  private vao: WebGLVertexArrayObject
  private positionBuffer: WebGLBuffer
  private indexBuffer: WebGLBuffer

  private batchedVertices: Float32Array
  private batchedIndices: Uint16Array
  private batchedColors: Float32Array
  private currentBatchSize = 0
  private maxBatchSize = WEBGL_BATCH.FILL_MAX_VERTICES // vertices

  constructor(gl: WebGL2RenderingContext, program: WebGLProgram, pool: ResourcePool) {
    this.gl = gl
    this.program = program
    this.pool = pool

    this.batchedVertices = new Float32Array(this.maxBatchSize * WEBGL_FILL_GEOMETRY.POSITION_COMPONENTS)
    this.batchedIndices = new Uint16Array(this.maxBatchSize * WEBGL_FILL_GEOMETRY.INDICES_MULTIPLIER)
    this.batchedColors = new Float32Array(this.maxBatchSize * WEBGL_FILL_GEOMETRY.COLOR_COMPONENTS)

    this.vao = gl.createVertexArray()!
    this.positionBuffer = gl.createBuffer()!
    this.indexBuffer = gl.createBuffer()!

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

    this.gl.bindBuffer(this.gl.ELEMENT_ARRAY_BUFFER, this.indexBuffer)

    this.gl.bindVertexArray(null)
  }

  /**
     * Draw a single filled polygon (immediate mode)
     */
  drawFill(
    polygonPoints: Point[],
    triangleIndices: number[],
    color: [number, number, number, number],
    scale: Scale,
    view: View
  ): void {
    if (triangleIndices.length < WEBGL_GEOMETRY.MIN_TRIANGLE_INDEX_COUNT) return

    const vertexCount = triangleIndices.length
    const fillVertices = this.pool.getFloat32Array('fill-vertices', vertexCount * 2)

    for (let i = 0; i < triangleIndices.length; i++) {
      const triangleIndex = triangleIndices[i]!
      const vertex = polygonPoints[triangleIndex]!
      fillVertices[i * 2] = vertex.x
      fillVertices[i * 2 + 1] = vertex.y
    }

    this.gl.bindVertexArray(this.vao)
    this.gl.useProgram(this.program)

    this.gl.enable(this.gl.BLEND)
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA)

    this.setTransformUniforms(scale, view)
    this.gl.uniform4f(
      this.gl.getUniformLocation(this.program, 'u_color'),
      color[0], color[1], color[2], color[3]
    )

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      fillVertices.subarray(0, vertexCount * 2),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.drawArrays(this.gl.TRIANGLES, 0, vertexCount)

    this.gl.disable(this.gl.BLEND)
    this.gl.bindVertexArray(null)
  }

  /**
     * BATCHED: Add polygon to batch for later rendering
     */
  addToBatch(
    polygonPoints: Point[],
    triangleIndices: number[],
    color: [number, number, number, number]
  ): void {
    if (triangleIndices.length < WEBGL_GEOMETRY.MIN_TRIANGLE_INDEX_COUNT) return

    const vertexCount = triangleIndices.length

    if (this.currentBatchSize + vertexCount > this.maxBatchSize) {
      this.flushBatch()
    }

    const baseVertex = this.currentBatchSize
    for (let i = 0; i < vertexCount; i++) {
      const triangleIndex = triangleIndices[i]!
      const vertex = polygonPoints[triangleIndex]!
      const idx = (baseVertex + i) * 2
      this.batchedVertices[idx] = vertex.x
      this.batchedVertices[idx + 1] = vertex.y

      const colorIdx = (baseVertex + i) * 4
      this.batchedColors[colorIdx] = color[0]
      this.batchedColors[colorIdx + 1] = color[1]
      this.batchedColors[colorIdx + 2] = color[2]
      this.batchedColors[colorIdx + 3] = color[3]
    }

    for (let i = 0; i < vertexCount; i++) {
      this.batchedIndices[baseVertex + i] = baseVertex + i
    }

    this.currentBatchSize += vertexCount
  }

  /**
     * BATCHED: Render all batched polygons in a single draw call
     */
  flushBatch(scale?: Scale, view?: View): void {
    if (this.currentBatchSize === 0) return

    this.gl.bindVertexArray(this.vao)
    this.gl.useProgram(this.program)

    if (scale && view) {
      this.setTransformUniforms(scale, view)
    }

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      this.batchedVertices.subarray(0, this.currentBatchSize * WEBGL_FILL_GEOMETRY.POSITION_COMPONENTS),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.bindBuffer(this.gl.ELEMENT_ARRAY_BUFFER, this.indexBuffer)
    this.gl.bufferData(
      this.gl.ELEMENT_ARRAY_BUFFER,
      this.batchedIndices.subarray(0, this.currentBatchSize),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.drawElements(
      this.gl.TRIANGLES,
      this.currentBatchSize,
      this.gl.UNSIGNED_SHORT,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.gl.bindVertexArray(null)
    this.currentBatchSize = 0
  }

  /**
     * Draw hover polygon with transparency
     */
  drawHoverFill(
    polygonPoints: Point[],
    triangleIndices: number[],
    scale: Scale,
    view: View
  ): void {
    this.gl.enable(this.gl.BLEND)
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA)

    this.drawFill(
      polygonPoints,
      triangleIndices,
      RENDER_COLORS.HOVER_FILL_YELLOW as [number, number, number, number],
      scale,
      view
    )

    this.gl.disable(this.gl.BLEND)
  }

  /**
     * Draw invalid position fill (red overlay by default, or custom color)
     */
  drawInvalidFill(
    polygonPoints: Point[],
    triangleIndices: number[],
    scale: Scale,
    view: View,
    color: [number, number, number, number] = RENDER_COLORS.INVALID_FILL_RED as [number, number, number, number]
  ): void {
    this.gl.enable(this.gl.BLEND)
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA)

    this.drawFill(
      polygonPoints,
      triangleIndices,
      color,
      scale,
      view
    )

    this.gl.disable(this.gl.BLEND)
  }

  private setTransformUniforms(scale: Scale, view: View): void {
    const scaleLocation = this.gl.getUniformLocation(this.program, 'u_scale')
    const offsetLocation = this.gl.getUniformLocation(this.program, 'u_offset')
    const zoomLocation = this.gl.getUniformLocation(this.program, 'u_zoom')
    const rotationLocation = this.gl.getUniformLocation(this.program, 'u_rotation')
    const canvasAspectLocation = this.gl.getUniformLocation(this.program, 'u_canvasAspect')
    if (!scaleLocation || !offsetLocation || !zoomLocation) return

    this.gl.uniform2f(scaleLocation, scale.scaleX, scale.scaleY)
    this.gl.uniform2f(offsetLocation, view.offsetX, view.offsetY)
    this.gl.uniform1f(zoomLocation, view.zoom)
    if (rotationLocation) {
      this.gl.uniform2f(rotationLocation, scale.rotationCos ?? 1, scale.rotationSin ?? 0)
    }
    if (canvasAspectLocation) {
      const fallbackAspect = (this.gl.canvas.width > 0 && this.gl.canvas.height > 0)
        ? (this.gl.canvas.width / this.gl.canvas.height)
        : 1
      this.gl.uniform1f(canvasAspectLocation, scale.rotationAspect ?? fallbackAspect)
    }
  }

  cleanup(): void {
    this.gl.deleteVertexArray(this.vao)
    this.gl.deleteBuffer(this.positionBuffer)
    this.gl.deleteBuffer(this.indexBuffer)
  }
}
