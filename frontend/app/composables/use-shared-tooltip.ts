import { onBeforeUnmount, onMounted } from 'vue'

export const SHARED_TOOLTIP_DELAY_MS = 200

function findTooltipTarget(node: EventTarget | null): HTMLElement | null {
  if (!(node instanceof Element)) return null
  const target = node.closest<HTMLElement>('[data-tooltip]')
  return target?.dataset.tooltip ? target : null
}

/**
 * Shows one tooltip for every element with a `data-tooltip` attribute, so long lists do not have
 * to mount a tooltip component per button. Hover shows it after a short delay, keyboard focus at once.
 */
export function useSharedTooltip(root: () => Document | HTMLElement | null = () => document) {
  const reference = shallowRef<HTMLElement | null>(null)
  const text = ref('')
  const open = ref(false)
  let showTimer: ReturnType<typeof setTimeout> | null = null

  function clearTimer() {
    if (showTimer) clearTimeout(showTimer)
    showTimer = null
  }

  function show(target: HTMLElement, delay: number) {
    clearTimer()
    showTimer = setTimeout(() => {
      showTimer = null
      if (!target.isConnected) return
      reference.value = target
      text.value = target.dataset.tooltip ?? ''
      open.value = true
    }, delay)
  }

  function hide() {
    clearTimer()
    open.value = false
  }

  function onPointerOver(event: PointerEvent) {
    const target = findTooltipTarget(event.target)
    if (!target) return
    if (open.value && target === reference.value) return
    show(target, SHARED_TOOLTIP_DELAY_MS)
  }

  function onPointerOut(event: PointerEvent) {
    const target = findTooltipTarget(event.target)
    if (!target) return
    const next = event.relatedTarget
    if (next instanceof Node && target.contains(next)) return
    hide()
  }

  function onFocusIn(event: FocusEvent) {
    const target = findTooltipTarget(event.target)
    if (target?.matches(':focus-visible')) show(target, 0)
  }

  const listeners = [
    ['pointerover', onPointerOver],
    ['pointerout', onPointerOut],
    ['pointerdown', hide],
    ['focusin', onFocusIn],
    ['focusout', hide],
    ['keydown', hide]
  ] as const
  let listenedRoot: Document | HTMLElement | null = null

  onMounted(() => {
    listenedRoot = root()
    for (const [type, listener] of listeners) listenedRoot?.addEventListener(type, listener as EventListener)
  })

  onBeforeUnmount(() => {
    clearTimer()
    for (const [type, listener] of listeners) listenedRoot?.removeEventListener(type, listener as EventListener)
    listenedRoot = null
  })

  return { reference, text, open, hide }
}
