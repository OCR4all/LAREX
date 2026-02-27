import type { ResourcePool } from './resource-pool'
import { RENDER_COLORS, RENDER_COLOR_TUNING, RENDER_SIZES } from '@/utils/editor/editor-constants'

export class PolygonRenderer {
  private gl: WebGL2RenderingContext
  private pool: ResourcePool
  private program: WebGLProgram
  private vao: WebGLVertexArrayObject
  private positionBuffer: WebGLBuffer

  constructor(gl: WebGL2RenderingContext, program: WebGLProgram, pool: ResourcePool) {
    this.gl = gl
    this.program = program
    this.pool = pool

    this.vao = gl.createVertexArray()!
    this.positionBuffer = gl.createBuffer()!

    this.setupVAO()
  }

  private setupVAO(): void {
    this.gl.bindVertexArray(this.vao)
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)

    const aPos = this.gl.getAttribLocation(this.program, 'a_position')
    this.gl.enableVertexAttribArray(aPos)
    this.gl.vertexAttribPointer(aPos, 2, this.gl.FLOAT, false, 0, 0)

    this.gl.bindVertexArray(null)
  }

  /**
     * Draw polygon nodes efficiently without buffer reallocation
     */
  drawPolygonNodes(
    polygons: any[],
    selectedIndex: number,
    scale: any,
    view: any,
    hoveredNodeIndex: number,
    draggedNodeInfo: any,
    isInvalidPosition: boolean,
    nodeColor?: number[] // Optional custom color for nodes
  ): void {
    if (selectedIndex < 0 || selectedIndex >= polygons.length) return

    const polygon = polygons[selectedIndex]
    const pointCount = polygon.points.length

    const nodeData = this.pool.getFloat32Array('polygon-nodes', pointCount * 2)

    for (let i = 0; i < pointCount; i++) {
      nodeData[i * 2] = polygon.points[i].x
      nodeData[i * 2 + 1] = polygon.points[i].y
    }

    this.gl.bindVertexArray(this.vao)
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      nodeData.subarray(0, pointCount * 2),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.useProgram(this.program)
    this.setTransformUniforms(scale, view)
    this.gl.uniform1f(this.gl.getUniformLocation(this.program, 'u_pointSize'), RENDER_SIZES.POLYGON_POINT_SIZE)

    const color: [number, number, number, number] = (draggedNodeInfo.isDragging && isInvalidPosition)
      ? (RENDER_COLORS.INVALID_RED as [number, number, number, number])
      : (nodeColor as [number, number, number, number] || (RENDER_COLORS.SELECTED_BLUE as [number, number, number, number]))

    this.gl.uniform4f(
      this.gl.getUniformLocation(this.program, 'u_color'),
      color[0], color[1], color[2], color[3]
    )

    this.gl.drawArrays(this.gl.POINTS, 0, pointCount)

    if (hoveredNodeIndex >= 0 && hoveredNodeIndex < pointCount) {
      this.gl.uniform1f(this.gl.getUniformLocation(this.program, 'u_pointSize'), RENDER_SIZES.DRAGGED_POINT_SIZE)

      let hoverColor: [number, number, number, number]
      if (draggedNodeInfo.isDragging && draggedNodeInfo.nodeIndex === hoveredNodeIndex && isInvalidPosition) {
        hoverColor = RENDER_COLORS.INVALID_RED as [number, number, number, number]
      } else if (nodeColor && nodeColor.length >= 4) {
        hoverColor = [
          Math.min((nodeColor[0] || RENDER_COLOR_TUNING.CHANNEL_MAX) * RENDER_COLOR_TUNING.HOVER_BRIGHTEN_MULTIPLIER, RENDER_COLOR_TUNING.CHANNEL_MAX),
          Math.min((nodeColor[1] || RENDER_COLOR_TUNING.CHANNEL_MAX) * RENDER_COLOR_TUNING.HOVER_BRIGHTEN_MULTIPLIER, RENDER_COLOR_TUNING.CHANNEL_MAX),
          Math.min((nodeColor[2] || RENDER_COLOR_TUNING.CHANNEL_MAX) * RENDER_COLOR_TUNING.HOVER_BRIGHTEN_MULTIPLIER, RENDER_COLOR_TUNING.CHANNEL_MAX),
          RENDER_COLOR_TUNING.CHANNEL_MAX
        ]
      } else {
        hoverColor = RENDER_COLORS.PREVIEW_PINK as [number, number, number, number]
      }

      this.gl.uniform4f(
        this.gl.getUniformLocation(this.program, 'u_color'),
        hoverColor[0], hoverColor[1], hoverColor[2], hoverColor[3]
      )

      const hoveredNodeData = this.pool.getFloat32Array('hovered-node', 2)
      hoveredNodeData[0] = polygon.points[hoveredNodeIndex].x
      hoveredNodeData[1] = polygon.points[hoveredNodeIndex].y

      this.gl.bufferData(
        this.gl.ARRAY_BUFFER,
        hoveredNodeData.subarray(0, 2),
        this.gl.DYNAMIC_DRAW
      )

      this.gl.drawArrays(this.gl.POINTS, 0, 1)
    }

    this.gl.bindVertexArray(null)
  }

  /**
     * Draw current polygon being created
     */
  drawCurrentPolygon(
    currentPoints: any[],
    isInvalidPosition: boolean,
    scale?: any,
    view?: any
  ): void {
    if (currentPoints.length === 0) return

    const pointCount = currentPoints.length
    const nodeData = this.pool.getFloat32Array('current-polygon', pointCount * 2)

    for (let i = 0; i < pointCount; i++) {
      nodeData[i * 2] = currentPoints[i].x
      nodeData[i * 2 + 1] = currentPoints[i].y
    }

    this.gl.bindVertexArray(this.vao)
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer)
    this.gl.bufferData(
      this.gl.ARRAY_BUFFER,
      nodeData.subarray(0, pointCount * 2),
      this.gl.DYNAMIC_DRAW
    )

    this.gl.useProgram(this.program)

    if (scale && view) {
      this.setTransformUniforms(scale, view)
    }

    this.gl.uniform1f(this.gl.getUniformLocation(this.program, 'u_pointSize'), RENDER_SIZES.POLYGON_POINT_SIZE)

    const color: [number, number, number, number] = isInvalidPosition
      ? (RENDER_COLORS.INVALID_RED as [number, number, number, number])
      : (RENDER_COLORS.ACTIVE_YELLOW_POLYGON as [number, number, number, number])

    this.gl.uniform4f(
      this.gl.getUniformLocation(this.program, 'u_color'),
      color[0], color[1], color[2], color[3]
    )

    this.gl.drawArrays(this.gl.POINTS, 0, pointCount)
    this.gl.bindVertexArray(null)
  }

  private setTransformUniforms(scale: any, view: any): void {
    this.gl.uniform2f(
      this.gl.getUniformLocation(this.program, 'u_scale'),
      scale.scaleX, scale.scaleY
    )
    this.gl.uniform2f(
      this.gl.getUniformLocation(this.program, 'u_offset'),
      view.offsetX, view.offsetY
    )
    this.gl.uniform1f(
      this.gl.getUniformLocation(this.program, 'u_zoom'),
      view.zoom
    )
  }

  cleanup(): void {
    this.gl.deleteVertexArray(this.vao)
    this.gl.deleteBuffer(this.positionBuffer)
  }
}
