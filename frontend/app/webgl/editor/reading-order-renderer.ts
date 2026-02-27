/**
 * Reading Order Renderer - Renders reading order visualization with animated arrows and group bounds
 *
 * Features:
 * - Animated dashed arrows that flow in the reading direction
 * - Filled triangular arrowheads
 * - Thick, highly visible lines
 * - Dashed bounding boxes around ordered/unordered groups
 * - Batched rendering for performance
 */

import { createProgram } from './core'
import { normalize } from '@/utils/editor/geometry-utils'
import type { Point, View, AspectRatioScale } from '@/models/editor'
import { WEBGL_BUFFER_LAYOUT, WEBGL_EPSILON, WEBGL_GLSL } from '@/webgl/editor/webgl-constants'
import { glslFloatLiteral } from '@/webgl/editor/glsl-utils'

export interface ArrowSegment {
  from: Point
  to: Point
  color: [number, number, number, number]
}

export interface GroupBounds {
  points: Point[] // 4 corner points for bounding box
  color: [number, number, number, number]
  isOrdered: boolean
  label?: string
}

export interface OrderNumber {
  position: Point
  number: number
  color: [number, number, number, number]
  isNested?: boolean
  isHidden?: boolean
  depth: number // Nesting depth (0 = top-level)
  label?: string
}

export interface ReadingOrderRenderData {
  arrows: ArrowSegment[]
  groupBounds: GroupBounds[]
  orderNumbers: OrderNumber[]
}

const ARROW_HEAD_LENGTH = 24 // pixels - larger arrowhead
const ARROW_HEAD_WIDTH = 20 // pixels - width of arrowhead base
const ARROW_SHAFT_THICKNESS = 8 // pixels - much thicker shaft for visibility
const DASH_LENGTH = 16 // Longer dashes for better visibility
const GAP_LENGTH = 10 // Gap between dashes
const ANIMATION_SPEED = 40 // pixels per second for dash animation
const GROUP_BOUNDS_THICKNESS = 3
const GROUP_BOUNDS_DASH_LENGTH = 10
const GROUP_BOUNDS_GAP_LENGTH = 6

export class ReadingOrderRenderer {
  private gl: WebGL2RenderingContext

  private arrowShaftProgram: WebGLProgram | null = null
  private arrowShaftVao: WebGLVertexArrayObject | null = null
  private arrowShaftPositionBuffer: WebGLBuffer | null = null
  private arrowShaftNormalBuffer: WebGLBuffer | null = null
  private arrowShaftUvBuffer: WebGLBuffer | null = null
  private arrowShaftColorBuffer: WebGLBuffer | null = null

  private arrowHeadProgram: WebGLProgram | null = null
  private arrowHeadVao: WebGLVertexArrayObject | null = null
  private arrowHeadPositionBuffer: WebGLBuffer | null = null
  private arrowHeadColorBuffer: WebGLBuffer | null = null

  private dashedLineProgram: WebGLProgram | null = null
  private dashedVao: WebGLVertexArrayObject | null = null
  private dashedPositionBuffer: WebGLBuffer | null = null
  private dashedNormalBuffer: WebGLBuffer | null = null
  private dashedUvBuffer: WebGLBuffer | null = null

  private animationStartTime: number = 0
  private animationFrameId: number | null = null
  private onAnimationFrame: (() => void) | null = null

  private initialized = false

  constructor(gl: WebGL2RenderingContext) {
    this.gl = gl
  }

  init(): void {
    if (this.initialized) return

    this.initArrowShaftProgram()
    this.initArrowHeadProgram()
    this.initDashedLineProgram()
    this.animationStartTime = performance.now()
    this.initialized = true
  }

  /**
   * Set callback for animation frame requests
   * This allows the renderer to request re-renders for animation
   */
  setAnimationCallback(callback: () => void): void {
    this.onAnimationFrame = callback
  }

  /**
   * Start the animation loop
   */
  startAnimation(): void {
    if (this.animationFrameId !== null) return

    const animate = () => {
      if (this.onAnimationFrame) {
        this.onAnimationFrame()
      }
      this.animationFrameId = requestAnimationFrame(animate)
    }

    this.animationFrameId = requestAnimationFrame(animate)
  }

  /**
   * Stop the animation loop
   */
  stopAnimation(): void {
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId)
      this.animationFrameId = null
    }
  }

  private initArrowShaftProgram(): void {
    const vsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      in vec2 a_position;
      in vec2 a_normal;
      in float a_uv;
      in vec4 a_color;
      
      uniform vec2 u_scale;
      uniform vec2 u_offset;
      uniform float u_zoom;
      uniform float u_thickness;
      uniform vec2 u_resolution;
      
      out float v_uv;
      out vec4 v_color;
      
      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        pos *= u_scale;
        
        vec2 pixelSize = ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_PIXEL_SCALE)} / u_resolution;
        vec2 offset = a_normal * u_thickness * pixelSize * ${glslFloatLiteral(WEBGL_GLSL.HALF)};
        
        gl_Position = vec4(pos + offset, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_Z)}, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_W)});
        v_uv = a_uv;
        v_color = a_color;
      }`

    const fsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      precision mediump float;
      
      in float v_uv;
      in vec4 v_color;
      
      uniform float u_dashLength;
      uniform float u_gapLength;
      uniform float u_timeOffset;
      
      out vec4 outColor;
      
      void main() {
        float animatedUv = v_uv - u_timeOffset;
        float pattern = mod(animatedUv, u_dashLength + u_gapLength);
        if (pattern > u_dashLength) {
          discard;
        }
        outColor = v_color;
      }`

    this.arrowShaftProgram = createProgram(this.gl, vsSource, fsSource)

    this.arrowShaftVao = this.gl.createVertexArray()
    this.gl.bindVertexArray(this.arrowShaftVao)

    this.arrowShaftPositionBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowShaftPositionBuffer)
    const aPos = this.gl.getAttribLocation(this.arrowShaftProgram, 'a_position')
    this.gl.enableVertexAttribArray(aPos)
    this.gl.vertexAttribPointer(aPos, WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS, this.gl.FLOAT, false, 0, 0)

    this.arrowShaftNormalBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowShaftNormalBuffer)
    const aNorm = this.gl.getAttribLocation(this.arrowShaftProgram, 'a_normal')
    this.gl.enableVertexAttribArray(aNorm)
    this.gl.vertexAttribPointer(aNorm, WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS, this.gl.FLOAT, false, 0, 0)

    this.arrowShaftUvBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowShaftUvBuffer)
    const aUv = this.gl.getAttribLocation(this.arrowShaftProgram, 'a_uv')
    this.gl.enableVertexAttribArray(aUv)
    this.gl.vertexAttribPointer(aUv, 1, this.gl.FLOAT, false, 0, 0)

    this.arrowShaftColorBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowShaftColorBuffer)
    const aColor = this.gl.getAttribLocation(this.arrowShaftProgram, 'a_color')
    this.gl.enableVertexAttribArray(aColor)
    this.gl.vertexAttribPointer(aColor, WEBGL_BUFFER_LAYOUT.VEC4_COMPONENTS, this.gl.FLOAT, false, 0, 0)

    this.gl.bindVertexArray(null)
  }

  private initArrowHeadProgram(): void {
    const vsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      in vec2 a_position;
      in vec4 a_color;
      
      uniform vec2 u_scale;
      uniform vec2 u_offset;
      uniform float u_zoom;
      
      out vec4 v_color;
      
      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        pos *= u_scale;
        
        gl_Position = vec4(pos, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_Z)}, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_W)});
        v_color = a_color;
      }`

    const fsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      precision mediump float;
      in vec4 v_color;
      out vec4 outColor;
      
      void main() {
        outColor = v_color;
      }`

    this.arrowHeadProgram = createProgram(this.gl, vsSource, fsSource)

    this.arrowHeadVao = this.gl.createVertexArray()
    this.gl.bindVertexArray(this.arrowHeadVao)

    this.arrowHeadPositionBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowHeadPositionBuffer)
    const aPos = this.gl.getAttribLocation(this.arrowHeadProgram, 'a_position')
    this.gl.enableVertexAttribArray(aPos)
    this.gl.vertexAttribPointer(aPos, WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS, this.gl.FLOAT, false, 0, 0)

    this.arrowHeadColorBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowHeadColorBuffer)
    const aColor = this.gl.getAttribLocation(this.arrowHeadProgram, 'a_color')
    this.gl.enableVertexAttribArray(aColor)
    this.gl.vertexAttribPointer(aColor, WEBGL_BUFFER_LAYOUT.VEC4_COMPONENTS, this.gl.FLOAT, false, 0, 0)

    this.gl.bindVertexArray(null)
  }

  private initDashedLineProgram(): void {
    const vsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      in vec2 a_position;
      in vec2 a_normal;
      in float a_uv;
      
      uniform vec2 u_scale;
      uniform vec2 u_offset;
      uniform float u_zoom;
      uniform float u_thickness;
      uniform vec2 u_resolution;
      
      out float v_uv;
      
      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        pos *= u_scale;
        
        vec2 pixelSize = ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_PIXEL_SCALE)} / u_resolution;
        vec2 offset = a_normal * u_thickness * pixelSize * ${glslFloatLiteral(WEBGL_GLSL.HALF)};
        
        gl_Position = vec4(pos + offset, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_Z)}, ${glslFloatLiteral(WEBGL_GLSL.CLIPSPACE_W)});
        v_uv = a_uv;
      }`

    const fsSource = `#version ${WEBGL_GLSL.VERSION} ${WEBGL_GLSL.ES_SUFFIX}
      precision mediump float;
      
      in float v_uv;
      uniform vec4 u_color;
      uniform float u_dashLength;
      uniform float u_gapLength;
      
      out vec4 outColor;
      
      void main() {
        float pattern = mod(v_uv, u_dashLength + u_gapLength);
        if (pattern > u_dashLength) {
          discard;
        }
        outColor = u_color;
      }`

    this.dashedLineProgram = createProgram(this.gl, vsSource, fsSource)

    this.dashedVao = this.gl.createVertexArray()
    this.gl.bindVertexArray(this.dashedVao)

    this.dashedPositionBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.dashedPositionBuffer)
    const aPosDashed = this.gl.getAttribLocation(this.dashedLineProgram, 'a_position')
    this.gl.enableVertexAttribArray(aPosDashed)
    this.gl.vertexAttribPointer(aPosDashed, WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS, this.gl.FLOAT, false, 0, 0)

    this.dashedNormalBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.dashedNormalBuffer)
    const aNormDashed = this.gl.getAttribLocation(this.dashedLineProgram, 'a_normal')
    this.gl.enableVertexAttribArray(aNormDashed)
    this.gl.vertexAttribPointer(aNormDashed, WEBGL_BUFFER_LAYOUT.VEC2_COMPONENTS, this.gl.FLOAT, false, 0, 0)

    this.dashedUvBuffer = this.gl.createBuffer()
    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.dashedUvBuffer)
    const aUvDashed = this.gl.getAttribLocation(this.dashedLineProgram, 'a_uv')
    this.gl.enableVertexAttribArray(aUvDashed)
    this.gl.vertexAttribPointer(aUvDashed, 1, this.gl.FLOAT, false, 0, 0)

    this.gl.bindVertexArray(null)
  }

  /**
   * Get current animation time offset
   */
  private getTimeOffset(): number {
    const elapsed = (performance.now() - this.animationStartTime) / 1000 // seconds
    return (elapsed * ANIMATION_SPEED) % (DASH_LENGTH + GAP_LENGTH)
  }

  /**
   * Draw reading order visualization
   */
  draw(
    data: ReadingOrderRenderData,
    scale: AspectRatioScale,
    view: View,
    canvasWidth: number,
    canvasHeight: number
  ): void {
    if (!this.initialized) return

    if (data.arrows.length > 0) {
      this.drawArrowShafts(data.arrows, scale, view, canvasWidth, canvasHeight)
      this.drawArrowHeads(data.arrows, scale, view, canvasWidth, canvasHeight)
    }

    if (data.groupBounds.length > 0) {
      this.drawGroupBounds(data.groupBounds, scale, view, canvasWidth, canvasHeight)
    }
  }

  /**
   * Draw animated dashed arrow shafts
   */
  private drawArrowShafts(
    arrows: ArrowSegment[],
    scale: AspectRatioScale,
    view: View,
    canvasWidth: number,
    canvasHeight: number
  ): void {
    if (!this.arrowShaftProgram || !this.arrowShaftVao) return

    const positions: number[] = []
    const normals: number[] = []
    const uvs: number[] = []
    const colors: number[] = []

    for (const arrow of arrows) {
      this.generateArrowShaftGeometry(arrow, positions, normals, uvs, colors, view.zoom, canvasWidth, canvasHeight)
    }

    if (positions.length === 0) return

    this.gl.useProgram(this.arrowShaftProgram)
    this.gl.bindVertexArray(this.arrowShaftVao)

    const scaleLocation = this.gl.getUniformLocation(this.arrowShaftProgram, 'u_scale')
    const offsetLocation = this.gl.getUniformLocation(this.arrowShaftProgram, 'u_offset')
    const zoomLocation = this.gl.getUniformLocation(this.arrowShaftProgram, 'u_zoom')
    const thicknessLocation = this.gl.getUniformLocation(this.arrowShaftProgram, 'u_thickness')
    const resolutionLocation = this.gl.getUniformLocation(this.arrowShaftProgram, 'u_resolution')
    const dashLengthLocation = this.gl.getUniformLocation(this.arrowShaftProgram, 'u_dashLength')
    const gapLengthLocation = this.gl.getUniformLocation(this.arrowShaftProgram, 'u_gapLength')
    const timeOffsetLocation = this.gl.getUniformLocation(this.arrowShaftProgram, 'u_timeOffset')

    this.gl.uniform2f(scaleLocation, scale.scaleX, scale.scaleY)
    this.gl.uniform2f(offsetLocation, view.offsetX, view.offsetY)
    this.gl.uniform1f(zoomLocation, view.zoom)
    this.gl.uniform1f(thicknessLocation, ARROW_SHAFT_THICKNESS)
    this.gl.uniform2f(resolutionLocation, canvasWidth, canvasHeight)
    this.gl.uniform1f(dashLengthLocation, DASH_LENGTH)
    this.gl.uniform1f(gapLengthLocation, GAP_LENGTH)
    this.gl.uniform1f(timeOffsetLocation, this.getTimeOffset())

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowShaftPositionBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowShaftNormalBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowShaftUvBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(uvs), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowShaftColorBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(colors), this.gl.DYNAMIC_DRAW)

    this.gl.enable(this.gl.BLEND)
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA)

    this.gl.drawArrays(this.gl.TRIANGLES, 0, positions.length / 2)

    this.gl.bindVertexArray(null)
  }

  /**
   * Draw filled triangular arrowheads
   */
  private drawArrowHeads(
    arrows: ArrowSegment[],
    scale: AspectRatioScale,
    view: View,
    canvasWidth: number,
    canvasHeight: number
  ): void {
    if (!this.arrowHeadProgram || !this.arrowHeadVao) return

    const positions: number[] = []
    const colors: number[] = []

    for (const arrow of arrows) {
      this.generateArrowHeadGeometry(arrow, positions, colors, view.zoom, canvasWidth, canvasHeight)
    }

    if (positions.length === 0) return

    this.gl.useProgram(this.arrowHeadProgram)
    this.gl.bindVertexArray(this.arrowHeadVao)

    const scaleLocation = this.gl.getUniformLocation(this.arrowHeadProgram, 'u_scale')
    const offsetLocation = this.gl.getUniformLocation(this.arrowHeadProgram, 'u_offset')
    const zoomLocation = this.gl.getUniformLocation(this.arrowHeadProgram, 'u_zoom')

    this.gl.uniform2f(scaleLocation, scale.scaleX, scale.scaleY)
    this.gl.uniform2f(offsetLocation, view.offsetX, view.offsetY)
    this.gl.uniform1f(zoomLocation, view.zoom)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowHeadPositionBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.arrowHeadColorBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(colors), this.gl.DYNAMIC_DRAW)

    this.gl.enable(this.gl.BLEND)
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA)

    this.gl.drawArrays(this.gl.TRIANGLES, 0, positions.length / 2)

    this.gl.bindVertexArray(null)
  }

  /**
   * Generate geometry for arrow shaft (thick dashed line)
   */
  private generateArrowShaftGeometry(
    arrow: ArrowSegment,
    positions: number[],
    normals: number[],
    uvs: number[],
    colors: number[],
    zoom: number,
    canvasWidth: number,
    canvasHeight: number
  ): void {
    const { from, to, color } = arrow

    const dx = to.x - from.x
    const dy = to.y - from.y
    const len = Math.sqrt(dx * dx + dy * dy)

    if (len < WEBGL_EPSILON.MIN_SEGMENT_LENGTH) return

    const dirX = dx / len
    const dirY = dy / len

    const headLenWorld = ARROW_HEAD_LENGTH / (zoom * Math.max(canvasWidth, canvasHeight) * 0.5)

    const shaftEnd = {
      x: to.x - dirX * headLenWorld * 0.8, // Slightly overlap for clean connection
      y: to.y - dirY * headLenWorld * 0.8
    }

    const normalVec = normalize(-dirY, dirX)

    const segmentLenPx = len * zoom * Math.max(canvasWidth, canvasHeight) * 0.5

    positions.push(from.x, from.y)
    normals.push(normalVec.x, normalVec.y)
    uvs.push(0)
    colors.push(...color)

    positions.push(from.x, from.y)
    normals.push(-normalVec.x, -normalVec.y)
    uvs.push(0)
    colors.push(...color)

    positions.push(shaftEnd.x, shaftEnd.y)
    normals.push(normalVec.x, normalVec.y)
    uvs.push(segmentLenPx)
    colors.push(...color)

    positions.push(from.x, from.y)
    normals.push(-normalVec.x, -normalVec.y)
    uvs.push(0)
    colors.push(...color)

    positions.push(shaftEnd.x, shaftEnd.y)
    normals.push(-normalVec.x, -normalVec.y)
    uvs.push(segmentLenPx)
    colors.push(...color)

    positions.push(shaftEnd.x, shaftEnd.y)
    normals.push(normalVec.x, normalVec.y)
    uvs.push(segmentLenPx)
    colors.push(...color)
  }

  /**
   * Generate geometry for arrow head (filled triangle)
   */
  private generateArrowHeadGeometry(
    arrow: ArrowSegment,
    positions: number[],
    colors: number[],
    zoom: number,
    canvasWidth: number,
    canvasHeight: number
  ): void {
    const { from, to, color } = arrow

    const dx = to.x - from.x
    const dy = to.y - from.y
    const len = Math.sqrt(dx * dx + dy * dy)

    if (len < WEBGL_EPSILON.MIN_SEGMENT_LENGTH) return

    const dirX = dx / len
    const dirY = dy / len

    const headLenWorld = ARROW_HEAD_LENGTH / (zoom * Math.max(canvasWidth, canvasHeight) * 0.5)
    const headWidthWorld = ARROW_HEAD_WIDTH / (zoom * Math.max(canvasWidth, canvasHeight) * 0.5)

    const baseCenter = {
      x: to.x - dirX * headLenWorld,
      y: to.y - dirY * headLenWorld
    }

    const normalX = -dirY
    const normalY = dirX

    const baseLeft = {
      x: baseCenter.x + normalX * headWidthWorld * 0.5,
      y: baseCenter.y + normalY * headWidthWorld * 0.5
    }
    const baseRight = {
      x: baseCenter.x - normalX * headWidthWorld * 0.5,
      y: baseCenter.y - normalY * headWidthWorld * 0.5
    }

    positions.push(to.x, to.y)
    colors.push(...color)

    positions.push(baseLeft.x, baseLeft.y)
    colors.push(...color)

    positions.push(baseRight.x, baseRight.y)
    colors.push(...color)
  }

  /**
   * Draw dashed bounding boxes around groups
   */
  private drawGroupBounds(
    bounds: GroupBounds[],
    scale: AspectRatioScale,
    view: View,
    canvasWidth: number,
    canvasHeight: number
  ): void {
    if (!this.dashedLineProgram || !this.dashedVao) return

    for (const bound of bounds) {
      this.drawDashedBox(bound, scale, view, canvasWidth, canvasHeight)
    }
  }

  /**
   * Draw a single dashed bounding box
   */
  private drawDashedBox(
    bound: GroupBounds,
    scale: AspectRatioScale,
    view: View,
    canvasWidth: number,
    canvasHeight: number
  ): void {
    if (bound.points.length < 4) return

    const positions: number[] = []
    const normals: number[] = []
    const uvs: number[] = []

    let cumulativeDistance = 0

    for (let i = 0; i < bound.points.length; i++) {
      const p1 = bound.points[i]!
      const p2 = bound.points[(i + 1) % bound.points.length]!

      const dx = p2.x - p1.x
      const dy = p2.y - p1.y
      const segmentLen = Math.sqrt(dx * dx + dy * dy)

      if (segmentLen < WEBGL_EPSILON.MIN_SEGMENT_LENGTH) continue

      const normalVec = normalize(-dy / segmentLen, dx / segmentLen)

      const segmentLenPx = segmentLen * view.zoom * Math.max(canvasWidth, canvasHeight) * 0.5

      positions.push(p1.x, p1.y)
      normals.push(normalVec.x, normalVec.y)
      uvs.push(cumulativeDistance)

      positions.push(p1.x, p1.y)
      normals.push(-normalVec.x, -normalVec.y)
      uvs.push(cumulativeDistance)

      positions.push(p2.x, p2.y)
      normals.push(normalVec.x, normalVec.y)
      uvs.push(cumulativeDistance + segmentLenPx)

      positions.push(p1.x, p1.y)
      normals.push(-normalVec.x, -normalVec.y)
      uvs.push(cumulativeDistance)

      positions.push(p2.x, p2.y)
      normals.push(-normalVec.x, -normalVec.y)
      uvs.push(cumulativeDistance + segmentLenPx)

      positions.push(p2.x, p2.y)
      normals.push(normalVec.x, normalVec.y)
      uvs.push(cumulativeDistance + segmentLenPx)

      cumulativeDistance += segmentLenPx
    }

    if (positions.length === 0) return

    this.gl.useProgram(this.dashedLineProgram)
    this.gl.bindVertexArray(this.dashedVao)

    const scaleLocation = this.gl.getUniformLocation(this.dashedLineProgram, 'u_scale')
    const offsetLocation = this.gl.getUniformLocation(this.dashedLineProgram, 'u_offset')
    const zoomLocation = this.gl.getUniformLocation(this.dashedLineProgram, 'u_zoom')
    const thicknessLocation = this.gl.getUniformLocation(this.dashedLineProgram, 'u_thickness')
    const resolutionLocation = this.gl.getUniformLocation(this.dashedLineProgram, 'u_resolution')
    const colorLocation = this.gl.getUniformLocation(this.dashedLineProgram, 'u_color')
    const dashLengthLocation = this.gl.getUniformLocation(this.dashedLineProgram, 'u_dashLength')
    const gapLengthLocation = this.gl.getUniformLocation(this.dashedLineProgram, 'u_gapLength')

    this.gl.uniform2f(scaleLocation, scale.scaleX, scale.scaleY)
    this.gl.uniform2f(offsetLocation, view.offsetX, view.offsetY)
    this.gl.uniform1f(zoomLocation, view.zoom)
    this.gl.uniform1f(thicknessLocation, GROUP_BOUNDS_THICKNESS)
    this.gl.uniform2f(resolutionLocation, canvasWidth, canvasHeight)
    this.gl.uniform4f(colorLocation, bound.color[0], bound.color[1], bound.color[2], bound.color[3])
    this.gl.uniform1f(dashLengthLocation, GROUP_BOUNDS_DASH_LENGTH)
    this.gl.uniform1f(gapLengthLocation, GROUP_BOUNDS_GAP_LENGTH)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.dashedPositionBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.dashedNormalBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.DYNAMIC_DRAW)

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.dashedUvBuffer!)
    this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(uvs), this.gl.DYNAMIC_DRAW)

    this.gl.enable(this.gl.BLEND)
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA)

    this.gl.drawArrays(this.gl.TRIANGLES, 0, positions.length / 2)

    this.gl.bindVertexArray(null)
  }

  cleanup(): void {
    this.stopAnimation()

    if (this.arrowShaftVao) this.gl.deleteVertexArray(this.arrowShaftVao)
    if (this.arrowShaftPositionBuffer) this.gl.deleteBuffer(this.arrowShaftPositionBuffer)
    if (this.arrowShaftNormalBuffer) this.gl.deleteBuffer(this.arrowShaftNormalBuffer)
    if (this.arrowShaftUvBuffer) this.gl.deleteBuffer(this.arrowShaftUvBuffer)
    if (this.arrowShaftColorBuffer) this.gl.deleteBuffer(this.arrowShaftColorBuffer)
    if (this.arrowShaftProgram) this.gl.deleteProgram(this.arrowShaftProgram)

    if (this.arrowHeadVao) this.gl.deleteVertexArray(this.arrowHeadVao)
    if (this.arrowHeadPositionBuffer) this.gl.deleteBuffer(this.arrowHeadPositionBuffer)
    if (this.arrowHeadColorBuffer) this.gl.deleteBuffer(this.arrowHeadColorBuffer)
    if (this.arrowHeadProgram) this.gl.deleteProgram(this.arrowHeadProgram)

    if (this.dashedVao) this.gl.deleteVertexArray(this.dashedVao)
    if (this.dashedPositionBuffer) this.gl.deleteBuffer(this.dashedPositionBuffer)
    if (this.dashedNormalBuffer) this.gl.deleteBuffer(this.dashedNormalBuffer)
    if (this.dashedUvBuffer) this.gl.deleteBuffer(this.dashedUvBuffer)
    if (this.dashedLineProgram) this.gl.deleteProgram(this.dashedLineProgram)

    this.initialized = false
  }
}
