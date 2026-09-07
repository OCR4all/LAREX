import type { ResourcePool } from './resource-pool'
import { UniformStateCache } from './uniform-state-cache'
import type { Point, View } from '@/models/editor'
import type { Scale } from '@/utils/editor/webgl-utils'
import { RENDER_COLORS, type RGBA } from '@/utils/editor/editor-constants'
import { WEBGL_BUFFER_LAYOUT, WEBGL_GEOMETRY } from '@/webgl/editor/webgl-constants'

export class FillRenderer {
  private gl: WebGL2RenderingContext
  private pool: ResourcePool
  private program: WebGLProgram
  private processingProgram: WebGLProgram | null
  private conflictProgram: WebGLProgram | null
  private uniformState: UniformStateCache
  private vao: WebGLVertexArrayObject
  private processingVao: WebGLVertexArrayObject | null = null
  private conflictVao: WebGLVertexArrayObject | null = null
  private positionBuffer: WebGLBuffer

  constructor(
    gl: WebGL2RenderingContext,
    program: WebGLProgram,
    pool: ResourcePool,
    processingProgram?: WebGLProgram | null,
    conflictProgram?: WebGLProgram | null,
    uniformState = new UniformStateCache(gl),
    private readonly processingTexture?: WebGLTexture | null
  ) {
    this.gl = gl
    this.program = program
    this.processingProgram = processingProgram ?? null
    this.conflictProgram = conflictProgram ?? null
    this.pool = pool
    this.uniformState = uniformState

    this.vao = gl.createVertexArray()!
    this.positionBuffer = gl.createBuffer()!

    this.setupVAO(this.vao, this.program)
    if (this.processingProgram) {
      this.processingVao = gl.createVertexArray()!
      this.setupVAO(this.processingVao, this.processingProgram)
    }
    if (this.conflictProgram) {
      this.conflictVao = gl.createVertexArray()!
      this.setupVAO(this.conflictVao, this.conflictProgram)
    }
  }

  private setupVAO(vao: WebGLVertexArrayObject, program: WebGLProgram): void {
    this.gl.bindVertexArray(vao)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    const aPos = this.gl.getAttribLocation(program, 'a_position')
    this.gl.enableVertexAttribArray(aPos)
    this.gl.vertexAttribPointer(
      aPos,
      WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS,
      this.gl.FLOAT,
      false,
      WEBGL_BUFFER_LAYOUT.NO_STRIDE_BYTES,
      WEBGL_BUFFER_LAYOUT.NO_OFFSET_BYTES
    )

    this.gl.bindVertexArray(null)
  }

  /**
     * Draw a single filled polygon (immediate mode)
     */
  drawFill(
    polygonPoints: Point[],
    triangleIndices: readonly number[],
    color: RGBA,
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

    this.setTransformUniforms(this.program, scale, view)
    this.uniformState.uniform4f(
      this.uniformState.getLocation(this.program, 'u_color'),
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

  drawProcessingFill(
    polygonPoints: Point[],
    triangleIndices: readonly number[],
    scale: Scale,
    view: View,
    timeSeconds: number
  ): void {
    if (triangleIndices.length < WEBGL_GEOMETRY.MIN_TRIANGLE_INDEX_COUNT) return
    const program = this.processingProgram ?? this.program
    const vao = this.processingVao ?? this.vao
    const vertexCount = triangleIndices.length
    const fillVertices = this.pool.getFloat32Array('processing-fill-vertices', vertexCount * 2)

    for (let i = 0; i < triangleIndices.length; i++) {
      const triangleIndex = triangleIndices[i]!
      const vertex = polygonPoints[triangleIndex]!
      fillVertices[i * 2] = vertex.x
      fillVertices[i * 2 + 1] = vertex.y
    }

    this.gl.bindVertexArray(vao)
    this.gl.useProgram(program)

    this.gl.enable(this.gl.BLEND)
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA)

    this.setTransformUniforms(program, scale, view)
    this.uniformState.uniform1f(this.uniformState.getLocation(program, 'u_time'), timeSeconds)
    this.gl.activeTexture(this.gl.TEXTURE0)
    this.gl.bindTexture(this.gl.TEXTURE_2D, this.processingTexture ?? null)
    this.uniformState.uniform1i(this.uniformState.getLocation(program, 'u_image'), 0)

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

  drawLabelConflictFill(
    polygonPoints: Point[],
    triangleIndices: readonly number[],
    scale: Scale,
    view: View,
    baseColor: RGBA,
    stripeColor: RGBA
  ): void {
    if (!this.conflictProgram || !this.conflictVao) return
    if (triangleIndices.length < WEBGL_GEOMETRY.MIN_TRIANGLE_INDEX_COUNT) return

    const vertexCount = triangleIndices.length
    const fillVertices = this.pool.getFloat32Array('label-conflict-fill-vertices', vertexCount * 2)

    for (let i = 0; i < triangleIndices.length; i++) {
      const triangleIndex = triangleIndices[i]!
      const vertex = polygonPoints[triangleIndex]!
      fillVertices[i * 2] = vertex.x
      fillVertices[i * 2 + 1] = vertex.y
    }

    this.gl.bindVertexArray(this.conflictVao)
    this.gl.useProgram(this.conflictProgram)
    this.gl.enable(this.gl.BLEND)
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA)

    this.setTransformUniforms(this.conflictProgram, scale, view)
    this.uniformState.uniform4f(
      this.uniformState.getLocation(this.conflictProgram, 'u_baseColor'),
      baseColor[0], baseColor[1], baseColor[2], baseColor[3]
    )
    this.uniformState.uniform4f(
      this.uniformState.getLocation(this.conflictProgram, 'u_stripeColor'),
      stripeColor[0], stripeColor[1], stripeColor[2], stripeColor[3]
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
      RENDER_COLORS.HOVER_FILL_YELLOW,
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
    triangleIndices: readonly number[],
    scale: Scale,
    view: View,
    color: RGBA = RENDER_COLORS.INVALID_FILL_RED
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

  private setTransformUniforms(program: WebGLProgram, scale: Scale, view: View): void {
    this.uniformState.uniform2f(
      this.uniformState.getLocation(program, 'u_scale'),
      scale.scaleX,
      scale.scaleY
    )
    this.uniformState.uniform2f(
      this.uniformState.getLocation(program, 'u_offset'),
      view.offsetX,
      view.offsetY
    )
    this.uniformState.uniform1f(this.uniformState.getLocation(program, 'u_zoom'), view.zoom)
    this.uniformState.uniform2f(
      this.uniformState.getLocation(program, 'u_rotation'),
      scale.rotationCos ?? 1,
      scale.rotationSin ?? 0
    )

    const fallbackAspect = (this.gl.canvas.width > 0 && this.gl.canvas.height > 0)
      ? (this.gl.canvas.width / this.gl.canvas.height)
      : 1
    this.uniformState.uniform1f(
      this.uniformState.getLocation(program, 'u_canvasAspect'),
      scale.rotationAspect ?? fallbackAspect
    )
  }

  cleanup(): void {
    this.gl.deleteVertexArray(this.vao)
    if (this.processingVao) {
      this.gl.deleteVertexArray(this.processingVao)
    }
    if (this.conflictVao) {
      this.gl.deleteVertexArray(this.conflictVao)
    }
    this.gl.deleteBuffer(this.positionBuffer)
  }
}
