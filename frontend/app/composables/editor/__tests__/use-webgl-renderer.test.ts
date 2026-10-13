import { afterEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useWebglRenderer } from '../use-webgl-renderer'
import { ThickLineRenderer } from '@/webgl/editor/thick-line-renderer'
import { SelectionOverlayRenderer } from '@/webgl/editor/selection-overlay'
import { RENDER_COLORS } from '@/utils/editor/editor-constants'
import type { WebGLRenderState } from '@/types/editor/rendering'

const editorSettings = vi.hoisted(() => ({
  defaultLineWidth: 'normal', defaultVertexSize: undefined as string | undefined, showPolygonLabelFill: false
}))
vi.mock('@/stores/editor/editor.ui.store', () => ({
  useEditorUiStore: () => ({ globalSettings: editorSettings })
}))
vi.mock('@/stores/editor/editor.store', () => ({ useEditorStore: () => ({}) }))
vi.mock('@/session/editor/editor-session', () => ({ getEditorSession: vi.fn() }))

function setupImageUpload(width: number, height: number, maxTextureSize = 4096) {
  vi.stubGlobal('onBeforeUnmount', vi.fn())
  const invalidUploads: string[] = []
  const uploads: Array<{ width: number, height: number }> = []
  const texImage2D = vi.fn((...args: unknown[]) => {
    if (args.length !== 6) return // Initial 1x1 placeholder.
    const source = args[5] as { width: number, height: number }
    if (source.width > maxTextureSize || source.height > maxTextureSize) {
      invalidUploads.push('INVALID_VALUE: texImage2D: width or height out of range')
    } else {
      uploads.push({ width: source.width, height: source.height })
    }
  })
  // Exercise the real renderer initialization; unrelated GL calls are no-ops.
  const members: Record<string, unknown> = { texImage2D }
  const gl = new Proxy(members, {
    get(target, key: string) {
      if (!(key in target)) {
        if (key === key.toUpperCase()) target[key] = Object.keys(target).length
        else if (key === 'getParameter') target[key] = () => maxTextureSize
        else if (key === 'getExtension') target[key] = () => null
        else if (key === 'getUniformLocation') target[key] = (_program: unknown, name: string) => ({ name })
        else if (key.startsWith('create')) target[key] = () => ({})
        else if (key === 'getShaderParameter' || key === 'getProgramParameter') target[key] = () => true
        else target[key] = vi.fn(() => 0)
      }
      return target[key]
    }
  }) as unknown as WebGL2RenderingContext
  const drawImage = vi.fn()
  const context = { drawImage, imageSmoothingEnabled: false, imageSmoothingQuality: 'low' }
  const textureCanvas = { width: 0, height: 0, getContext: () => context }
  const createElement = vi.fn(() => textureCanvas)
  vi.stubGlobal('document', { createElement })
  vi.stubGlobal('Image', class {
    width = width
    height = height
    naturalWidth = width
    naturalHeight = height
    onload = () => {}
    set src(_value: string) { queueMicrotask(() => this.onload()) }
  })
  class TestCanvas {
    width = 1000
    height = 800
    clientWidth = 1000
    clientHeight = 800
    getContext() { return gl }
  }
  vi.stubGlobal('HTMLCanvasElement', TestCanvas)
  vi.stubGlobal('window', { devicePixelRatio: 1 })
  const canvas = new TestCanvas() as unknown as HTMLCanvasElement
  members.canvas = canvas
  const renderer = useWebglRenderer(ref(canvas))
  renderer.initGL()
  return { renderer, gl, invalidUploads, uploads, texImage2D, drawImage, createElement, textureCanvas }
}

afterEach(() => {
  editorSettings.defaultVertexSize = undefined
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function baselineRenderState(): WebGLRenderState {
  return {
    polygons: [{ id: 'line', type: 'textline', points: [
      { x: -0.8, y: 0.3 }, { x: 0.8, y: 0.3 }, { x: 0.8, y: -0.3 }, { x: -0.8, y: -0.3 }
    ] }],
    polylines: [{ id: 'baseline:line', parentId: 'line', points: [
      { x: -0.7, y: 0 }, { x: 0, y: 0.02 }, { x: 0.7, y: 0 }
    ] }],
    selectedPolygonIndex: ref(0), selectedPolylineIndex: ref(0),
    selectedPolygonIds: ref([]), selectedPolylineIds: ref([]),
    hiddenPolygonIds: ref([]), hiddenPolylineIds: ref([]),
    hoveredPolygonIndex: ref(-1), hoveredPolylineIndex: ref(-1),
    hoveredNodeIndex: ref(-1), hoveredPolylineNodeIndex: ref(-1),
    draggedNodeInfo: { isDragging: false },
    previewNodePosition: null, polylinePreviewNodePosition: null,
    currentPolygonPoints: [], currentPolylinePoints: [],
    previewPoint: { x: null, y: null }, isInvalidPosition: ref(false)
  }
}

describe('vertex size rendering', () => {
  const scale = { scaleX: 1, scaleY: 1 }
  const view = { zoom: 1, offsetX: 0, offsetY: 0 }
  const pointSizes = (gl: WebGL2RenderingContext) => vi.mocked(gl.uniform1f).mock.calls
    .filter(([location]) => (location as unknown as { name: string }).name === 'u_pointSize')
    .map(([, size]) => size)

  it.each([
    [undefined, 1], ['normal', 1], ['tiny', 0.5], ['small', 0.75], ['large', 1.25], ['extraLarge', 1.5]
  ] as const)('uses %s vertex sizes for polygon editing and drawing', (preset, multiplier) => {
    editorSettings.defaultVertexSize = preset
    const { renderer, gl } = setupImageUpload(1000, 800)
    const state = baselineRenderState()
    state.selectedPolylineIndex.value = -1
    state.hoveredNodeIndex.value = 1
    state.currentPolygonPoints = [{ x: 0, y: 0 }]
    state.previewNodePosition = { x: 0.2, y: 0.1 }
    state.rectanglePreviewPoints = state.polygons[0]!.points
    renderer.renderFrame(state, scale, view, () => [0, 1, 2, 0, 2, 3])

    expect(pointSizes(gl)).toEqual(expect.arrayContaining([20, 30, 25, 10].map(size => size * multiplier)))
    renderer.cleanup()
  })

  it('scales every layer of baseline handles and keeps their size when zooming', () => {
    editorSettings.defaultVertexSize = 'small'
    const { renderer, gl } = setupImageUpload(1000, 800)
    const state = baselineRenderState()
    state.hoveredPolylineNodeIndex!.value = 1
    renderer.renderFrame(state, scale, view, () => [0, 1, 2, 0, 2, 3])
    const sizes = pointSizes(gl)
    expect(sizes).toEqual(expect.arrayContaining([22, 18, 8, 28, 24].map(size => size * 0.75)))

    vi.mocked(gl.uniform1f).mockClear()
    renderer.renderFrame(state, scale, { ...view, zoom: 4 }, () => [0, 1, 2, 0, 2, 3])
    expect(pointSizes(gl)).toEqual(sizes)
    renderer.cleanup()
  })
})

describe('baseline rendering', () => {
  const scale = { scaleX: 1, scaleY: 1 }
  const view = { zoom: 1, offsetX: 0, offsetY: 0 }

  it('does not re-upload unchanged page geometry when zooming in the baselines view', () => {
    const { renderer, gl } = setupImageUpload(1000, 800)
    const state = baselineRenderState()
    state.viewMode = 'baseline'
    state.selectedPolygonIndex.value = -1
    state.selectedPolylineIndex.value = -1
    state.polygons = Array.from({ length: 64 }, (_, index) => ({
      id: `line-${index}`, type: 'textline',
      points: Array.from({ length: 226 }, (_, node) => ({
        x: 0.8 * Math.cos(node * 2 * Math.PI / 226),
        y: index / 64 - 0.5 + 0.005 * Math.sin(node * 2 * Math.PI / 226)
      }))
    }))
    state.polylines = state.polygons.map(polygon => ({
      id: `baseline:${polygon.id}`, parentId: polygon.id,
      points: [{ x: -0.8, y: polygon.points[0]!.y }, { x: 0.8, y: polygon.points[0]!.y }]
    }))
    renderer.renderFrame(state, scale, view, () => [])
    const upload = vi.mocked(gl.bufferData)
    upload.mockClear()
    renderer.renderFrame(state, scale, { ...view, zoom: 1.1 }, () => [])
    expect(upload.mock.calls.length).toBe(0)
    renderer.cleanup()
  })

  it('draws the active baseline above its parent outline and selection overlay', () => {
    const { renderer } = setupImageUpload(1000, 800)
    const stroke = vi.spyOn(ThickLineRenderer.prototype, 'drawThickLine')
    const overlay = vi.spyOn(SelectionOverlayRenderer.prototype, 'renderSelectionOverlay')
    const state = baselineRenderState()
    renderer.renderFrame(state, scale, view, () => [0, 1, 2, 0, 2, 3])

    const baselineCalls = stroke.mock.calls.flatMap((call, index) => call[0] === state.polylines[0]!.points ? [index] : [])
    const parentCall = stroke.mock.calls.findIndex(call => call[0] === state.polygons[0]!.points)
    expect(baselineCalls.length).toBeGreaterThan(0)
    expect(parentCall).toBeGreaterThanOrEqual(0)
    expect(stroke.mock.invocationCallOrder[baselineCalls[0]!]).toBeGreaterThan(overlay.mock.invocationCallOrder[0]!)
    expect(baselineCalls[0]).toBeGreaterThan(parentCall)
    renderer.cleanup()
  })

  it('uses baseline node hover independently from polygon node hover', () => {
    const { renderer, gl } = setupImageUpload(1000, 800)
    const state = baselineRenderState()
    state.hoveredPolylineNodeIndex!.value = 1
    const color = vi.mocked(gl.uniform4f)
    renderer.renderFrame(state, scale, view, () => [0, 1, 2, 0, 2, 3])
    expect(color).toHaveBeenCalledWith(expect.anything(), ...RENDER_COLORS.PREVIEW_PINK)
    renderer.cleanup()
  })

  it('refreshes cached baseline strokes and dashed context after geometry edits', () => {
    const { renderer, gl } = setupImageUpload(1000, 800)
    const state = baselineRenderState()
    state.viewMode = 'baseline'
    state.selectedPolygonIndex.value = -1
    state.selectedPolylineIndex.value = -1
    renderer.renderFrame(state, scale, view, () => [])
    const upload = vi.mocked(gl.bufferData)
    upload.mockClear()

    state.polylines[0]!.points[0]!.x = -0.55
    state.polygons[0]!.points[0]!.x = -0.75
    renderer.invalidateMultipleGeometry(['line', 'baseline:line'])
    renderer.renderFrame(state, scale, view, () => [])

    const positions = upload.mock.calls.flatMap(call => call[1] instanceof Float32Array ? [Array.from(call[1])] : [])
    expect(positions.some(points => points.length > 4 && Math.abs(points[0]! + 0.55) < 0.00001)).toBe(true)
    expect(positions.some(points => Math.abs(points[0]! + 0.75) < 0.00001)).toBe(true)
    renderer.cleanup()
  })

  it('does not render a hidden baseline or its hover highlight', () => {
    const { renderer } = setupImageUpload(1000, 800)
    const stroke = vi.spyOn(ThickLineRenderer.prototype, 'drawThickLine')
    const state = baselineRenderState()
    state.hiddenPolylineIds.value = ['baseline:line']
    state.hoveredPolylineIndex.value = 0
    renderer.renderFrame(state, scale, view, () => [0, 1, 2, 0, 2, 3])
    expect(stroke.mock.calls.some(call => call[0] === state.polylines[0]!.points)).toBe(false)
    renderer.cleanup()
  })

  it('reuses baseline geometry across casing passes, panning, and zooming', () => {
    const { renderer } = setupImageUpload(1000, 800)
    const generate = vi.spyOn(ThickLineRenderer.prototype, 'generateThickLineVertices')
    const state = baselineRenderState()
    state.selectedPolylineIndex.value = -1
    renderer.renderFrame(state, scale, view, () => [0, 1, 2, 0, 2, 3])
    renderer.renderFrame(state, scale, { ...view, offsetX: 0.1 }, () => [])
    renderer.renderFrame(state, scale, { ...view, zoom: 4 }, () => [])

    expect(generate.mock.calls.filter(call => call[0] === state.polylines[0]!.points)).toHaveLength(1)
    renderer.cleanup()
  })

  it('preserves multi-selection feedback when hovering a selected baseline', () => {
    const { renderer } = setupImageUpload(1000, 800)
    const stroke = vi.spyOn(ThickLineRenderer.prototype, 'drawThickLine')
    const state = baselineRenderState()
    state.selectedPolylineIndex.value = -1
    state.selectedPolylineIds.value = ['baseline:line']
    state.hoveredPolylineIndex.value = 0
    renderer.renderFrame(state, scale, view, () => [0, 1, 2, 0, 2, 3])

    const baselineColors = stroke.mock.calls.filter(call => call[0] === state.polylines[0]!.points).map(call => call[1])
    expect(baselineColors).toContainEqual(RENDER_COLORS.SELECTED_BLUE)
    expect(baselineColors).not.toContainEqual(RENDER_COLORS.ACTIVE_VIOLET_BASELINE)
    renderer.cleanup()
  })
})

describe('WebGL raster image upload', () => {
  it.each([
    [12000, 6000, 4096, 2048],
    [6000, 12000, 2048, 4096],
    [12000, 12000, 4096, 4096]
  ])('loads a %ix%i raster within the GPU limit', async (width, height, textureWidth, textureHeight) => {
    const { renderer, invalidUploads, uploads, drawImage } = setupImageUpload(width, height)
    await renderer.loadAndRender('oversized-page.png')

    expect(invalidUploads).toEqual([])
    expect(uploads).toEqual([{ width: textureWidth, height: textureHeight }])
    expect(drawImage).toHaveBeenCalledWith(expect.anything(), 0, 0, textureWidth, textureHeight)
    // Coordinate conversion must continue using the original page dimensions.
    expect(renderer.imageSize.value).toEqual({ width, height })
    renderer.cleanup()
  })

  it.each([[2000, 3000], [4096, 4096]])('uploads a %ix%i image directly', async (width, height) => {
    const { renderer, invalidUploads, uploads, createElement } = setupImageUpload(width, height)
    await renderer.loadAndRender('page.png')

    expect(invalidUploads).toEqual([])
    expect(uploads).toEqual([{ width, height }])
    expect(createElement).not.toHaveBeenCalled()
    expect(renderer.imageSize.value).toEqual({ width, height })
    renderer.cleanup()
  })
})
