import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import { getScrollContainer } from './text-field-navigation'

// Content renders this far outside the visible part of the scroll container, so it is ready before it scrolls into view.
export const RENDER_MARGIN = '150% 0px'
// Rendered content is only dropped once it is this far away, so scrolling back and forth does not rebuild it every time.
export const KEEP_MARGIN = '400% 0px'

type IdleHandle = { cancel: () => void }

function scheduleIdle(callback: () => void): IdleHandle {
  if (typeof requestIdleCallback === 'function') {
    const id = requestIdleCallback(callback, { timeout: 2000 })
    return { cancel: () => cancelIdleCallback(id) }
  }
  const id = setTimeout(callback, 200)
  return { cancel: () => clearTimeout(id) }
}

// Drops from all elements run together, so the browser lays out the list once instead of once per element.
const pendingDrops = new Set<() => void>()
let pendingFlush: IdleHandle | null = null

function flushDrops() {
  pendingFlush = null
  const drops = [...pendingDrops]
  pendingDrops.clear()
  for (const drop of drops) drop()
}

function queueDrop(drop: () => void) {
  pendingDrops.add(drop)
  pendingFlush ??= scheduleIdle(flushDrops)
}

function unqueueDrop(drop: () => void) {
  pendingDrops.delete(drop)
  if (pendingDrops.size === 0) {
    pendingFlush?.cancel()
    pendingFlush = null
  }
}

/**
 * Renders an element's content once it comes near the visible part of its scroll container and
 * drops it again when it is far away, while it holds no focus and `keepRendered` is false. Dropping
 * waits for idle time, and the last rendered height is kept so the placeholder takes the same space
 * and the scroll position stays put.
 */
export function useRenderNearViewport(target: Ref<HTMLElement | null>, keepRendered: () => boolean) {
  const isRendered = ref(false)
  const containsFocus = ref(false)
  const placeholderHeight = ref<number | null>(null)
  const shouldRender = computed(() => isRendered.value || containsFocus.value || keepRendered())

  watch(shouldRender, (render) => {
    if (!render && target.value) placeholderHeight.value = target.value.clientHeight || null
  })

  function drop() {
    isRendered.value = false
  }

  function cancelDrop() {
    unqueueDrop(drop)
  }

  function onNear(entries: IntersectionObserverEntry[]) {
    if (!entries[entries.length - 1]?.isIntersecting) return
    cancelDrop()
    isRendered.value = true
  }

  function onKeep(entries: IntersectionObserverEntry[]) {
    if (entries[entries.length - 1]?.isIntersecting) {
      cancelDrop()
      return
    }
    if (isRendered.value) queueDrop(drop)
  }

  function onFocusIn() {
    containsFocus.value = true
  }

  function onFocusOut(event: FocusEvent) {
    const next = event.relatedTarget
    containsFocus.value = next instanceof Node && Boolean(target.value?.contains(next))
  }

  const observers: IntersectionObserver[] = []

  onMounted(() => {
    const el = target.value
    if (!el) return

    el.addEventListener('focusin', onFocusIn)
    el.addEventListener('focusout', onFocusOut)

    if (typeof IntersectionObserver === 'undefined') {
      isRendered.value = true
      return
    }
    const root = getScrollContainer(el)
    for (const [callback, rootMargin] of [[onNear, RENDER_MARGIN], [onKeep, KEEP_MARGIN]] as const) {
      const observer = new IntersectionObserver(callback, { root, rootMargin, threshold: 0 })
      observer.observe(el)
      observers.push(observer)
    }
  })

  onBeforeUnmount(() => {
    cancelDrop()
    for (const observer of observers) observer.disconnect()
    observers.length = 0
    target.value?.removeEventListener('focusin', onFocusIn)
    target.value?.removeEventListener('focusout', onFocusOut)
  })

  return { shouldRender, placeholderHeight }
}
