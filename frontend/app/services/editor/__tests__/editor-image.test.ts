import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearEditorImageCache, editorPresentationUrl, editorRasterCrop, loadEditorImage } from '../editor-image'
import { imageToWorld, worldToImage } from '@/utils/editor/coordinates'

beforeEach(() => {
  clearEditorImageCache()
  vi.stubGlobal('Image', class {
    naturalWidth = 3200
    naturalHeight = 2102
    onload = () => {}
    set src(value: string) {
      if (value.endsWith('/blob')) {
        this.naturalWidth = 19218
        this.naturalHeight = 12623
      }
      queueMicrotask(() => this.onload())
    }
  })
  vi.stubGlobal('URL', { createObjectURL: () => 'blob:preview', revokeObjectURL: vi.fn() })
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function response(width = 19218, height = 12623) {
  return new Response(new Blob(['jpeg']), {
    headers: { 'X-Original-Width': String(width), 'X-Original-Height': String(height) }
  })
}

describe('Editor presentation images', () => {
  it.each([
    '/api/projects/project/pages/images/scan/blob',
    '/api/workspaces/ws/datasets/ds/items/item/images/scan/blob'
  ])('uses an authenticated bounded presentation for %s and shares it between consumers', async (src) => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(response()))
    vi.stubGlobal('fetch', fetch)
    const [canvas, cutout] = await Promise.all([loadEditorImage(src), loadEditorImage(src)])
    expect(canvas).toBe(cutout)
    expect(canvas.originalSize).toEqual({ width: 19218, height: 12623 })
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith(src.replace('/blob', '/presentation?size=detail'), expect.objectContaining({ credentials: 'same-origin', signal: expect.any(AbortSignal) }))
    const point = { x: 18000, y: 12000 }
    const roundTrip = worldToImage(imageToWorld(point, canvas.originalSize), canvas.originalSize)
    expect(roundTrip.x).toBeCloseTo(point.x)
    expect(roundTrip.y).toBeCloseTo(point.y)
  })

  it('rejects missing source dimensions and failed previews without falling back to originals', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(new Blob(['jpeg'])))
    vi.stubGlobal('fetch', fetch)
    const src = '/api/projects/p/pages/images/scan/blob'
    await expect(loadEditorImage(src)).rejects.toThrow('missing original image dimensions')
    fetch.mockResolvedValueOnce(response())
    await expect(loadEditorImage(src)).resolves.toHaveProperty('originalSize.width', 19218)
    expect(fetch.mock.calls.every(([url]) => String(url).includes('/presentation?'))).toBe(true)
  })

  it('loads originals only when explicitly requested', async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(response(1200, 800)))
    vi.stubGlobal('fetch', fetch)
    const loaded = await loadEditorImage('/api/projects/p/pages/images/image/presentation?size=detail', { original: true })
    expect(loaded.originalSize).toEqual({ width: 1200, height: 800 })
    expect(fetch).toHaveBeenCalledWith('/api/projects/p/pages/images/image/presentation?size=original', expect.anything())
  })

  it('uses bounded thumbnails and leaves local images alone', () => {
    expect(editorPresentationUrl('/api/projects/p/pages/images/i/thumbnail', 'thumbnail')).toBe('/api/projects/p/pages/images/i/presentation?size=thumbnail')
    expect(editorPresentationUrl('blob:local-image')).toBe('blob:local-image')
  })

  it('maps a text crop near the bottom-right of a large scan onto the bounded raster', () => {
    const original = { width: 19218, height: 12623 }
    const raster = { width: 3200, height: 2102 }
    const crop = editorRasterCrop({ x: 18000, y: 12000, width: 1000, height: 200 }, original, raster, 0.5)!
    expect(crop[0]).toBeCloseTo(18000 / 19218 * 3200)
    expect(crop[1]).toBeCloseTo(12000 / 12623 * 2102)
    expect(crop.slice(4)).toEqual([0, 0, 500, 100])
    expect(crop[0] + crop[2]).toBeLessThan(raster.width)
    expect(crop[1] + crop[3]).toBeLessThan(raster.height)
  })

  it('clips out-of-page crops while preserving their output offset', () => {
    expect(editorRasterCrop({ x: -100, y: -20, width: 400, height: 100 }, { width: 1000, height: 1000 }, { width: 100, height: 100 }, 2))
      .toEqual([0, 0, 30, 8, 200, 40, 600, 160])
    expect(editorRasterCrop({ x: 2000, y: 0, width: 100, height: 100 }, { width: 1000, height: 1000 }, { width: 100, height: 100 }, 1)).toBeNull()
  })

  it('evicts older decoded previews instead of retaining a whole project', async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(response()))
    vi.stubGlobal('fetch', fetch)
    for (const id of ['a', 'b', 'c', 'd', 'a']) await loadEditorImage(`/api/projects/p/pages/images/${id}/blob`)
    expect(fetch).toHaveBeenCalledTimes(5)
  })

  function gatedFetch() {
    const gates = new Map<string, (response: Response) => void>()
    const signals = new Map<string, AbortSignal>()
    const fetch = vi.fn((url: string, options: RequestInit) => new Promise<Response>((resolve, reject) => {
      gates.set(url, resolve)
      signals.set(url, options.signal!)
      options.signal!.addEventListener('abort', () => reject(new DOMException('Cancelled', 'AbortError')), { once: true })
    }))
    vi.stubGlobal('fetch', fetch)
    return { fetch, gates, signals }
  }
  const src = (id: string) => `/api/projects/p/pages/images/${id}/blob`
  const preview = (id: string) => editorPresentationUrl(src(id))

  it('bounds active work and deduplicates pending images independently of LRU eviction', async () => {
    const { fetch, gates } = gatedFetch()
    const promises = ['a', 'b', 'c', 'd', 'a'].map(id => loadEditorImage(src(id)))
    expect(fetch).toHaveBeenCalledTimes(2)
    gates.get(preview('a'))!(response())
    await promises[0]
    await vi.waitFor(() => expect(gates.has(preview('c'))).toBe(true))
    gates.get(preview('b'))!(response())
    await vi.waitFor(() => expect(gates.has(preview('d'))).toBe(true))
    gates.get(preview('c'))!(response())
    gates.get(preview('d'))!(response())
    const values = await Promise.all(promises)
    expect(fetch).toHaveBeenCalledTimes(4)
    expect(values[0]).toBe(values[4])
  })

  it('cancels abandoned requests but keeps an image needed by another consumer', async () => {
    const { gates, signals } = gatedFetch()
    const first = new AbortController()
    const second = new AbortController()
    const a = loadEditorImage(src('shared'), { signal: first.signal })
    const b = loadEditorImage(src('shared'), { signal: second.signal })
    const rejected = expect(a).rejects.toMatchObject({ name: 'AbortError' })
    first.abort()
    await rejected
    expect(signals.get(preview('shared'))!.aborted).toBe(false)
    gates.get(preview('shared'))!(response())
    await b
    const last = new AbortController()
    const abandoned = loadEditorImage(src('abandoned'), { signal: last.signal })
    const aborted = expect(abandoned).rejects.toMatchObject({ name: 'AbortError' })
    last.abort()
    await aborted
    expect(signals.get(preview('abandoned'))!.aborted).toBe(true)
  })

  it('prioritizes visible images over queued prefetches', async () => {
    const { gates } = gatedFetch()
    const a = loadEditorImage(src('a'), { priority: 'prefetch' })
    const b = loadEditorImage(src('b'), { priority: 'prefetch' })
    const c = loadEditorImage(src('c'), { priority: 'prefetch' })
    const d = loadEditorImage(src('visible'))
    gates.get(preview('a'))!(response())
    await a
    await vi.waitFor(() => expect(gates.has(preview('visible'))).toBe(true))
    expect(gates.has(preview('c'))).toBe(false)
    gates.get(preview('visible'))!(response())
    await d
    await vi.waitFor(() => expect(gates.has(preview('c'))).toBe(true))
    gates.get(preview('b'))!(response())
    gates.get(preview('c'))!(response())
    await Promise.all([b, c])
  })

  it('retains source revisions and expires decoded cache entries', async () => {
    let now = 1000
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(response()))
    vi.stubGlobal('fetch', fetch)
    expect(editorPresentationUrl(`${src('revision')}?v=42`)).toContain('v=42')
    await loadEditorImage(`${src('revision')}?v=42`)
    await loadEditorImage(`${src('revision')}?v=42`)
    expect(fetch).toHaveBeenCalledTimes(1)
    now += 61_000
    await loadEditorImage(`${src('revision')}?v=42`)
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('loads an explicitly requested large original even after caching its preview', async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(response()))
    vi.stubGlobal('fetch', fetch)
    await loadEditorImage(src('large'))
    const original = await loadEditorImage(src('large'), { original: true })
    expect(original.originalSize).toEqual({ width: 19218, height: 12623 })
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(fetch).toHaveBeenLastCalledWith(editorPresentationUrl(src('large'), 'original'), expect.anything())
  })
  it('keeps an explicit original loading beyond the preview deadline and still allows cancellation', async () => {
    vi.useFakeTimers()
    try {
      const { signals } = gatedFetch()
      const controller = new AbortController()
      const pending = loadEditorImage(src('slow-original'), { original: true, signal: controller.signal })
      const cancelled = expect(pending).rejects.toMatchObject({ name: 'AbortError' })
      await vi.advanceTimersByTimeAsync(60_000)
      expect(signals.get(editorPresentationUrl(src('slow-original'), 'original'))!.aborted).toBe(false)
      controller.abort()
      await cancelled
    } finally { vi.useRealTimers() }
  })

  it('still retries a busy preview once', async () => {
    vi.useFakeTimers()
    try {
      const fetch = vi.fn().mockResolvedValueOnce(new Response('busy', { status: 503 })).mockResolvedValueOnce(response())
      vi.stubGlobal('fetch', fetch)
      const loading = loadEditorImage(src('busy-preview'))
      await vi.advanceTimersByTimeAsync(2000)
      await expect(loading).resolves.toHaveProperty('originalSize.width', 19218)
      expect(fetch).toHaveBeenCalledTimes(2)
    } finally { vi.useRealTimers() }
  })
})
