import type { ImageSize } from '@/models/editor'

export interface EditorImage {
  image: HTMLImageElement
  originalSize: ImageSize
}
export interface EditorImageOptions {
  original?: boolean
  size?: 'thumbnail' | 'overview' | 'detail'
  signal?: AbortSignal
  priority?: 'visible' | 'prefetch'
  refresh?: boolean
}
const projectImagePath = /^(\/api\/projects\/[^/]+\/pages\/images\/[^/]+)\/(?:blob|thumbnail|presentation)$/
const datasetImagePath = /^(\/api\/workspaces\/[^/]+\/datasets\/[^/]+\/items\/[^/]+\/images\/[^/]+)\/(?:blob|presentation)$/
function imageBase(src: string): string | undefined {
  const path = src.split('?')[0]!
  return projectImagePath.exec(path)?.[1] ?? datasetImagePath.exec(path)?.[1]
}
export function editorPresentationUrl(src: string, size: 'thumbnail' | 'overview' | 'detail' | 'original' = 'detail'): string {
  const base = imageBase(src)
  if (!base) return src
  const query = new URLSearchParams(src.split('?')[1] ?? '')
  query.set('size', size)
  return `${base}/presentation?${query}`
}
interface Consumer { resolve: (image: EditorImage) => void, reject: (error: unknown) => void, detach: () => void }
interface Job {
  key: string
  url: string
  original: boolean
  managed: boolean
  priority: number
  controller: AbortController
  consumers: Set<Consumer>
}
interface Cached { value: EditorImage, expires: number, bytes: number }
const completed = new Map<string, Cached>()
const pending = new Map<string, Job>()
const queue: Job[] = []
let active = 0
const CACHE_BYTES = 64 * 1024 * 1024
const CACHE_TTL = 60_000
const abortError = () => new DOMException('Image load cancelled', 'AbortError')

function decodeImage(src: string, signal: AbortSignal): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    let settled = false
    const finish = (error?: unknown) => {
      if (settled) return
      settled = true
      signal.removeEventListener('abort', cancel)
      image.onload = null
      image.onerror = null
      if (error) reject(error)
      else resolve(image)
    }
    const cancel = () => {
      finish(abortError())
      image.src = ''
    }
    if (signal.aborted) {
      cancel()
      return
    }
    signal.addEventListener('abort', cancel, { once: true })
    image.crossOrigin = 'anonymous'
    image.decoding = 'async'
    image.onload = () => finish()
    image.onerror = () => finish(new Error('Unable to load editor image'))
    image.src = src
  })
}
function delay(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const cancel = () => {
      clearTimeout(timer)
      reject(abortError())
    }
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', cancel)
      resolve()
    }, ms)
    if (signal.aborted) cancel()
    else signal.addEventListener('abort', cancel, { once: true })
  })
}
async function fetchImage(job: Job): Promise<EditorImage> {
  const signal = job.controller.signal
  if (!job.managed) {
    const image = await decodeImage(job.url, signal)
    return { image, originalSize: { width: image.naturalWidth, height: image.naturalHeight } }
  }
  let response = await fetch(job.url, { credentials: 'same-origin', signal })
  // One bounded retry for server backpressure; cancellation interrupts the wait.
  if (response.status === 503 || response.status === 504) {
    await response.body?.cancel()
    await delay(2000, signal)
    response = await fetch(job.url, { credentials: 'same-origin', signal })
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    throw new Error(data.statusMessage || data.message || `Unable to load image (${response.status})`)
  }
  const width = Number(response.headers.get('X-Original-Width'))
  const height = Number(response.headers.get('X-Original-Height'))
  if (!Number.isSafeInteger(width) || !Number.isSafeInteger(height) || width <= 0 || height <= 0)
    throw new Error('Editor preview is missing original image dimensions')
  const blob = await response.blob()
  if (signal.aborted) throw abortError()
  const url = URL.createObjectURL(blob)
  try {
    const image = await decodeImage(url, signal)
    return { image, originalSize: { width, height } }
  } finally { URL.revokeObjectURL(url) }
}
function cacheResult(key: string, value: EditorImage) {
  const bytes = value.image.naturalWidth * value.image.naturalHeight * 4
  if (bytes > CACHE_BYTES) return
  completed.set(key, { value, bytes, expires: Date.now() + CACHE_TTL })
  let total = [...completed.values()].reduce((sum, entry) => sum + entry.bytes, 0)
  while (completed.size > 3 || total > CACHE_BYTES) {
    const oldest = completed.keys().next().value!
    total -= completed.get(oldest)!.bytes
    completed.delete(oldest)
  }
}
function pump() {
  queue.sort((a, b) => a.priority - b.priority)
  while (active < 2 && queue.length) {
    const job = queue.shift()!
    if (!job.consumers.size || job.controller.signal.aborted) continue
    active++
    let timedOut = false
    const timer = job.original
      ? undefined
      : setTimeout(() => {
          timedOut = true
          job.controller.abort()
        }, 50_000)
    void fetchImage(job).then((value) => {
      if (job.controller.signal.aborted) throw abortError()
      if (!job.original && job.managed) cacheResult(job.key, value)
      if (pending.get(job.key) === job) pending.delete(job.key)
      for (const consumer of job.consumers) consumer.resolve(value)
    }).catch((error) => {
      if (pending.get(job.key) === job) pending.delete(job.key)
      for (const consumer of job.consumers) consumer.reject(timedOut ? new Error('Image request timed out. Please retry.') : error)
    }).finally(() => {
      clearTimeout(timer)
      for (const consumer of job.consumers) consumer.detach()
      job.consumers.clear()
      if (pending.get(job.key) === job) pending.delete(job.key)
      active--
      pump()
    })
  }
}
export function loadEditorImage(src: string, options: EditorImageOptions = {}): Promise<EditorImage> {
  if (options.signal?.aborted) return Promise.reject(abortError())
  const managed = Boolean(imageBase(src))
  let url = managed ? editorPresentationUrl(src, options.original ? 'original' : options.size ?? 'detail') : src
  if (options.refresh) url += `${url.includes('?') ? '&' : '?'}_reload=${Date.now()}`
  const key = url
  const cached = completed.get(key)
  if (cached && cached.expires > Date.now() && !options.original) {
    completed.delete(key)
    completed.set(key, cached)
    return Promise.resolve(cached.value)
  }
  completed.delete(key)
  let job = pending.get(key)
  if (job?.controller.signal.aborted) job = undefined
  if (!job) {
    if (queue.length >= 32) return Promise.reject(new Error('Too many image requests. Please retry shortly.'))
    job = { key, url, managed, original: Boolean(options.original), priority: options.priority === 'prefetch' ? 1 : 0,
      controller: new AbortController(), consumers: new Set() }
    pending.set(key, job)
    queue.push(job)
  }
  const current = job
  if (options.priority !== 'prefetch') current.priority = 0
  const result = new Promise<EditorImage>((resolve, reject) => {
    const cancel = () => {
      current.consumers.delete(consumer)
      consumer.detach()
      reject(abortError())
      if (!current.consumers.size) {
        current.controller.abort()
        if (pending.get(key) === current) pending.delete(key)
        const index = queue.indexOf(current)
        if (index >= 0) queue.splice(index, 1)
      }
    }
    const consumer: Consumer = { resolve, reject, detach: () => options.signal?.removeEventListener('abort', cancel) }
    current.consumers.add(consumer)
    options.signal?.addEventListener('abort', cancel, { once: true })
  })
  pump()
  return result
}
export function clearEditorImageCache(): void {
  completed.clear()
}

/** Convert a crop in original pixels into source-raster and destination pixels. */
export function editorRasterCrop(
  box: { x: number, y: number, width: number, height: number },
  original: ImageSize,
  raster: ImageSize,
  displayScale: number
): [number, number, number, number, number, number, number, number] | null {
  const x = Math.max(0, box.x)
  const y = Math.max(0, box.y)
  const width = Math.min(original.width, box.x + box.width) - x
  const height = Math.min(original.height, box.y + box.height) - y
  if (width <= 0 || height <= 0) return null
  const scaleX = raster.width / original.width
  const scaleY = raster.height / original.height
  return [x * scaleX, y * scaleY, width * scaleX, height * scaleY,
    (x - box.x) * displayScale, (y - box.y) * displayScale, width * displayScale, height * displayScale]
}
