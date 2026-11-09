// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, ref, type App } from 'vue'
import { KEEP_MARGIN, RENDER_MARGIN, useRenderNearViewport } from '../use-render-near-viewport'

type ObserverCallback = (entries: Array<{ isIntersecting: boolean }>) => void

const observers = new Map<string, { callback: ObserverCallback, options?: IntersectionObserverInit }>()
const disconnect = vi.fn()

class FakeIntersectionObserver {
  constructor(callback: ObserverCallback, options?: IntersectionObserverInit) {
    observers.set(String(options?.rootMargin), { callback, options })
  }

  observe() {}
  disconnect = disconnect
}

let app: App | null = null

function mountLine(keepRendered = ref(false)) {
  const state: { result?: ReturnType<typeof useRenderNearViewport>, target?: HTMLElement } = {}
  const container = document.createElement('div')
  container.style.overflowY = 'auto'
  document.body.append(container)

  app = createApp(defineComponent({
    setup() {
      const target = ref<HTMLElement | null>(null)
      state.result = useRenderNearViewport(target, () => keepRendered.value)
      return () => h('div', { ref: target }, [
        state.result!.shouldRender.value ? h('textarea') : null
      ])
    }
  }))
  app.mount(container)
  state.target = container.firstElementChild as HTMLElement
  return { result: state.result!, target: state.target, container, keepRendered }
}

function setNear(isIntersecting: boolean) {
  observers.get(RENDER_MARGIN)?.callback([{ isIntersecting }])
}

function setWithinKeepRange(isIntersecting: boolean) {
  observers.get(KEEP_MARGIN)?.callback([{ isIntersecting }])
}

function moveFarAway() {
  setNear(false)
  setWithinKeepRange(false)
  vi.runAllTimers()
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver)
  vi.stubGlobal('requestIdleCallback', undefined)
})

afterEach(() => {
  app?.unmount()
  app = null
  observers.clear()
  document.body.innerHTML = ''
  disconnect.mockClear()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('useRenderNearViewport', () => {
  it('observes against the scroll container with a render and a keep margin', () => {
    const { container } = mountLine()

    expect(observers.get(RENDER_MARGIN)?.options?.root).toBe(container)
    expect(observers.get(KEEP_MARGIN)?.options?.root).toBe(container)
  })

  it('renders near the viewport', () => {
    const { result } = mountLine()
    expect(result.shouldRender.value).toBe(false)

    setNear(true)

    expect(result.shouldRender.value).toBe(true)
  })

  it('keeps content while it is within the keep range', () => {
    const { result } = mountLine()
    setNear(true)

    setNear(false)
    setWithinKeepRange(true)
    vi.runAllTimers()

    expect(result.shouldRender.value).toBe(true)
  })

  it('drops content once idle after it leaves the keep range', () => {
    const { result } = mountLine()
    setNear(true)

    setNear(false)
    setWithinKeepRange(false)
    expect(result.shouldRender.value).toBe(true)

    vi.runAllTimers()
    expect(result.shouldRender.value).toBe(false)
  })

  it('cancels a pending drop when the content comes back', () => {
    const { result } = mountLine()
    setNear(true)
    setNear(false)
    setWithinKeepRange(false)

    setWithinKeepRange(true)
    vi.runAllTimers()

    expect(result.shouldRender.value).toBe(true)
  })

  it('drops all pending lines in one idle callback', () => {
    const idleCallbacks: Array<() => void> = []
    vi.stubGlobal('requestIdleCallback', (callback: () => void) => idleCallbacks.push(callback))
    vi.stubGlobal('cancelIdleCallback', () => {})
    const first = mountLine()
    const firstNear = observers.get(RENDER_MARGIN)!.callback
    const firstKeep = observers.get(KEEP_MARGIN)!.callback
    const firstApp = app
    const second = mountLine()
    firstNear([{ isIntersecting: true }])
    setNear(true)

    firstKeep([{ isIntersecting: false }])
    setWithinKeepRange(false)
    expect(idleCallbacks).toHaveLength(1)

    idleCallbacks[0]!()
    expect(first.result.shouldRender.value).toBe(false)
    expect(second.result.shouldRender.value).toBe(false)
    firstApp?.unmount()
  })

  it('keeps the last rendered height for the placeholder', async () => {
    const { result, target } = mountLine()
    setNear(true)
    await nextTick()
    Object.defineProperty(target, 'clientHeight', { configurable: true, value: 240 })

    moveFarAway()
    await nextTick()

    expect(result.placeholderHeight.value).toBe(240)
  })

  it('keeps content rendered while it holds focus', async () => {
    const { result, target } = mountLine()
    setNear(true)
    await nextTick()
    const textarea = target.querySelector('textarea')!
    textarea.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))

    moveFarAway()
    expect(result.shouldRender.value).toBe(true)

    textarea.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: document.body }))
    expect(result.shouldRender.value).toBe(false)
  })

  it('stays rendered when focus moves within the line', async () => {
    const { result, target } = mountLine()
    setNear(true)
    await nextTick()
    const textarea = target.querySelector('textarea')!
    textarea.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    moveFarAway()

    textarea.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: target }))

    expect(result.shouldRender.value).toBe(true)
  })

  it('renders while keepRendered is true even when far away', () => {
    const { result, keepRendered } = mountLine()

    keepRendered.value = true
    expect(result.shouldRender.value).toBe(true)

    keepRendered.value = false
    expect(result.shouldRender.value).toBe(false)
  })

  it('renders everything when IntersectionObserver is unavailable', () => {
    vi.stubGlobal('IntersectionObserver', undefined)
    const { result } = mountLine()

    expect(result.shouldRender.value).toBe(true)
  })

  it('disconnects both observers on unmount', () => {
    mountLine()
    app!.unmount()
    app = null

    expect(disconnect).toHaveBeenCalledTimes(2)
  })
})
