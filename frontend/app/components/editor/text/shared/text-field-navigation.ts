import type { Ref } from 'vue'

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

export function focusTextContentVariantAtOffset(rootEl: Ref<HTMLElement | null>, delta: 1 | -1): void {
  const all = getVisibleTextContentVariantTextareas(rootEl)
  if (all.length === 0) return

  const active = document.activeElement
  const currentIndex = active instanceof HTMLTextAreaElement ? all.indexOf(active) : -1
  const nextIndexRaw = currentIndex >= 0
    ? currentIndex + delta
    : (delta === 1 ? 0 : all.length - 1)

  const nextIndex = (nextIndexRaw + all.length) % all.length
  all[nextIndex]?.focus()
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
      candidate.focus()
      return
    }
  }
}
