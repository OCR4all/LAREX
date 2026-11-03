import type { Ref } from 'vue'

// Keeps the focused line clear of sticky region headers at the top and floating panels at the bottom.
const FOCUS_AREA_TOP_INSET = 56
const FOCUS_AREA_BOTTOM_INSET = 96

function isVisibleElement(el: HTMLElement): boolean {
  return !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length)
}

export function getVisibleTextContentVariantTextareas(rootEl: Ref<HTMLElement | null>): HTMLTextAreaElement[] {
  const el = rootEl.value
  if (!el) return []

  const textareas = Array.from(el.querySelectorAll<HTMLTextAreaElement>('textarea[data-textequiv-pos]'))
  return textareas
    .filter(textarea => !textarea.disabled)
    .filter(textarea => !textarea.readOnly)
    .filter(textarea => isVisibleElement(textarea))
}

function getScrollContainer(el: HTMLElement): HTMLElement | null {
  for (let node = el.parentElement; node; node = node.parentElement) {
    const { overflowY } = getComputedStyle(node)
    if (overflowY === 'auto' || overflowY === 'scroll') return node
  }
  return null
}

/**
 * Focuses a text field and scrolls its line towards the vertical center, keeping the lines
 * above and below in view for context. At the start and end of the list, where centering is
 * not possible, the browser clamps the scroll position and the line sits closer to the edge.
 */
export function focusTextFieldInContext(field: HTMLElement): void {
  field.focus({ preventScroll: true })
  const line = field.closest<HTMLElement>('[data-textline-item]') ?? field
  const container = getScrollContainer(line)
  if (!container) {
    field.scrollIntoView({ block: 'nearest' })
    return
  }

  const viewport = container.getBoundingClientRect()
  const areaTop = viewport.top + Math.min(FOCUS_AREA_TOP_INSET, viewport.height / 4)
  const areaBottom = viewport.bottom - Math.min(FOCUS_AREA_BOTTOM_INSET, viewport.height / 4)
  const lines = Array.from(container.querySelectorAll<HTMLElement>('[data-textline-item]')).filter(isVisibleElement)
  const index = lines.indexOf(line)
  const current = line.getBoundingClientRect()
  const above = (index > 0 ? lines[index - 1]! : line).getBoundingClientRect()
  const below = (index >= 0 && index < lines.length - 1 ? lines[index + 1]! : line).getBoundingClientRect()

  // Center the line, then shift just enough to keep its neighbours (or at least the line itself) visible.
  const contextFits = below.bottom - above.top <= areaBottom - areaTop
  const first = contextFits ? above : current
  const last = contextFits ? below : current
  let delta = ((current.top + current.bottom) / 2) - ((areaTop + areaBottom) / 2)
  delta = Math.max(delta, last.bottom - areaBottom)
  delta = Math.min(delta, first.top - areaTop)

  container.scrollTop += delta
}

export function focusTextContentVariantAtOffset(rootEl: Ref<HTMLElement | null>, delta: 1 | -1): void {
  const all = getVisibleTextContentVariantTextareas(rootEl)
  if (all.length === 0) return

  const active = document.activeElement
  const currentIndex = active instanceof HTMLTextAreaElement ? all.indexOf(active) : -1
  const nextIndexRaw = currentIndex >= 0
    ? currentIndex + delta
    : (delta === 1 ? 0 : all.length - 1)

  const nextIndex = (nextIndexRaw + all.length) % all.length
  const next = all[nextIndex]
  if (next) focusTextFieldInContext(next)
}

export function focusNextSameIndex(rootEl: Ref<HTMLElement | null>): void {
  const all = getVisibleTextContentVariantTextareas(rootEl)
  if (all.length === 0) return

  const active = document.activeElement
  if (!(active instanceof HTMLTextAreaElement)) return

  const currentIndex = all.indexOf(active)
  if (currentIndex < 0) return

  const idx = active.dataset.textequivIndex
  if (!idx) return

  for (let step = 1; step <= all.length; step++) {
    const candidate = all[(currentIndex + step) % all.length]
    if (candidate?.dataset.textequivIndex === idx) {
      focusTextFieldInContext(candidate)
      return
    }
  }
}
