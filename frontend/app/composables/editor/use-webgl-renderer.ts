import { ThickLineRenderer } from '@/webgl/editor/thick-line-renderer'
import { DashedLineRenderer } from '@/webgl/editor/dashed-line-renderer'
import { SelectionOverlayRenderer } from '@/webgl/editor/selection-overlay'
import { ResourcePool } from '@/webgl/editor/resource-pool'
import { PolygonRenderer } from '@/webgl/editor/polygon-renderer'
import { FillRenderer } from '@/webgl/editor/fill-renderer'
import { BatchedLineRenderer } from '@/webgl/editor/batched-line-renderer'
import { TextureManager } from '@/webgl/editor/texture-manager'
import { ShaderProgramManager } from '@/webgl/editor/shader-program-manager'
import { GeometryCache } from '@/webgl/editor/geometry-cache'
import { ReadingOrderRenderer } from '@/webgl/editor/reading-order-renderer'
import { PolygonType } from '@/models/editor'
import { visibilityService } from '@/services/editor/visibility-service'
import { getColorForLabel, getStrokeColorForLabel } from '@/utils/editor/label-utils'
import { computeElementConfidence, confidenceToHeatRgba, scaleConfidenceForHeatmap } from '@/utils/editor/confidence-heatmap'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { getEditorSession } from '@/session/editor/editor-session'
import type { View, ImageSize, AspectRatioScale, Point, PcGts as DocumentModel } from '@/models/editor'
import { RASTER_IMAGE_SHADOW, RENDER_SIZES, CANVAS_BACKGROUND, RENDER_THICKNESS, RENDER_COLORS, BACKGROUND_ELEMENT, AUTO_PARENT_INDICATOR, RENDER_ALPHA, LINE_WIDTH_PRESETS, type RGBA } from '@/utils/editor/editor-constants'
import { WEBGL_CORE, WEBGL_GEOMETRY } from '@/webgl/editor/webgl-constants'
import type { RenderablePolygon, RenderablePolyline, WebGLRenderState, ViewMode } from '@/types/editor/rendering'
import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('WebGLRenderer')

type RenderState = WebGLRenderState

function normalizeViewMode(raw: string | undefined): ViewMode | undefined {
  if (raw === 'default' || raw === 'textline' || raw === 'baseline') return raw
  return undefined
}

interface GeometryCacheStats {
  size: number
  version: number
}

export interface UseWebGLRendererReturn {
  gl: () => WebGL2RenderingContext | null
  imageSize: Ref<ImageSize>

  initGL: (triangulatePolygon?: (points: Point[]) => number[]) => void

  renderFrame: (
    renderState: WebGLRenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ) => void
  stopRenderLoop: () => void

  loadAndRender: (src: string) => Promise<void>

  invalidateGeometry: (polygonId: string) => void
  invalidateMultipleGeometry: (polygonIds: string[]) => void
  clearGeometryCache: () => void
  pruneGeometryCache: (activePolygonIds: Set<string>) => void
  getGeometryCacheStats: () => GeometryCacheStats | null
  startReadingOrderAnimation: () => void
  stopReadingOrderAnimation: () => void

  cleanup: () => void
}

/**
 * Main WebGL renderer composable - orchestrates all rendering components
 */
export function useWebglRenderer(canvasRef: Ref<HTMLCanvasElement | null>): UseWebGLRendererReturn {
  const editorUiStore = useEditorUiStore()

  let gl: WebGL2RenderingContext | null = null
  let resourcePool: ResourcePool | null = null
  let shaderManager: ShaderProgramManager | null = null
  let textureManager: TextureManager | null = null
  let geometryCache: GeometryCache | null = null

  let polygonRenderer: PolygonRenderer | null = null
  let fillRenderer: FillRenderer | null = null
  let batchedLineRenderer: BatchedLineRenderer | null = null
  let thickLineRenderer: ThickLineRenderer | null = null
  let dashedLineRenderer: DashedLineRenderer | null = null
  let selectionOverlayRenderer: SelectionOverlayRenderer | null = null
  let readingOrderRenderer: ReadingOrderRenderer | null = null

  let animationFrameId: number | null = null

  let imageProgram: WebGLProgram | null = null
  let imageTexture: WebGLTexture | null = null
  let imageVao: WebGLVertexArrayObject | null = null
  const imageSize = ref<ImageSize>({ width: 1, height: 1 })

  function getActiveDocument(): DocumentModel | undefined {
    const editorStore = useEditorStore()
    const canvasId = editorStore.activeCanvasId
    if (!canvasId) return undefined
    const session = getEditorSession(canvasId)
    return session?.document.value ?? undefined
  }

  function getActiveLabelSet() {
    const editorStore = useEditorStore()
    return editorStore.labelSet ?? null
  }

  function getLineWidth(): number {
    const editorUiStore = useEditorUiStore()
    const preset = editorUiStore.globalSettings.defaultLineWidth
    return LINE_WIDTH_PRESETS[preset] ?? LINE_WIDTH_PRESETS.normal
  }

  function getRotationForScale(scale: AspectRatioScale): { rotationCos: number, rotationSin: number } {
    const rotationCos = (typeof scale.rotationCos === 'number' && isFinite(scale.rotationCos)) ? scale.rotationCos : 1
    const rotationSin = (typeof scale.rotationSin === 'number' && isFinite(scale.rotationSin)) ? scale.rotationSin : 0
    return { rotationCos, rotationSin }
  }

  function setProgramRotation(program: WebGLProgram, scale: AspectRatioScale): void {
    if (!gl) return
    const rotationLocation = gl.getUniformLocation(program, 'u_rotation')
    if (rotationLocation) {
      const { rotationCos, rotationSin } = getRotationForScale(scale)
      gl.uniform2f(rotationLocation, rotationCos, rotationSin)
    }

    const canvasAspectLocation = gl.getUniformLocation(program, 'u_canvasAspect')
    if (canvasAspectLocation) {
      const fallbackAspect = (gl.canvas.width > 0 && gl.canvas.height > 0) ? (gl.canvas.width / gl.canvas.height) : 1
      const rotationAspect = (typeof scale.rotationAspect === 'number' && isFinite(scale.rotationAspect) && scale.rotationAspect > 0)
        ? scale.rotationAspect
        : fallbackAspect
      gl.uniform1f(canvasAspectLocation, rotationAspect)
    }
  }

  let polygonProgram: WebGLProgram | null = null
  let polygonVao: WebGLVertexArrayObject | null = null
  let polygonBuffer: WebGLBuffer | null = null
  let fillProgram: WebGLProgram | null = null
  let actionProcessingProgram: WebGLProgram | null = null

  /**
   * Initialize the WebGL renderer and all subsystems
   */
  function initGL(triangulatePolygon?: (points: Point[]) => number[]): void {
    try {
      const canvas = canvasRef.value
      if (!canvas) throw new Error('Canvas element is not available')
      const context = canvas.getContext('webgl2', { stencil: true, alpha: true })
      if (!context) throw new Error('WebGL 2 not supported')
      gl = context

      resourcePool = new ResourcePool(gl)
      shaderManager = new ShaderProgramManager(gl)
      textureManager = new TextureManager(gl)

      if (triangulatePolygon) {
        geometryCache = new GeometryCache(triangulatePolygon)
      }

      initShaderPrograms()

      const lineProgram = shaderManager.getProgram('line')
      if (!lineProgram) throw new Error('Failed to get line program')

      if (!fillProgram || !resourcePool) {
        throw new Error('Required resources not initialized')
      }

      fillRenderer = new FillRenderer(gl, fillProgram, resourcePool, actionProcessingProgram)
      batchedLineRenderer = new BatchedLineRenderer(gl, lineProgram, resourcePool)

      if (!polygonProgram) throw new Error('Polygon program not initialized')
      polygonRenderer = new PolygonRenderer(gl, polygonProgram, resourcePool)

      thickLineRenderer = new ThickLineRenderer(gl)
      thickLineRenderer.init()

      dashedLineRenderer = new DashedLineRenderer(gl)
      dashedLineRenderer.init()

      selectionOverlayRenderer = new SelectionOverlayRenderer(gl)
      selectionOverlayRenderer.init()

      readingOrderRenderer = new ReadingOrderRenderer(gl)
      readingOrderRenderer.init()

      readingOrderRenderer.setAnimationCallback(() => {
      })
    } catch (error) {
      log.error('Failed to initialize WebGL renderer:', error)
      throw error
    }
  }

  /**
   * Initialize all shader programs and WebGL resources
   */
  function initShaderPrograms(): void {
    if (!gl || !shaderManager) return

    const imageVsSource = `#version 300 es
      in vec2 a_position; in vec2 a_uv; out vec2 v_uv;
      uniform vec2 u_scale; uniform vec2 u_offset; uniform float u_zoom; uniform vec2 u_rotation; uniform float u_canvasAspect;
      uniform vec2 u_resolution; uniform vec2 u_pixelOffset; uniform vec2 u_shadowExpand;
      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        vec2 scaled = pos * u_scale;
        vec2 clip = vec2(
          scaled.x * u_rotation.x - (scaled.y * u_rotation.y) / u_canvasAspect,
          scaled.x * u_rotation.y * u_canvasAspect + scaled.y * u_rotation.x
        );
        vec2 pixelSize = 2.0 / u_resolution;
        vec2 expand = vec2(
          a_position.x * u_shadowExpand.x * pixelSize.x,
          a_position.y * u_shadowExpand.y * pixelSize.y
        );
        vec2 clipExpand = vec2(
          expand.x * u_rotation.x - (expand.y * u_rotation.y) / u_canvasAspect,
          expand.x * u_rotation.y * u_canvasAspect + expand.y * u_rotation.x
        );
        vec2 clipOffset = vec2(
          (u_pixelOffset.x * 2.0) / u_resolution.x,
          (-u_pixelOffset.y * 2.0) / u_resolution.y
        );
        gl_Position = vec4(clip + clipOffset + clipExpand, 0.0, 1.0);
        v_uv = a_uv;
      }`
    const imageFsSource = `#version 300 es
      precision mediump float;
      in vec2 v_uv;
      uniform sampler2D u_tex;
      uniform float u_renderShadow;
      uniform vec4 u_shadowColor;
      uniform vec2 u_shadowBlur;
      uniform vec2 u_shadowInset;
      uniform vec2 u_shadowQuadSize;
      out vec4 outColor;

      float getRectangleShadowAlpha() {
        vec2 rectMin = u_shadowInset;
        vec2 rectMax = vec2(1.0) - u_shadowInset;
        vec2 outsideUv = max(max(rectMin - v_uv, v_uv - rectMax), vec2(0.0));
        vec2 outsidePx = outsideUv * u_shadowQuadSize;
        vec2 blur = max(u_shadowBlur, vec2(1.0));
        float normalizedDistance = length(outsidePx / blur);
        return exp(-0.5 * normalizedDistance * normalizedDistance);
      }

      void main() {
        vec4 texel = texture(u_tex, v_uv);
        outColor = u_renderShadow > 0.5
          ? vec4(u_shadowColor.rgb, getRectangleShadowAlpha() * u_shadowColor.a)
          : texel;
      }`

    imageProgram = shaderManager.registerProgram('image', imageVsSource, imageFsSource)

    const imageData = new Float32Array([-1, -1, 0, 0, 1, -1, 1, 0, -1, 1, 0, 1, -1, 1, 0, 1, 1, -1, 1, 0, 1, 1, 1, 1])
    imageVao = gl.createVertexArray()
    gl.bindVertexArray(imageVao)
    const imageBuffer = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, imageBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, imageData, gl.STATIC_DRAW)
    const aPosImg = gl.getAttribLocation(imageProgram, 'a_position')
    const aUvImg = gl.getAttribLocation(imageProgram, 'a_uv')
    gl.enableVertexAttribArray(aPosImg)
    gl.vertexAttribPointer(aPosImg, 2, gl.FLOAT, false, 16, 0)
    gl.enableVertexAttribArray(aUvImg)
    gl.vertexAttribPointer(aUvImg, 2, gl.FLOAT, false, 16, 8)

    imageTexture = gl.createTexture()
    gl.bindTexture(gl.TEXTURE_2D, imageTexture)
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(CANVAS_BACKGROUND))
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)

    const polygonVsSource = `#version 300 es
      in vec2 a_position;
      uniform vec2 u_scale; uniform vec2 u_offset; uniform float u_zoom; uniform vec2 u_rotation; uniform float u_canvasAspect;
      uniform float u_pointSize;
      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        vec2 scaled = pos * u_scale;
        vec2 clip = vec2(
          scaled.x * u_rotation.x - (scaled.y * u_rotation.y) / u_canvasAspect,
          scaled.x * u_rotation.y * u_canvasAspect + scaled.y * u_rotation.x
        );
        gl_Position = vec4(clip, 0.0, 1.0);
        gl_PointSize = u_pointSize;
      }`
    const polygonFsSource = `#version 300 es
      precision mediump float; uniform vec4 u_color; out vec4 outColor;
      void main() { outColor = u_color; }`

    polygonProgram = shaderManager.registerProgram('polygon', polygonVsSource, polygonFsSource)

    const lineVsSource = `#version 300 es
      in vec2 a_position;
      in vec2 a_normal;
      in vec4 a_color;
      uniform vec2 u_scale;
      uniform vec2 u_offset;
      uniform float u_zoom;
      uniform vec2 u_rotation;
      uniform float u_canvasAspect;
      uniform float u_thickness;
      uniform vec2 u_resolution;
      out vec4 v_color;

      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        vec2 scaled = pos * u_scale;
        vec2 clip = vec2(
          scaled.x * u_rotation.x - (scaled.y * u_rotation.y) / u_canvasAspect,
          scaled.x * u_rotation.y * u_canvasAspect + scaled.y * u_rotation.x
        );
        
        vec2 pixelSize = 2.0 / u_resolution;
        vec2 offset = a_normal * u_thickness * pixelSize * 0.5;
        vec2 clipOffset = vec2(
          offset.x * u_rotation.x - (offset.y * u_rotation.y) / u_canvasAspect,
          offset.x * u_rotation.y * u_canvasAspect + offset.y * u_rotation.x
        );
        
        gl_Position = vec4(clip + clipOffset, 0.0, 1.0);
        v_color = a_color;
      }`
    const lineFsSource = `#version 300 es
      precision mediump float;
      in vec4 v_color;
      uniform vec4 u_color;
      out vec4 outColor;
      void main() { 
        outColor = v_color.a > 0.0 ? v_color : u_color;
      }`

    shaderManager.registerProgram('line', lineVsSource, lineFsSource)

    const fillVsSource = `#version 300 es
      in vec2 a_position;
      uniform vec2 u_scale; uniform vec2 u_offset; uniform float u_zoom; uniform vec2 u_rotation; uniform float u_canvasAspect;
      void main() {
        vec2 pos = (a_position * u_zoom) + u_offset;
        vec2 scaled = pos * u_scale;
        vec2 clip = vec2(
          scaled.x * u_rotation.x - (scaled.y * u_rotation.y) / u_canvasAspect,
          scaled.x * u_rotation.y * u_canvasAspect + scaled.y * u_rotation.x
        );
        gl_Position = vec4(clip, 0.0, 1.0);
      }`
    const fillFsSource = `#version 300 es
      precision mediump float; uniform vec4 u_color; out vec4 outColor;
      void main() { outColor = u_color; }`

    fillProgram = shaderManager.registerProgram('fill', fillVsSource, fillFsSource)

    const actionProcessingVsSource = `#version 300 es
      in vec2 a_position;
      out vec2 v_world;
      uniform vec2 u_scale; uniform vec2 u_offset; uniform float u_zoom; uniform vec2 u_rotation; uniform float u_canvasAspect;
      void main() {
        v_world = a_position;
        vec2 pos = (a_position * u_zoom) + u_offset;
        vec2 scaled = pos * u_scale;
        vec2 clip = vec2(
          scaled.x * u_rotation.x - (scaled.y * u_rotation.y) / u_canvasAspect,
          scaled.x * u_rotation.y * u_canvasAspect + scaled.y * u_rotation.x
        );
        gl_Position = vec4(clip, 0.0, 1.0);
      }`
    const actionProcessingFsSource = `#version 300 es
      precision mediump float;
      in vec2 v_world;
      uniform float u_time;
      uniform float u_intensity;
      uniform vec4 u_bounds;
      out vec4 outColor;

      void main() {
        vec2 uv = (v_world - u_bounds.xy) / max(u_bounds.zw, vec2(0.0001));
        vec2 center = uv - vec2(0.5);
        float distanceFromCenter = length(center);
        float pulse = 0.5 + 0.5 * sin(u_time * 2.8);
        float scan = fract((uv.x + uv.y) * 0.5 - u_time * 0.28);
        float scanBand = smoothstep(0.42, 0.5, scan) * (1.0 - smoothstep(0.5, 0.64, scan));
        float vignette = smoothstep(0.78, 0.12, distanceFromCenter);

        vec3 base = mix(vec3(0.0, 0.62, 1.0), vec3(0.72, 0.16, 1.0), clamp(uv.x * 0.65 + uv.y * 0.35, 0.0, 1.0));
        vec3 glow = vec3(0.45, 0.95, 1.0) * (0.28 + 0.35 * pulse * vignette + 0.58 * scanBand);
        float alpha = (0.2 + pulse * 0.14 + scanBand * 0.16) * u_intensity;

        outColor = vec4(base + glow, min(alpha, 0.68));
      }`

    actionProcessingProgram = shaderManager.registerProgram('action-processing-fill', actionProcessingVsSource, actionProcessingFsSource)

    polygonVao = gl.createVertexArray()
    gl.bindVertexArray(polygonVao)
    polygonBuffer = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, polygonBuffer)
    const aPosPoly = gl.getAttribLocation(polygonProgram, 'a_position')
    gl.enableVertexAttribArray(aPosPoly)
    gl.vertexAttribPointer(aPosPoly, 2, gl.FLOAT, false, 8, 0)
  }

  /**
   * Render image
   */
  function drawImage(aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale, view: View): void {
    if (!gl || !imageProgram || !imageVao || !imageTexture) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    if (!scale || scale.scaleX === 0 || scale.scaleY === 0 || !isFinite(scale.scaleX) || !isFinite(scale.scaleY)) {
      return
    }

    gl.bindVertexArray(null)

    gl.useProgram(imageProgram)
    gl.bindVertexArray(imageVao)
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, imageTexture)
    gl.enable(gl.BLEND)
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_scale'), scale.scaleX, scale.scaleY)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_offset'), view.offsetX, view.offsetY)
    gl.uniform1f(gl.getUniformLocation(imageProgram, 'u_zoom'), view.zoom)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_resolution'), gl.canvas.width, gl.canvas.height)
    setProgramRotation(imageProgram, scale)

    const displayZoom = Math.abs(view.zoom) || 1
    const imageWidthPx = Math.max(Math.abs(scale.scaleX) * displayZoom * gl.canvas.width, 1)
    const imageHeightPx = Math.max(Math.abs(scale.scaleY) * displayZoom * gl.canvas.height, 1)
    const maxBlurX = imageWidthPx * RASTER_IMAGE_SHADOW.MAX_RELATIVE_BLUR
    const maxBlurY = imageHeightPx * RASTER_IMAGE_SHADOW.MAX_RELATIVE_BLUR
    const effectiveBlurX = Math.min(RASTER_IMAGE_SHADOW.BLUR_X, maxBlurX)
    const effectiveBlurY = Math.min(RASTER_IMAGE_SHADOW.BLUR_Y, maxBlurY)
    const effectiveOffsetX = Math.min(RASTER_IMAGE_SHADOW.OFFSET_X, imageWidthPx * RASTER_IMAGE_SHADOW.MAX_RELATIVE_OFFSET)
    const effectiveOffsetY = Math.min(RASTER_IMAGE_SHADOW.OFFSET_Y, imageHeightPx * RASTER_IMAGE_SHADOW.MAX_RELATIVE_OFFSET)
    const shadowExpandX = effectiveBlurX * 2.0
    const shadowExpandY = effectiveBlurY * 2.0
    const shadowQuadWidth = imageWidthPx + shadowExpandX * 2.0
    const shadowQuadHeight = imageHeightPx + shadowExpandY * 2.0
    const shadowInsetX = shadowExpandX / shadowQuadWidth
    const shadowInsetY = shadowExpandY / shadowQuadHeight

    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_pixelOffset'), effectiveOffsetX, effectiveOffsetY)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_shadowExpand'), shadowExpandX, shadowExpandY)
    gl.uniform1f(gl.getUniformLocation(imageProgram, 'u_renderShadow'), 1.0)
    gl.uniform4f(gl.getUniformLocation(imageProgram, 'u_shadowColor'), 0.0, 0.0, 0.0, RASTER_IMAGE_SHADOW.ALPHA)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_shadowBlur'), effectiveBlurX, effectiveBlurY)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_shadowInset'), shadowInsetX, shadowInsetY)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_shadowQuadSize'), shadowQuadWidth, shadowQuadHeight)
    gl.drawArrays(gl.TRIANGLES, 0, 6)

    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_pixelOffset'), 0.0, 0.0)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_shadowExpand'), 0.0, 0.0)
    gl.uniform1f(gl.getUniformLocation(imageProgram, 'u_renderShadow'), 0.0)
    gl.uniform4f(gl.getUniformLocation(imageProgram, 'u_shadowColor'), 0.0, 0.0, 0.0, 0.0)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_shadowBlur'), 0.0, 0.0)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_shadowInset'), 0.0, 0.0)
    gl.uniform2f(gl.getUniformLocation(imageProgram, 'u_shadowQuadSize'), 1.0, 1.0)

    gl.drawArrays(gl.TRIANGLES, 0, 6)

    gl.disable(gl.BLEND)
    gl.bindVertexArray(null)
  }

  /**
   * Draw thick line (using ThickLineRenderer - tested and stable)
   */
  function drawThickLine(
    points: Point[],
    color: readonly number[],
    thickness: number,
    isClosed: boolean,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View
  ): void {
    if (!thickLineRenderer || points.length < 2) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    thickLineRenderer.drawThickLine(points, color, thickness, isClosed, scale, view)
  }

  /**
   * Get triangulation with caching (if available)
   * Falls back to direct triangulation if cache is not initialized
   */
  function getCachedTriangulation(polygon: RenderablePolygon, triangulatePolygon: (points: Point[]) => number[]): number[] {
    if (geometryCache && polygon.id) {
      return geometryCache.getTriangulation(polygon.id, polygon.points)
    }
    return triangulatePolygon(polygon.points)
  }

  function getGlCanvasElement(): HTMLCanvasElement | null {
    if (!gl) return null
    return gl.canvas instanceof HTMLCanvasElement ? gl.canvas : null
  }

  /**
   * Draw hover polygons
   */
  function drawHoverPolygons(
    polygons: RenderablePolygon[],
    hoveredPolygonIndex: Ref<number>,
    selectedPolygonIndex: Ref<number>,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[],
    viewMode?: string,
    hiddenPolygonIds?: Set<string>
  ): void {
    if (!fillRenderer) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    const tempHoverId = editorUiStore.temporaryHoverPolygonId
    let polygonToHover: RenderablePolygon | undefined

    if (tempHoverId) {
      polygonToHover = polygons.find(p => p.id === tempHoverId)
    } else if (hoveredPolygonIndex.value >= 0 && hoveredPolygonIndex.value !== selectedPolygonIndex.value) {
      polygonToHover = polygons[hoveredPolygonIndex.value]
    }

    if (!polygonToHover) return
    if (hiddenPolygonIds?.has(polygonToHover.id)) return

    const polygonIndex = polygons.findIndex(p => p.id === polygonToHover!.id)
    if (polygonIndex === selectedPolygonIndex.value) return

    if (!tempHoverId) {
      let shouldRenderHover = true

      const hasSelection = selectedPolygonIndex.value >= 0
      const isInViewMode = viewMode && viewMode !== 'default'

      if (polygonToHover.parentId && !isInViewMode) {
        const parentIndex = polygons.findIndex(p => p.id === polygonToHover!.parentId)
        shouldRenderHover = parentIndex === selectedPolygonIndex.value
      } else if (polygonToHover.parentId && isInViewMode && hasSelection) {
        const parentIndex = polygons.findIndex(p => p.id === polygonToHover!.parentId)
        shouldRenderHover = parentIndex === selectedPolygonIndex.value
      }

      if (!shouldRenderHover) return
    }

    const hoveredPoly = polygonToHover.points
    const triangleIndices = getCachedTriangulation(polygonToHover, triangulatePolygon)

    const document = getActiveDocument()
    const hoverColor = getColorForLabel(polygonToHover.label, document, getActiveLabelSet(), polygonToHover.regionKind, polygonToHover.regionSubtype, polygonToHover.regionCustom)

    if (fillRenderer) {
      fillRenderer.drawFill(hoveredPoly, triangleIndices, hoverColor, scale, view)
    }
  }

  /**
   * Draw filled polygons with colors based on their labels
   */
  function drawPolygonFills(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!fillRenderer) return
    if (!editorUiStore.globalSettings.showPolygonLabelFill) return
    if (renderState.confidenceHeatmap?.enabled) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    const document = getActiveDocument()
    const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
    const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)

    renderState.polygons.forEach((polygon, polygonIndex) => {
      if (polygonIndex === renderState.selectedPolygonIndex.value) {
        return
      }
      if (renderState.selectedPolygonIds.value.includes(polygon.id)) {
        return
      }
      if (polygon.type !== PolygonType.REGION && polygon.type !== PolygonType.TEXTLINE) {
        return
      }

      if (
        !visibilityService.shouldShowPolygon(polygon, {
          selectedPolygonIndex: renderState.selectedPolygonIndex.value,
          selectedPolylineIndex: renderState.selectedPolylineIndex.value,
          allPolygons: renderState.polygons,
          allPolylines: renderState.polylines,
          viewMode: normalizeViewMode(renderState.viewMode),
          hiddenPolygonIds: hiddenPolygonIdSet,
          hiddenPolylineIds: hiddenPolylineIdSet,
          temporaryHoverPolygonId: editorUiStore.temporaryHoverPolygonId
        })
      ) {
        return
      }

      const strokeColor = getStrokeColorForLabel(polygon.label, document, getActiveLabelSet(), polygon.regionKind, polygon.regionSubtype)
      const color: RGBA = [strokeColor[0], strokeColor[1], strokeColor[2], RENDER_ALPHA.FILL_LABEL_BACKGROUND]

      const triangleIndices = getCachedTriangulation(polygon, triangulatePolygon)

      if (triangleIndices.length >= 3) {
        fillRenderer.drawFill(polygon.points, triangleIndices, color, scale, view)
      }
    })
  }

  /**
   * Draw background polygons with dashed outlines.
   * These are non-selectable context elements that help users understand the annotation structure.
   *
   * In TEXTLINE mode: Regions are drawn as background
   * In BASELINE mode: Regions and Textlines are drawn as background
   */
  function drawBackgroundPolygons(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View
  ): void {
    if (!dashedLineRenderer) return
    const localDashedLineRenderer = dashedLineRenderer

    const viewMode = normalizeViewMode(renderState.viewMode)
    if (!viewMode || viewMode === 'default') return

    const hasSelection = renderState.selectedPolygonIndex.value >= 0 || renderState.selectedPolylineIndex.value >= 0
    if (hasSelection) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    const document = getActiveDocument()
    const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
    const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)

    renderState.polygons.forEach((polygon) => {
      if (
        !visibilityService.shouldRenderAsBackground(polygon, {
          selectedPolygonIndex: renderState.selectedPolygonIndex.value,
          selectedPolylineIndex: renderState.selectedPolylineIndex.value,
          allPolygons: renderState.polygons,
          allPolylines: renderState.polylines,
          viewMode: viewMode,
          hiddenPolygonIds: hiddenPolygonIdSet,
          hiddenPolylineIds: hiddenPolylineIdSet
        })
      ) {
        return
      }

      const labelColor = getColorForLabel(polygon.label, document, getActiveLabelSet(), polygon.regionKind, polygon.regionSubtype, polygon.regionCustom)
      const color = [labelColor[0], labelColor[1], labelColor[2], BACKGROUND_ELEMENT.LINE_ALPHA]

      const isClosed = true

      localDashedLineRenderer.drawDashedLine(
        polygon.points,
        color,
        getLineWidth() * 0.7,
        isClosed,
        BACKGROUND_ELEMENT.DASH_LENGTH,
        BACKGROUND_ELEMENT.GAP_LENGTH,
        scale,
        view
      )
    })
  }

  /**
   * Draw auto-parent indicator during Textline/Baseline creation in view modes.
   * Shows visual feedback about which parent the new element will be assigned to,
   * or what helper shape(s) will be created.
   */
  function drawAutoParentIndicator(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!dashedLineRenderer || !fillRenderer) return

    const preview = renderState.autoParentPreview
    if (!preview) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    if (preview.isExisting && preview.parentPolygon) {
      const parentPoints = preview.parentPolygon.points

      const triangleIndices = triangulatePolygon(parentPoints)
      if (triangleIndices.length >= 3) {
        fillRenderer.drawFill(
          parentPoints,
          triangleIndices,
          AUTO_PARENT_INDICATOR.EXISTING_PARENT_FILL,
          scale,
          view
        )
      }

      dashedLineRenderer.drawDashedLine(
        parentPoints,
        AUTO_PARENT_INDICATOR.EXISTING_PARENT_OUTLINE,
        AUTO_PARENT_INDICATOR.LINE_THICKNESS,
        true, // closed polygon
        AUTO_PARENT_INDICATOR.DASH_LENGTH,
        AUTO_PARENT_INDICATOR.GAP_LENGTH,
        scale,
        view
      )
    }

    if (preview.helperShapePoints && preview.helperShapePoints.length >= 3) {
      const helperPoints = preview.helperShapePoints

      const triangleIndices = triangulatePolygon(helperPoints)
      if (triangleIndices.length >= 3) {
        fillRenderer.drawFill(
          helperPoints,
          triangleIndices,
          AUTO_PARENT_INDICATOR.NEW_PARENT_FILL,
          scale,
          view
        )
      }

      dashedLineRenderer.drawDashedLine(
        helperPoints,
        AUTO_PARENT_INDICATOR.NEW_PARENT_OUTLINE,
        AUTO_PARENT_INDICATOR.LINE_THICKNESS,
        true, // closed polygon
        AUTO_PARENT_INDICATOR.DASH_LENGTH,
        AUTO_PARENT_INDICATOR.GAP_LENGTH,
        scale,
        view
      )
    }

    if (preview.helperTextlinePoints && preview.helperTextlinePoints.length >= 3) {
      const textlinePoints = preview.helperTextlinePoints

      dashedLineRenderer.drawDashedLine(
        textlinePoints,
        AUTO_PARENT_INDICATOR.NEW_TEXTLINE_OUTLINE,
        AUTO_PARENT_INDICATOR.LINE_THICKNESS,
        true, // closed polygon
        AUTO_PARENT_INDICATOR.DASH_LENGTH,
        AUTO_PARENT_INDICATOR.GAP_LENGTH,
        scale,
        view
      )
    }
  }

  /**
   * Draw buffer preview (expand/shrink preview)
   */
  function drawBufferPreview(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!renderState.bufferPreview || !fillRenderer || !thickLineRenderer) return

    const { points } = renderState.bufferPreview
    if (points.length < 3) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    const triangleIndices = triangulatePolygon(points)
    if (triangleIndices.length >= 3) {
      fillRenderer.drawFill(points, triangleIndices, [0.2, 0.6, 1.0, 0.3], scale, view)
    }

    thickLineRenderer.drawThickLine(
      points,
      [0.2, 0.6, 1.0, 1.0],
      2.0,
      true,
      scale,
      view
    )
  }

  function drawActionProcessingTargets(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    const targets = renderState.actionProcessingTargets
    if (!targets || (!targets.page && targets.polygonIds.length === 0) || !fillRenderer) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    const pulse = 0.5 + Math.sin(performance.now() / 420) * 0.5
    const timeSeconds = performance.now() / 1000
    const haloColor: RGBA = [0.16, 0.74, 1.0, 0.18 + pulse * 0.12]
    const glowColor: RGBA = [0.46, 0.22, 1.0, 0.13 + pulse * 0.1]
    const strokeColor: RGBA = [0.86, 0.96, 1.0, 0.58 + pulse * 0.34]
    const strokeWidth = 2.25 + pulse * 2.25

    if (targets.page) {
      const pagePoints: Point[] = [
        { x: -1, y: 1 },
        { x: 1, y: 1 },
        { x: 1, y: -1 },
        { x: -1, y: -1 }
      ]
      fillRenderer.drawFill(pagePoints, [0, 1, 2, 0, 2, 3], haloColor, scale, view)
      fillRenderer.drawFill(pagePoints, [0, 1, 2, 0, 2, 3], glowColor, scale, view)
      fillRenderer.drawProcessingFill(pagePoints, [0, 1, 2, 0, 2, 3], scale, view, timeSeconds, 1.22)
      drawThickLine(pagePoints, strokeColor, strokeWidth, true, aspectRatioScale, view)
    }

    const polygonIds = new Set(targets.polygonIds)
    for (const polygon of renderState.polygons) {
      if (!polygonIds.has(polygon.id) || polygon.points.length < 3) continue
      const triangleIndices = getCachedTriangulation(polygon, triangulatePolygon)
      fillRenderer.drawFill(polygon.points, triangleIndices, haloColor, scale, view)
      fillRenderer.drawFill(polygon.points, triangleIndices, glowColor, scale, view)
      fillRenderer.drawProcessingFill(polygon.points, triangleIndices, scale, view, timeSeconds, 1.25)
      drawThickLine(polygon.points, strokeColor, strokeWidth, polygon.type !== PolygonType.BASELINE, aspectRatioScale, view)
    }
  }

  function drawConfidenceHeatmapPolygons(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!fillRenderer) return
    if (!renderState.confidenceHeatmap?.enabled) return

    const viewMode = normalizeViewMode(renderState.viewMode)
    if (!viewMode || viewMode === 'baseline') return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
    const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)

    for (const polygon of renderState.polygons) {
      if (hiddenPolygonIdSet.has(polygon.id)) continue

      const isMovingInvalid = renderState.moveState?.isMoving
        && renderState.moveState.isInvalid
        && renderState.moveState.elementId === polygon.id
      if (isMovingInvalid) continue

      const shouldRender = viewMode === 'textline'
        ? polygon.type === PolygonType.TEXTLINE
        : visibilityService.shouldShowPolygon(polygon, {
            selectedPolygonIndex: renderState.selectedPolygonIndex.value,
            selectedPolylineIndex: renderState.selectedPolylineIndex.value,
            allPolygons: renderState.polygons,
            allPolylines: renderState.polylines,
            viewMode,
            hiddenPolygonIds: hiddenPolygonIdSet,
            hiddenPolylineIds: hiddenPolylineIdSet,
            temporaryHoverPolygonId: editorUiStore.temporaryHoverPolygonId
          })

      if (!shouldRender) continue

      const confidence = computeElementConfidence({
        variants: polygon.textContentVariants,
        elementConfidence: polygon.confidence,
        mode: renderState.confidenceHeatmap.mode,
        selectedIndices: renderState.confidenceHeatmap.selectedIndices
      })
      if (confidence === undefined) continue
      const scaledConfidence = scaleConfidenceForHeatmap(
        confidence,
        renderState.confidenceHeatmap.logScale ?? true,
        renderState.confidenceHeatmap.logScaleStrength ?? 8
      )

      const triangleIndices = getCachedTriangulation(polygon, triangulatePolygon)
      if (triangleIndices.length < 3) continue

      fillRenderer.drawFill(
        polygon.points,
        triangleIndices,
        confidenceToHeatRgba(
          scaledConfidence,
          renderState.confidenceHeatmap.fillOpacity ?? 0.35
        ),
        scale,
        view
      )
    }
  }

  /**
   * Draw non-selected polygon outlines (without nodes)
   */
  function drawNonSelectedPolygonOutlines(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View
  ): void {
    const document = getActiveDocument()

    const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
    const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)

    renderState.polygons.forEach((polygon, index) => {
      if (index === renderState.selectedPolygonIndex.value) {
        return
      }

      if (renderState.selectedPolygonIds.value.includes(polygon.id)) {
        return
      }

      if (
        !visibilityService.shouldShowNonSelectedPolygon(polygon, {
          selectedPolygonIndex: renderState.selectedPolygonIndex.value,
          allPolygons: renderState.polygons,
          viewMode: normalizeViewMode(renderState.viewMode),
          hiddenPolygonIds: hiddenPolygonIdSet,
          hiddenPolylineIds: hiddenPolylineIdSet,
          temporaryHoverPolygonId: editorUiStore.temporaryHoverPolygonId
        })
      ) {
        return
      }

      const labelColor = getColorForLabel(polygon.label, document, getActiveLabelSet(), polygon.regionKind, polygon.regionSubtype, polygon.regionCustom)
      const color = [labelColor[0], labelColor[1], labelColor[2], 1.0] // Full opacity for outline
      const isClosed = polygon.type !== PolygonType.BASELINE
      drawThickLine(polygon.points, color, getLineWidth(), isClosed, aspectRatioScale, view)
    })
  }

  /**
   * Draw multi-selected polygon fills with label colors.
   */
  function drawMultiSelectedPolygonFills(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!fillRenderer || !renderState.selectedPolygonIds.value.length) return
    const localFillRenderer = fillRenderer

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    const document = getActiveDocument()
    const selectedIds = new Set(renderState.selectedPolygonIds.value)
    const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
    const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)

    const focusedPolygon = renderState.selectedPolygonIndex.value >= 0
      ? renderState.polygons[renderState.selectedPolygonIndex.value]
      : null

    renderState.polygons.forEach((polygon) => {
      if (!selectedIds.has(polygon.id)) return
      if (focusedPolygon && polygon.id === focusedPolygon.id) return

      if (
        !visibilityService.shouldShowPolygon(polygon, {
          selectedPolygonIndex: renderState.selectedPolygonIndex.value,
          selectedPolylineIndex: renderState.selectedPolylineIndex.value,
          allPolygons: renderState.polygons,
          allPolylines: renderState.polylines,
          viewMode: normalizeViewMode(renderState.viewMode),
          hiddenPolygonIds: hiddenPolygonIdSet,
          hiddenPolylineIds: hiddenPolylineIdSet,
          temporaryHoverPolygonId: editorUiStore.temporaryHoverPolygonId
        })
      ) {
        return
      }

      const labelColor = getColorForLabel(polygon.label, document, getActiveLabelSet(), polygon.regionKind, polygon.regionSubtype, polygon.regionCustom)
      const color: RGBA = [labelColor[0], labelColor[1], labelColor[2], RENDER_ALPHA.FILL_MULTI_SELECTED]
      const triangleIndices = getCachedTriangulation(polygon, triangulatePolygon)

      if (triangleIndices.length >= 3) {
        localFillRenderer.drawFill(polygon.points, triangleIndices, color, scale, view)
      }
    })
  }

  /**
   * Draw multi-selected polygon outlines (thicker), without nodes.
   */
  function drawMultiSelectedPolygonOutlines(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View
  ): void {
    if (!renderState.selectedPolygonIds.value.length) return
    const document = getActiveDocument()
    const selectedIds = new Set(renderState.selectedPolygonIds.value)

    const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
    const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)

    renderState.polygons.forEach((polygon) => {
      if (!selectedIds.has(polygon.id)) return

      if (
        !visibilityService.shouldShowPolygon(polygon, {
          selectedPolygonIndex: renderState.selectedPolygonIndex.value,
          selectedPolylineIndex: renderState.selectedPolylineIndex.value,
          allPolygons: renderState.polygons,
          allPolylines: renderState.polylines,
          viewMode: normalizeViewMode(renderState.viewMode),
          hiddenPolygonIds: hiddenPolygonIdSet,
          hiddenPolylineIds: hiddenPolylineIdSet,
          temporaryHoverPolygonId: editorUiStore.temporaryHoverPolygonId
        })
      ) {
        return
      }

      const labelColor = getStrokeColorForLabel(polygon.label, document, getActiveLabelSet(), polygon.regionKind, polygon.regionSubtype)
      const color: RGBA = [labelColor[0], labelColor[1], labelColor[2], 1.0]
      const isClosed = polygon.type !== PolygonType.BASELINE
      drawThickLine(polygon.points, color, getLineWidth() * 1.4, isClosed, aspectRatioScale, view)
    })
  }

  /**
   * Draw selected polygon outline and current drawing elements (without nodes)
   */
  function drawSelectedPolygonOutline(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    let polygonIndexToDraw = renderState.selectedPolygonIndex.value

    if (polygonIndexToDraw < 0 && renderState.selectedPolylineIndex.value >= 0) {
      const selectedPolyline = renderState.polylines[renderState.selectedPolylineIndex.value]
      if (selectedPolyline && selectedPolyline.parentId) {
        polygonIndexToDraw = renderState.polygons.findIndex(p => p.id === selectedPolyline.parentId)
      }
    }

    if (polygonIndexToDraw >= 0 && polygonIndexToDraw < renderState.polygons.length) {
      const selectedPolygon = renderState.polygons[polygonIndexToDraw]
      if (!selectedPolygon) return

      const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
      const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)

      if (
        visibilityService.shouldShowPolygon(selectedPolygon, {
          selectedPolygonIndex: renderState.selectedPolygonIndex.value,
          selectedPolylineIndex: renderState.selectedPolylineIndex.value,
          allPolygons: renderState.polygons,
          allPolylines: renderState.polylines,
          viewMode: normalizeViewMode(renderState.viewMode),
          hiddenPolygonIds: hiddenPolygonIdSet,
          hiddenPolylineIds: hiddenPolylineIdSet,
          temporaryHoverPolygonId: editorUiStore.temporaryHoverPolygonId
        })
      ) {
        const isMovingInvalid = renderState.moveState?.isMoving
          && renderState.moveState.isInvalid
          && renderState.moveState.elementId === selectedPolygon.id
        const document = getActiveDocument()
        const labelColor = getColorForLabel(selectedPolygon.label, document, getActiveLabelSet(), selectedPolygon.regionKind, selectedPolygon.regionSubtype, selectedPolygon.regionCustom)
        const color: RGBA = isMovingInvalid
          ? [1.0, 0.2, 0.2, 1.0]
          : [labelColor[0], labelColor[1], labelColor[2], 1.0]
        const isClosed = selectedPolygon.type !== PolygonType.BASELINE
        drawThickLine(selectedPolygon.points, color, getLineWidth(), isClosed, aspectRatioScale, view)

        if (isMovingInvalid && fillRenderer && isClosed) {
          const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
          const triangleIndices = getCachedTriangulation(selectedPolygon, triangulatePolygon)
          if (triangleIndices.length >= 3) {
            fillRenderer.drawInvalidFill(selectedPolygon.points, triangleIndices, scale, view)
          }
        }
      }
    }
  }

  /**
   * Draw polygon nodes (for selected polygons and current polygon)
   */
  function drawPolygonNodes(renderState: RenderState, aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale, view: View): void {
    if (!polygonRenderer) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    const document = getActiveDocument()

    const isPolylineSelected = renderState.selectedPolylineIndex.value >= 0
    if (renderState.selectedPolygonIndex.value >= 0 && !isPolylineSelected) {
      const selectedPolygon = renderState.polygons[renderState.selectedPolygonIndex.value]
      const nodeColor = selectedPolygon?.label && document
        ? getStrokeColorForLabel(selectedPolygon.label, document, getActiveLabelSet(), selectedPolygon.regionKind, selectedPolygon.regionSubtype)
        : undefined

      polygonRenderer.drawPolygonNodes(
        renderState.polygons,
        renderState.selectedPolygonIndex.value,
        scale,
        view,
        renderState.hoveredNodeIndex.value,
        renderState.draggedNodeInfo,
        renderState.isInvalidPosition.value,
        nodeColor
      )
    }

    if (renderState.currentPolygonPoints.length > 0) {
      polygonRenderer.drawCurrentPolygon(
        renderState.currentPolygonPoints,
        renderState.isInvalidPosition.value,
        scale,
        view
      )
    }

    if (renderState.rectanglePreviewPoints?.length && renderState.rectanglePreviewPoints.length > 0) {
      drawRectanglePreviewNodes(renderState, scale, view)
    }
  }

  /**
   * Helper for rectangle preview (can be optimized similarly)
   */
  function drawRectanglePreviewNodes(renderState: RenderState, scale: AspectRatioScale, view: View): void {
    if (!gl || !polygonProgram || !polygonVao || !polygonBuffer || !resourcePool || !renderState.rectanglePreviewPoints) return

    const color = RENDER_COLORS.RECTANGLE_PREVIEW_YELLOW
    const pointCount = renderState.rectanglePreviewPoints.length

    const nodeData = resourcePool.getFloat32Array('rect-preview', pointCount * 2)

    for (let i = 0; i < pointCount; i++) {
      const point = renderState.rectanglePreviewPoints[i]
      if (!point) continue
      nodeData[i * 2] = point.x
      nodeData[i * 2 + 1] = point.y
    }

    gl.useProgram(polygonProgram)
    gl.bindVertexArray(polygonVao)
    gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_scale'), scale.scaleX, scale.scaleY)
    gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_offset'), view.offsetX, view.offsetY)
    gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_zoom'), view.zoom)
    setProgramRotation(polygonProgram, scale)
    gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.POLYGON_POINT_SIZE)
    gl.uniform4f(gl.getUniformLocation(polygonProgram, 'u_color'), color[0], color[1], color[2], color[3])

    gl.bindBuffer(gl.ARRAY_BUFFER, polygonBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, nodeData.subarray(0, pointCount * 2), gl.DYNAMIC_DRAW)
    gl.drawArrays(gl.POINTS, 0, pointCount)
  }

  /**
   * Draw polylines
   */
  function drawPolylines(renderState: RenderState, aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale, view: View): void {
    if (!gl || !polygonProgram || !polygonVao || !polygonBuffer) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    if (renderState.currentPolylinePoints && renderState.currentPolylinePoints.length > 0) {
      let color: RGBA
      if (renderState.isInvalidPosition.value) {
        color = RENDER_COLORS.INVALID_RED
      } else {
        color = RENDER_COLORS.ACTIVE_YELLOW_POLYLINE
      }

      drawThickLine(renderState.currentPolylinePoints, color, getLineWidth(), false, aspectRatioScale, view)

      if (
        renderState.polylinePreviewPoint
        && renderState.polylinePreviewPoint.x !== null
        && renderState.polylinePreviewPoint.y !== null
        && renderState.currentPolylinePoints.length > 0
      ) {
        const lastPoint = renderState.currentPolylinePoints[renderState.currentPolylinePoints.length - 1]
        if (lastPoint) {
          const previewLine = [lastPoint, { x: renderState.polylinePreviewPoint.x, y: renderState.polylinePreviewPoint.y }]
          const previewColor = renderState.isInvalidPosition.value
            ? RENDER_COLORS.POLYLINE_PREVIEW_INVALID_RED
            : RENDER_COLORS.POLYLINE_PREVIEW_YELLOW
          drawThickLine(previewLine, previewColor, RENDER_THICKNESS.POLYLINE_PREVIEW, false, aspectRatioScale, view)
        }
      }

      gl.useProgram(polygonProgram)
      gl.bindVertexArray(polygonVao)
      gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_scale'), scale.scaleX, scale.scaleY)
      gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_offset'), view.offsetX, view.offsetY)
      gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_zoom'), view.zoom)
      setProgramRotation(polygonProgram, scale)
      gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.POLYGON_POINT_SIZE)
      gl.uniform4f(gl.getUniformLocation(polygonProgram, 'u_color'), color[0], color[1], color[2], color[3])

      const currentPolylineData = new Float32Array(renderState.currentPolylinePoints.flatMap(p => [p.x, p.y]))
      gl.bindBuffer(gl.ARRAY_BUFFER, polygonBuffer)
      gl.bufferData(gl.ARRAY_BUFFER, currentPolylineData, gl.DYNAMIC_DRAW)
      gl.drawArrays(gl.POINTS, 0, renderState.currentPolylinePoints.length)
    }

    const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
    const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)
    const normalizedViewMode = normalizeViewMode(renderState.viewMode)
    const shouldHeatmapBaselines = renderState.confidenceHeatmap?.enabled && normalizedViewMode === 'baseline'

    renderState.polylines.forEach((polyline, index) => {
      const shouldShowPolyline = shouldHeatmapBaselines
        ? !hiddenPolylineIdSet.has(polyline.id)
        : visibilityService.shouldShowPolyline(
            polyline,
            {
              selectedPolygonIndex: renderState.selectedPolygonIndex.value,
              selectedPolylineIndex: renderState.selectedPolylineIndex.value,
              allPolygons: renderState.polygons,
              allPolylines: renderState.polylines,
              viewMode: renderState.viewMode,
              hiddenPolygonIds: hiddenPolygonIdSet,
              hiddenPolylineIds: hiddenPolylineIdSet,
              temporaryHoverPolylineId: editorUiStore.temporaryHoverPolylineId
            }
          )

      if (!shouldShowPolyline) {
        return
      }

      let color: RGBA
      const isMultiSelected = renderState.selectedPolylineIds.value.includes(polyline.id)
      const isMovingInvalid = renderState.moveState?.isMoving
        && renderState.moveState.isInvalid
        && renderState.moveState.elementId === polyline.id
      if (isMovingInvalid) {
        color = [1.0, 0.2, 0.2, 1.0]
      } else if (index === renderState.selectedPolylineIndex.value || isMultiSelected) {
        color = RENDER_COLORS.SELECTED_BLUE
      } else if (shouldHeatmapBaselines) {
        const confidence = computeElementConfidence({
          variants: [],
          elementConfidence: polyline.confidence,
          mode: renderState.confidenceHeatmap?.mode ?? 'average',
          selectedIndices: renderState.confidenceHeatmap?.selectedIndices ?? []
        })
        const scaledConfidence = confidence === undefined
          ? undefined
          : scaleConfidenceForHeatmap(
              confidence,
              renderState.confidenceHeatmap?.logScale ?? true,
              renderState.confidenceHeatmap?.logScaleStrength ?? 8
            )
        color = scaledConfidence === undefined
          ? RENDER_COLORS.ACTIVE_YELLOW_POLYLINE
          : confidenceToHeatRgba(scaledConfidence, 0.9)
      } else {
        color = RENDER_COLORS.ACTIVE_YELLOW_POLYLINE
      }

      drawThickLine(polyline.points, color, getLineWidth(), false, aspectRatioScale, view)

      if (index === renderState.selectedPolylineIndex.value && gl && polygonProgram && polygonVao && polygonBuffer) {
        gl.useProgram(polygonProgram)
        gl.bindVertexArray(polygonVao)
        gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_scale'), scale.scaleX, scale.scaleY)
        gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_offset'), view.offsetX, view.offsetY)
        gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_zoom'), view.zoom)
        setProgramRotation(polygonProgram, scale)
        gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.POLYGON_POINT_SIZE)

        const polylineData = new Float32Array(polyline.points.flatMap((p: Point) => [p.x, p.y]))
        gl.bindBuffer(gl.ARRAY_BUFFER, polygonBuffer)
        gl.bufferData(gl.ARRAY_BUFFER, polylineData, gl.DYNAMIC_DRAW)

        if (
          renderState.polylineDraggedNodeInfo
          && renderState.polylineDraggedNodeInfo.isDragging
          && renderState.polylineDraggedNodeInfo.polylineIndex === index
          && renderState.isInvalidPosition.value
        ) {
          gl.uniform4f(
            gl.getUniformLocation(polygonProgram, 'u_color'),
            RENDER_COLORS.INVALID_RED[0],
            RENDER_COLORS.INVALID_RED[1],
            RENDER_COLORS.INVALID_RED[2],
            RENDER_COLORS.INVALID_RED[3]
          )
        } else {
          gl.uniform4f(
            gl.getUniformLocation(polygonProgram, 'u_color'),
            RENDER_COLORS.SELECTED_BLUE[0],
            RENDER_COLORS.SELECTED_BLUE[1],
            RENDER_COLORS.SELECTED_BLUE[2],
            RENDER_COLORS.SELECTED_BLUE[3]
          )
        }
        gl.drawArrays(gl.POINTS, 0, polyline.points.length)

        if (
          renderState.hoveredNodeIndex
          && renderState.hoveredNodeIndex.value >= 0
          && renderState.hoveredNodeIndex.value < polyline.points.length
        ) {
          gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.DRAGGED_POINT_SIZE)

          if (
            renderState.polylineDraggedNodeInfo
            && renderState.polylineDraggedNodeInfo.isDragging
            && renderState.polylineDraggedNodeInfo.polylineIndex === index
            && renderState.polylineDraggedNodeInfo.nodeIndex === renderState.hoveredNodeIndex.value
            && renderState.isInvalidPosition.value
          ) {
            gl.uniform4f(
              gl.getUniformLocation(polygonProgram, 'u_color'),
              RENDER_COLORS.INVALID_RED[0],
              RENDER_COLORS.INVALID_RED[1],
              RENDER_COLORS.INVALID_RED[2],
              RENDER_COLORS.INVALID_RED[3]
            )
          } else {
            gl.uniform4f(
              gl.getUniformLocation(polygonProgram, 'u_color'),
              RENDER_COLORS.PREVIEW_PINK[0],
              RENDER_COLORS.PREVIEW_PINK[1],
              RENDER_COLORS.PREVIEW_PINK[2],
              RENDER_COLORS.PREVIEW_PINK[3]
            )
          }

          const hoveredPoint = polyline.points[renderState.hoveredNodeIndex.value]
          if (hoveredPoint) {
            const hoveredNodeData = new Float32Array([hoveredPoint.x, hoveredPoint.y])
            gl.bufferData(gl.ARRAY_BUFFER, hoveredNodeData, gl.DYNAMIC_DRAW)
            gl.drawArrays(gl.POINTS, 0, 1)
          }
        }
      }
    })
  }

  /**
   * Draw hover polylines
   */
  function drawHoverPolylines(
    polylines: RenderablePolyline[],
    hoveredPolylineIndex: Ref<number>,
    selectedPolylineIndex: Ref<number>,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    hiddenPolylineIds?: Set<string>
  ): void {
    if (!gl) return

    if (hoveredPolylineIndex.value >= 0 && hoveredPolylineIndex.value !== selectedPolylineIndex.value) {
      const hoveredPoly = polylines[hoveredPolylineIndex.value]
      if (!hoveredPoly) return

      if (hiddenPolylineIds?.has(hoveredPoly.id)) return

      gl.enable(gl.BLEND)
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

      drawThickLine(
        hoveredPoly.points,
        RENDER_COLORS.POLYLINE_HOVER_YELLOW,
        RENDER_THICKNESS.POLYLINE_HOVER,
        false,
        aspectRatioScale,
        view
      )

      gl.disable(gl.BLEND)
    }
  }

  /**
   * Draw selection overlay using our SelectionOverlayRenderer
   */
  function drawMultiLevelSelectionOverlay(
    polygons: RenderablePolygon[],
    selectedPolygonIndex: Ref<number>,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!selectionOverlayRenderer) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    const cachedTriangulate = (points: Point[]): number[] => {
      const polygon = polygons.find(p => p.points === points)
      if (polygon && polygon.id && geometryCache) {
        return geometryCache.getTriangulation(polygon.id, points)
      }
      return triangulatePolygon(points)
    }

    selectionOverlayRenderer.renderSelectionOverlay(polygons, selectedPolygonIndex, scale, view, cachedTriangulate)
  }

  /**
   * Draw preview nodes
   */
  function drawPreviewNodes(previewNodePosition: Point | null, aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale, view: View): void {
    if (!gl || !polygonProgram || !polygonVao || !polygonBuffer) return
    if (!previewNodePosition) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    gl.useProgram(polygonProgram)
    gl.bindVertexArray(polygonVao)
    gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_scale'), scale.scaleX, scale.scaleY)
    gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_offset'), view.offsetX, view.offsetY)
    gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_zoom'), view.zoom)
    setProgramRotation(polygonProgram, scale)
    gl.uniform4f(
      gl.getUniformLocation(polygonProgram, 'u_color'),
      RENDER_COLORS.PREVIEW_PINK[0],
      RENDER_COLORS.PREVIEW_PINK[1],
      RENDER_COLORS.PREVIEW_PINK[2],
      RENDER_COLORS.PREVIEW_PINK[3]
    )
    gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.SELECTED_POINT_SIZE)

    const previewData = new Float32Array([previewNodePosition.x, previewNodePosition.y])
    gl.bindBuffer(gl.ARRAY_BUFFER, polygonBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, previewData, gl.DYNAMIC_DRAW)
    gl.drawArrays(gl.POINTS, 0, 1)

    gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.PREVIEW_POINT_SIZE)
  }

  /**
   * Draw polyline preview nodes
   */
  function drawPolylinePreviewNodes(
    polylinePreviewNodePosition: Point | null | undefined,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View
  ): void {
    if (!gl || !polygonProgram || !polygonVao || !polygonBuffer || !polylinePreviewNodePosition) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    gl.useProgram(polygonProgram)
    gl.bindVertexArray(polygonVao)
    gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_scale'), scale.scaleX, scale.scaleY)
    gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_offset'), view.offsetX, view.offsetY)
    gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_zoom'), view.zoom)
    setProgramRotation(polygonProgram, scale)
    gl.uniform4f(
      gl.getUniformLocation(polygonProgram, 'u_color'),
      RENDER_COLORS.ACTIVE_YELLOW_POLYLINE[0],
      RENDER_COLORS.ACTIVE_YELLOW_POLYLINE[1],
      RENDER_COLORS.ACTIVE_YELLOW_POLYLINE[2],
      RENDER_COLORS.ACTIVE_YELLOW_POLYLINE[3]
    )
    gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.SELECTED_POINT_SIZE)

    const previewData = new Float32Array([polylinePreviewNodePosition.x, polylinePreviewNodePosition.y])
    gl.bindBuffer(gl.ARRAY_BUFFER, polygonBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, previewData, gl.DYNAMIC_DRAW)
    gl.drawArrays(gl.POINTS, 0, 1)

    gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.PREVIEW_POINT_SIZE)
  }

  /**
   * Draw red fill for current polygon when in invalid position
   */
  function drawInvalidPolygonFill(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!fillRenderer || !renderState.currentPolygonPoints || renderState.currentPolygonPoints.length < 3) {
      return
    }

    if (!renderState.isInvalidPosition.value) {
      return
    }

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    const triangleIndices = triangulatePolygon(renderState.currentPolygonPoints)

    fillRenderer.drawInvalidFill(renderState.currentPolygonPoints, triangleIndices, scale, view)
  }

  /**
   * Draw red fill for current rectangle when in invalid position
   */
  function drawInvalidRectangleFill(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View
  ): void {
    if (!fillRenderer || !renderState.rectanglePreviewPoints || renderState.rectanglePreviewPoints.length < 4) {
      return
    }

    if (!renderState.isInvalidPosition.value) {
      return
    }

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale

    const triangleIndices = WEBGL_GEOMETRY.RECTANGLE_TRIANGLE_INDICES

    fillRenderer.drawInvalidFill(renderState.rectanglePreviewPoints, triangleIndices, scale, view)
  }

  /**
   * Draw current polygon being created and its preview
   */
  function drawCurrentPolygonAndPreview(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View
  ): void {
    if (renderState.currentPolygonPoints.length > 0) {
      let color: RGBA
      if (renderState.isInvalidPosition.value) {
        color = RENDER_COLORS.INVALID_RED
      } else {
        color = RENDER_COLORS.ACTIVE_YELLOW_POLYGON
      }

      drawThickLine(renderState.currentPolygonPoints, color, RENDER_THICKNESS.PREVIEW_OUTLINE, false, aspectRatioScale, view)

      if (renderState.previewPoint.x !== null && renderState.previewPoint.y !== null) {
        const lastPoint = renderState.currentPolygonPoints[renderState.currentPolygonPoints.length - 1]
        if (lastPoint) {
          const previewColor = renderState.isInvalidPosition.value
            ? RENDER_COLORS.POLYGON_PREVIEW_INVALID_RED
            : RENDER_COLORS.POLYGON_PREVIEW_YELLOW
          drawThickLine(
            [lastPoint, { x: renderState.previewPoint.x, y: renderState.previewPoint.y }],
            previewColor,
            RENDER_THICKNESS.PREVIEW_OUTLINE,
            false,
            aspectRatioScale,
            view
          )
        }
      }
    }

    if (renderState.rectanglePreviewPoints && renderState.rectanglePreviewPoints.length > 0) {
      let color: RGBA
      if (renderState.isInvalidPosition.value) {
        color = RENDER_COLORS.POLYGON_PREVIEW_INVALID_RED
      } else {
        color = RENDER_COLORS.RECTANGLE_PREVIEW_YELLOW
      }
      drawThickLine(renderState.rectanglePreviewPoints, color, RENDER_THICKNESS.PREVIEW_OUTLINE, true, aspectRatioScale, view)
    }
  }

  /**
   * Draw cut shape preview (line, polygon, or rectangle)
   */
  function drawCutPreview(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!renderState.isCutDrawingActive) return

    const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
    const cutMode = renderState.cutMode

    let points: Point[] | undefined
    if (cutMode === 'rectangle') {
      points = renderState.cutRectanglePoints
    } else if (cutMode === 'polygon') {
      points = renderState.cutPolygonPoints
    } else {
      points = renderState.cutLinePoints
    }

    if (!points || points.length === 0) return

    const isInvalid = renderState.isInvalidPosition.value
    const outlineColor = isInvalid ? RENDER_COLORS.CUT_PREVIEW_INVALID : RENDER_COLORS.CUT_PREVIEW_OUTLINE

    if (cutMode === 'rectangle' && fillRenderer && points.length >= 3) {
      const triangleIndices = triangulatePolygon(points)
      if (triangleIndices.length > 0) {
        const fillColor = isInvalid ? RENDER_COLORS.INVALID_FILL_RED : RENDER_COLORS.CUT_PREVIEW_FILL
        fillRenderer.drawInvalidFill(points, triangleIndices, scale, view, fillColor)
      }
    }

    const isClosed = cutMode === 'rectangle'
    drawThickLine(points, outlineColor, RENDER_THICKNESS.PREVIEW_OUTLINE, isClosed, aspectRatioScale, view)

    if (cutMode !== 'rectangle'
      && renderState.cutPreviewPoint
      && renderState.cutPreviewPoint.x !== null
      && renderState.cutPreviewPoint.y !== null) {
      const lastPoint = points[points.length - 1]
      if (lastPoint) {
        const previewColor = isInvalid ? RENDER_COLORS.CUT_PREVIEW_INVALID : RENDER_COLORS.CUT_PREVIEW_OUTLINE
        drawThickLine(
          [lastPoint, { x: renderState.cutPreviewPoint.x, y: renderState.cutPreviewPoint.y }],
          previewColor,
          RENDER_THICKNESS.PREVIEW_OUTLINE,
          false,
          aspectRatioScale,
          view
        )
      }
    }

    if (cutMode !== 'rectangle' && gl && polygonProgram && polygonVao && polygonBuffer) {
      gl.useProgram(polygonProgram)
      gl.bindVertexArray(polygonVao)
      gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_scale'), scale.scaleX, scale.scaleY)
      gl.uniform2f(gl.getUniformLocation(polygonProgram, 'u_offset'), view.offsetX, view.offsetY)
      gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_zoom'), view.zoom)
      setProgramRotation(polygonProgram, scale)
      gl.uniform1f(gl.getUniformLocation(polygonProgram, 'u_pointSize'), RENDER_SIZES.POLYGON_POINT_SIZE)
      gl.uniform4f(gl.getUniformLocation(polygonProgram, 'u_color'), outlineColor[0], outlineColor[1], outlineColor[2], outlineColor[3])

      const pointsData = new Float32Array(points.flatMap(p => [p.x, p.y]))
      gl.bindBuffer(gl.ARRAY_BUFFER, polygonBuffer)
      gl.bufferData(gl.ARRAY_BUFFER, pointsData, gl.DYNAMIC_DRAW)
      gl.drawArrays(gl.POINTS, 0, points.length)
    }
  }

  /**
   * Main draw function - orchestrates all rendering (exact same order as original)
   */
  function draw(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!gl) return

    const dpr = window.devicePixelRatio || WEBGL_CORE.DEFAULT_DEVICE_PIXEL_RATIO
    const canvas = getGlCanvasElement()
    if (!canvas) return
    const { clientWidth, clientHeight } = canvas
    const displayWidth = Math.round(clientWidth * dpr)
    const displayHeight = Math.round(clientHeight * dpr)
    if (gl.canvas.width !== displayWidth || gl.canvas.height !== displayHeight) {
      gl.canvas.width = displayWidth
      gl.canvas.height = displayHeight
    }
    gl.viewport(WEBGL_CORE.VIEWPORT_ORIGIN_X, WEBGL_CORE.VIEWPORT_ORIGIN_Y, gl.canvas.width, gl.canvas.height)
    gl.clearColor(...WEBGL_CORE.DEFAULT_CLEAR_COLOR)
    gl.clear(gl.COLOR_BUFFER_BIT)

    drawImage(aspectRatioScale, view)

    drawAutoParentIndicator(renderState, aspectRatioScale, view, triangulatePolygon)

    drawInvalidPolygonFill(renderState, aspectRatioScale, view, triangulatePolygon)
    drawInvalidRectangleFill(renderState, aspectRatioScale, view)
    drawCurrentPolygonAndPreview(renderState, aspectRatioScale, view)

    drawCutPreview(renderState, aspectRatioScale, view, triangulatePolygon)

    drawBufferPreview(renderState, aspectRatioScale, view, triangulatePolygon)

    if (renderState.polylines) {
      const hiddenPolylineIdSet = new Set(renderState.hiddenPolylineIds.value)
      drawPolylines(renderState, aspectRatioScale, view)
      drawHoverPolylines(
        renderState.polylines,
        renderState.hoveredPolylineIndex,
        renderState.selectedPolylineIndex,
        aspectRatioScale,
        view,
        hiddenPolylineIdSet
      )
    }

    const hiddenPolygonIdSet = new Set(renderState.hiddenPolygonIds.value)
    drawHoverPolygons(
      renderState.polygons,
      renderState.hoveredPolygonIndex,
      renderState.selectedPolygonIndex,
      aspectRatioScale,
      view,
      triangulatePolygon,
      renderState.viewMode,
      hiddenPolygonIdSet
    )

    if (renderState.showReadingOrderOverlay && renderState.readingOrderData && readingOrderRenderer) {
      const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
      const canvas = gl.canvas as HTMLCanvasElement
      readingOrderRenderer.draw(
        renderState.readingOrderData,
        scale,
        view,
        canvas.width,
        canvas.height
      )
    }

    if (renderState.showRelationsOverlay && renderState.relationData && readingOrderRenderer) {
      const scale = 'value' in aspectRatioScale ? aspectRatioScale.value : aspectRatioScale
      const canvas = gl.canvas as HTMLCanvasElement
      readingOrderRenderer.draw(
        {
          arrows: renderState.relationData.segments,
          groupBounds: [],
          orderNumbers: []
        },
        scale,
        view,
        canvas.width,
        canvas.height
      )
    }

    drawBackgroundPolygons(renderState, aspectRatioScale, view)
    drawConfidenceHeatmapPolygons(renderState, aspectRatioScale, view, triangulatePolygon)
    drawActionProcessingTargets(renderState, aspectRatioScale, view, triangulatePolygon)
    drawPolygonFills(renderState, aspectRatioScale, view, triangulatePolygon)

    drawNonSelectedPolygonOutlines(renderState, aspectRatioScale, view)
    drawMultiSelectedPolygonFills(renderState, aspectRatioScale, view, triangulatePolygon)
    drawMultiSelectedPolygonOutlines(renderState, aspectRatioScale, view)

    let overlayPolygonIndex = renderState.selectedPolygonIndex.value

    if (renderState.selectedPolylineIndex.value >= 0 && overlayPolygonIndex < 0) {
      const selectedPolyline = renderState.polylines[renderState.selectedPolylineIndex.value]
      if (selectedPolyline && selectedPolyline.parentId) {
        overlayPolygonIndex = renderState.polygons.findIndex(p => p.id === selectedPolyline.parentId)
      }
    }

    if (overlayPolygonIndex >= 0) {
      drawMultiLevelSelectionOverlay(
        renderState.polygons,
        { value: overlayPolygonIndex } as Ref<number>,
        aspectRatioScale,
        view,
        triangulatePolygon
      )
    }

    drawSelectedPolygonOutline(renderState, aspectRatioScale, view, triangulatePolygon)
    drawPolygonNodes(renderState, aspectRatioScale, view)
    drawPreviewNodes(renderState.previewNodePosition, aspectRatioScale, view)
    drawPolylinePreviewNodes(renderState.polylinePreviewNodePosition, aspectRatioScale, view)
  }

  /**
   * Image loading functions
   */
  async function loadImage(src: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      img.onload = () => resolve(img)
      img.onerror = err => reject(err)
      img.src = src
    })
  }

  function updateTexture(img: HTMLImageElement): void {
    if (!gl || !imageTexture) return

    imageSize.value.width = img.width
    imageSize.value.height = img.height

    gl.bindTexture(gl.TEXTURE_2D, imageTexture)
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true)
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img)
  }

  async function loadAndRender(src: string): Promise<void> {
    try {
      const img = await loadImage(src)
      updateTexture(img)
    } catch (err) {
      log.error('Image load error:', err)
    }
  }

  function renderFrame(
    renderState: RenderState,
    aspectRatioScale: Ref<AspectRatioScale> | AspectRatioScale,
    view: View,
    triangulatePolygon: (points: Point[]) => number[]
  ): void {
    if (!gl) return

    const dpr = window.devicePixelRatio || WEBGL_CORE.DEFAULT_DEVICE_PIXEL_RATIO
    const canvas = getGlCanvasElement()
    if (!canvas) return
    const { clientWidth, clientHeight } = canvas
    const displayWidth = Math.round(clientWidth * dpr)
    const displayHeight = Math.round(clientHeight * dpr)
    if (gl.canvas.width !== displayWidth || gl.canvas.height !== displayHeight) {
      gl.canvas.width = displayWidth
      gl.canvas.height = displayHeight
    }
    gl.viewport(WEBGL_CORE.VIEWPORT_ORIGIN_X, WEBGL_CORE.VIEWPORT_ORIGIN_Y, gl.canvas.width, gl.canvas.height)

    gl.clearColor(...WEBGL_CORE.DEFAULT_CLEAR_COLOR)
    gl.clear(gl.COLOR_BUFFER_BIT)

    draw(renderState, aspectRatioScale, view, triangulatePolygon)
  }

  function stopRenderLoop(): void {
    if (animationFrameId) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }
  }

  /**
   * Start reading order animation loop
   */
  function startReadingOrderAnimation(): void {
    readingOrderRenderer?.startAnimation()
  }

  /**
   * Stop reading order animation loop
   */
  function stopReadingOrderAnimation(): void {
    readingOrderRenderer?.stopAnimation()
  }

  /**
   * Cleanup function
   */
  function cleanup(): void {
    stopRenderLoop()

    fillRenderer?.cleanup()
    batchedLineRenderer?.cleanup()
    polygonRenderer?.cleanup()
    thickLineRenderer?.cleanup()
    dashedLineRenderer?.cleanup()
    selectionOverlayRenderer?.cleanup()
    readingOrderRenderer?.cleanup()

    shaderManager?.cleanup()
    textureManager?.cleanup()
    resourcePool?.cleanup()

    if (geometryCache) {
      geometryCache.clear()
      geometryCache = null
    }

    if (gl) {
      if (polygonBuffer) {
        gl.deleteBuffer(polygonBuffer)
        polygonBuffer = null
      }

      if (imageVao) {
        gl.deleteVertexArray(imageVao)
        imageVao = null
      }

      if (polygonVao) {
        gl.deleteVertexArray(polygonVao)
        polygonVao = null
      }

      if (imageTexture) {
        gl.deleteTexture(imageTexture)
        imageTexture = null
      }

      gl.getExtension('WEBGL_lose_context')?.loseContext()
      gl = null
    }

    fillRenderer = null
    batchedLineRenderer = null
    polygonRenderer = null
    thickLineRenderer = null
    dashedLineRenderer = null
    selectionOverlayRenderer = null
    readingOrderRenderer = null
    shaderManager = null
    textureManager = null
    resourcePool = null
    imageProgram = null
    polygonProgram = null
    fillProgram = null
  }

  onBeforeUnmount(() => {
    cleanup()
  })

  return {
    gl: () => gl,
    imageSize,

    initGL,

    renderFrame,
    stopRenderLoop,

    startReadingOrderAnimation,
    stopReadingOrderAnimation,

    loadAndRender,

    invalidateGeometry: (polygonId: string) => {
      if (geometryCache) {
        geometryCache.invalidate(polygonId)
      }
    },
    invalidateMultipleGeometry: (polygonIds: string[]) => {
      if (geometryCache) {
        geometryCache.invalidateMultiple(polygonIds)
      }
    },
    clearGeometryCache: () => {
      if (geometryCache) {
        geometryCache.clear()
      }
    },
    pruneGeometryCache: (activePolygonIds: Set<string>) => {
      if (geometryCache) {
        geometryCache.pruneStaleEntries(activePolygonIds)
      }
    },
    getGeometryCacheStats: () => {
      if (!geometryCache) return null
      const stats = geometryCache.getStats()
      return { size: stats.size, version: stats.version }
    },

    cleanup
  }
}
