import { shallowRef } from 'vue'

/**
 * Supports popovers that are only mounted while open and anchored to an existing button through
 * `reference`. Clicking the button while the popover is open first dismisses it as an outside
 * pointer-down; `shouldOpen` keeps the following click from opening it again.
 */
export function useLazyPopoverTrigger() {
  const anchor = shallowRef<HTMLElement | null>(null)
  let openOnPointerDown = false

  function onPointerDown(isOpen: boolean) {
    openOnPointerDown = isOpen
  }

  function shouldOpen(event: Event | null, isOpen: boolean): boolean {
    const wasOpen = openOnPointerDown || isOpen
    openOnPointerDown = false
    if (wasOpen) return false
    if (event?.currentTarget instanceof HTMLElement) anchor.value = event.currentTarget
    return true
  }

  return { anchor, onPointerDown, shouldOpen }
}
