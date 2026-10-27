// @vitest-environment happy-dom
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'

// Exercise the production setup and template, with only Nuxt's shell/API stubbed.
const descriptor = parse(readFileSync(resolve('app/components/page/modal/images.vue'), 'utf8')).descriptor
const script = compileScript(descriptor, { id: 'images-zoom-test' })
const template = compileTemplate({
  id: 'images-zoom-test', source: descriptor.template!.content, filename: 'images.vue',
  compilerOptions: { bindingMetadata: script.bindings }
})
const compiled = transpileModule(`${script.content}\n${template.code}`, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
const exportsObject: { default?: Vue.Component & { setup: (...args: unknown[]) => Record<string, unknown> }, render?: Vue.RenderFunction } = {}
new Function('require', 'exports', compiled)((id: string) => {
  if (id === 'vue') return Vue
  if (id === '#components') return { LazyUiDeleteSlideover: {} }
  throw new Error(`Unexpected dependency: ${id}`)
}, exportsObject)

function wheelEvent(options: WheelEventInit) {
  const event = new WheelEvent('wheel', options)
  // happy-dom's WheelEvent does not initialize its MouseEvent coordinates.
  Object.defineProperties(event, { clientX: { value: options.clientX ?? 0 }, clientY: { value: options.clientY ?? 0 } })
  return event
}

const mountedApps: Vue.App[] = []
afterEach(() => {
  mountedApps.forEach(app => app.unmount())
  mountedApps.length = 0
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
})

function mountViewer() {
  vi.stubGlobal('useAttrs', () => ({}))
  vi.stubGlobal('useToast', () => ({ add: vi.fn() }))
  vi.stubGlobal('useOverlay', () => ({ create: () => ({}) }))
  vi.stubGlobal('useBackgroundDownloads', () => ({}))
  vi.stubGlobal('onMounted', () => {})
  vi.stubGlobal('onUnmounted', () => {})
  const component = exportsObject.default!
  let state: Record<string, unknown>
  const app = Vue.createApp({
    ...component,
    setup(props: unknown, context: unknown) {
      state = component.setup(props, context)
      return state
    },
    render: exportsObject.render
  }, { projectId: 'project', pages: [{ id: 'page', name: 'Page' }, { id: 'other', name: 'Other' }], initialPageIndex: 0 })
  const shell = Vue.defineComponent({ setup: (_, { slots }) => () => Vue.h('div', slots.body?.()) })
  app.component('UiResponsiveSlideover', shell)
  for (const name of ['UCarousel', 'UTabs', 'UButton', 'UBadge', 'UIcon', 'USelectMenu', 'UiSlideoverHeader', 'USkeleton', 'UDropdownMenu', 'UiImageCompare']) {
    app.component(name, Vue.defineComponent({ setup: () => () => null }))
  }
  const host = document.createElement('div')
  document.body.append(host)
  app.mount(host)
  mountedApps.push(app)
  const setup = state! as {
    pageImages: Vue.Ref<Array<{ id: string }>>
    currentPageId: Vue.Ref<string>
    isLoading: Vue.Ref<boolean>
    fetchPageImages: () => Promise<void>
    retryLightboxImage: () => void
    getImageSrc: (id: string, size?: 'overview' | 'detail') => string
    lightboxSrc: Vue.ComputedRef<string>
    lightboxOriginal: Vue.Ref<boolean>
    lightboxImageState: Vue.Ref<string>
    toggleLightboxResolution: () => void
    openLightbox: (image: unknown) => void
    zoomIn: () => void
    zoomOut: () => void
    resetLightboxView: () => void
    handleLightboxWheel: (event: WheelEvent) => void
    lightboxViewport: Vue.Ref<HTMLElement | null>
    lightboxImageElement: Vue.Ref<HTMLImageElement | null>
    lightboxNext: () => void
    isDragging: Vue.Ref<boolean>
    handleLightboxPointerDown: (event: PointerEvent) => void
    handleLightboxPointerMove: (event: PointerEvent) => void
    handleLightboxPointerUp: () => void
    lightboxScale: Vue.Ref<number>
    lightboxPosition: Vue.Ref<{ x: number, y: number }>
  }
  const image = { id: 'image', fileName: 'scan.png', baseName: 'scan', fileSize: 100, variant: '' }
  setup.pageImages.value = [image]
  setup.openLightbox(image)
  return { setup, host }
}

describe('Image detail zoom', () => {
  it('keeps the fitted image dimensions on the first zoom step', async () => {
    const { setup, host } = mountViewer()
    await Vue.nextTick()
    const image = host.querySelector('img')!
    const fittedWidth = image.style.maxWidth
    const fittedHeight = image.style.maxHeight
    setup.zoomIn()
    await Vue.nextTick()
    // Removing the fit constraints switches to the scan's full native size,
    // producing a many-fold jump instead of the requested 25% zoom.
    expect(image.style.maxWidth).toBe(fittedWidth)
    expect(image.style.maxHeight).toBe(fittedHeight)
    expect(setup.lightboxScale.value).toBe(1.25)
  })

  async function measuredViewer() {
    const viewer = mountViewer()
    await Vue.nextTick()
    viewer.setup.lightboxImageState.value = 'loaded'
    const viewport = viewer.setup.lightboxViewport.value!
    const image = viewer.setup.lightboxImageElement.value!
    Object.defineProperties(viewport, { clientWidth: { value: 1000 }, clientHeight: { value: 800 } })
    Object.defineProperties(image, { clientWidth: { value: 800 }, clientHeight: { value: 600 } })
    viewport.getBoundingClientRect = () => ({ left: 100, top: 50, width: 1000, height: 800 }) as DOMRect
    return viewer
  }

  it('uses small wheel deltas for fine zoom and ignores horizontal-only scrolling', async () => {
    const { setup } = await measuredViewer()
    setup.handleLightboxWheel(wheelEvent({ deltaY: -1, clientX: 600, clientY: 450 }))
    expect(setup.lightboxScale.value).toBeCloseTo(Math.exp(0.002))
    const scale = setup.lightboxScale.value
    setup.handleLightboxWheel(wheelEvent({ deltaX: 10, deltaY: 0 }))
    expect(setup.lightboxScale.value).toBe(scale)
  })

  it('keeps the point under the cursor stationary when zoomed in', async () => {
    const { setup } = await measuredViewer()
    for (let i = 0; i < 4; i++) setup.zoomIn()
    const oldScale = setup.lightboxScale.value
    // Pointer is 100px right and 50px below the viewport center.
    setup.handleLightboxWheel(wheelEvent({ deltaY: -20, clientX: 700, clientY: 500 }))
    const ratio = setup.lightboxScale.value / oldScale
    expect(setup.lightboxPosition.value.x).toBeCloseTo(100 * (1 - ratio))
    expect(setup.lightboxPosition.value.y).toBeCloseTo(50 * (1 - ratio))
  })

  it('captures drags, constrains panning and recenters when zooming out', async () => {
    const { setup } = await measuredViewer()
    const viewport = setup.lightboxViewport.value!
    viewport.setPointerCapture = vi.fn()
    for (let i = 0; i < 4; i++) setup.zoomIn()
    setup.handleLightboxPointerDown({ button: 0, isPrimary: true, pointerId: 1, clientX: 600, clientY: 450 } as PointerEvent)
    expect(viewport.setPointerCapture).toHaveBeenCalledWith(1)
    setup.handleLightboxPointerMove({ isPrimary: true, clientX: 10000, clientY: -10000 } as PointerEvent)
    expect(setup.lightboxPosition.value.x).toBeCloseTo((800 * setup.lightboxScale.value - 1000) / 2)
    expect(setup.lightboxPosition.value.y).toBeCloseTo(-(600 * setup.lightboxScale.value - 800) / 2)
    setup.handleLightboxPointerUp()
    expect(setup.isDragging.value).toBe(false)
    for (let i = 0; i < 4; i++) setup.zoomOut()
    expect(setup.lightboxPosition.value).toEqual({ x: 0, y: 0 })
  })

  it('resets the transform on image navigation and enforces zoom limits', async () => {
    const { setup } = await measuredViewer()
    for (let i = 0; i < 30; i++) setup.zoomIn()
    expect(setup.lightboxScale.value).toBe(5)
    setup.lightboxPosition.value = { x: 100, y: 50 }
    setup.lightboxNext()
    expect(setup.lightboxScale.value).toBe(1)
    expect(setup.lightboxPosition.value).toEqual({ x: 0, y: 0 })
    for (let i = 0; i < 30; i++) setup.zoomOut()
    expect(setup.lightboxScale.value).toBe(0.5)
  })

  it('requests bounded presentations by default and originals only on explicit request', async () => {
    const { setup } = mountViewer()
    expect(setup.getImageSrc('image')).toBe('/api/projects/project/pages/images/image/presentation?size=overview')
    expect(setup.lightboxSrc.value).toBe('/api/projects/project/pages/images/image/presentation?size=detail')
    setup.toggleLightboxResolution()
    expect(setup.lightboxSrc.value).toBe('/api/projects/project/pages/images/image/presentation?size=original')
    expect(setup.lightboxImageState.value).toBe('loading')
    setup.lightboxNext()
    expect(setup.lightboxOriginal.value).toBe(false)
    expect(setup.lightboxSrc.value).toContain('/presentation?size=detail')
  })

  it('shows load failures and retries the presentation without silently loading the original', async () => {
    const { setup, host } = mountViewer()
    await Vue.nextTick()
    const image = host.querySelector('img')!
    image.dispatchEvent(new Event('error'))
    await Vue.nextTick()
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Failed to load image')
    expect(setup.lightboxSrc.value).toContain('/presentation?size=detail')
    setup.retryLightboxImage()
    await Vue.nextTick()
    expect(host.querySelector('img')).not.toBe(image)
    expect(host.querySelector('[role="status"]')).not.toBeNull()
    host.querySelector('img')!.dispatchEvent(new Event('load'))
    await Vue.nextTick()
    expect(setup.lightboxImageState.value).toBe('loaded')
  })

  it('ignores wheel gestures while the image is still loading', async () => {
    const { setup } = await measuredViewer()
    setup.lightboxImageState.value = 'loading'
    setup.handleLightboxWheel(wheelEvent({ deltaY: -100, clientX: 700, clientY: 500 }))
    expect(setup.lightboxScale.value).toBe(1)
  })

  it('ignores a stale page response after navigating to a different page', async () => {
    const { setup } = mountViewer()
    let resolveFirst!: (images: Array<{ id: string }>) => void
    let resolveSecond!: (images: Array<{ id: string }>) => void
    vi.stubGlobal('$fetch', vi.fn()
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveSecond = resolve })))
    const first = setup.fetchPageImages()
    setup.currentPageId.value = 'other'
    const second = setup.fetchPageImages()
    resolveSecond([{ id: 'current' }])
    await second
    resolveFirst([{ id: 'stale' }])
    await first
    expect(setup.pageImages.value.map(image => image.id)).toEqual(['current'])
    expect(setup.isLoading.value).toBe(false)
  })
})
