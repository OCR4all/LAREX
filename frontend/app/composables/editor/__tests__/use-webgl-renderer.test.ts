import { afterEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useWebglRenderer } from '../use-webgl-renderer'

vi.mock('@/stores/editor/editor.ui.store', () => ({ useEditorUiStore: () => ({}) }))
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
        else if (key.startsWith('create') || key === 'getUniformLocation') target[key] = () => ({})
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
  const canvas = { getContext: () => gl } as unknown as HTMLCanvasElement
  const renderer = useWebglRenderer(ref(canvas))
  renderer.initGL()
  return { renderer, invalidUploads, uploads, texImage2D, drawImage, createElement, textureCanvas }
}

afterEach(() => vi.unstubAllGlobals())

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
