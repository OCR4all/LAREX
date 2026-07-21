import type { CSSProperties, ComputedRef, Ref } from 'vue'
import {
  getEditorFloatingAnchorElement,
  getEditorFloatingAnchorRect,
  useEditorFloatingAnchorRegistryVersion
} from '@/session/editor/editor-session'
import {
  clampFloatingPosition,
  computeFloatingDefaultPosition,
  resolveFloatingControlPosition,
  toFloatingControlOffset,
  type FloatingControlOffset,
  type FloatingControlPlacement,
  type FloatingControlPosition,
  type FloatingControlSize
} from '@/utils/editor/floating-anchor-position'

type UseFloatingAnchorPositionOptions = {
  enabled: ComputedRef<boolean>
  anchorId: ComputedRef<string | null | undefined>
  shellRef: Ref<HTMLElement | null>
  placement: FloatingControlPlacement
  fallbackSize: FloatingControlSize
  gap: number
  includeFixedPosition?: boolean
  viewportMargin?: number | Partial<Record<'top' | 'right' | 'bottom' | 'left', number>>
  getOffset: () => FloatingControlOffset | null
  setOffset: (offset: FloatingControlOffset | null) => void
}

type DragState = {
  pointerId: number
  startClientX: number
  startClientY: number
  startPosition: FloatingControlPosition
}

const VIEWPORT_MARGIN = 8

export function useFloatingAnchorPosition(options: UseFloatingAnchorPositionOptions) {
  const registryVersion = useEditorFloatingAnchorRegistryVersion()
  const resolvedPosition = ref<FloatingControlPosition | null>(null)
  const isDragging = ref(false)

  let dragState: DragState | null = null
  let shellResizeObserver: ResizeObserver | null = null
  let anchorResizeObserver: ResizeObserver | null = null

  function getViewportSize() {
    return {
      width: window.innerWidth || document.documentElement.clientWidth,
      height: window.innerHeight || document.documentElement.clientHeight
    }
  }

  function getControlSize(): FloatingControlSize {
    const rect = options.shellRef.value?.getBoundingClientRect()
    return {
      width: rect?.width && rect.width > 0 ? rect.width : options.fallbackSize.width,
      height: rect?.height && rect.height > 0 ? rect.height : options.fallbackSize.height
    }
  }

  function getDefaultPosition(): FloatingControlPosition {
    return computeFloatingDefaultPosition({
      placement: options.placement,
      anchorRect: getEditorFloatingAnchorRect(options.anchorId.value),
      controlSize: getControlSize(),
      viewport: getViewportSize(),
      gap: options.gap
    })
  }

  function computePosition(offset = options.getOffset()): FloatingControlPosition {
    return resolveFloatingControlPosition({
      defaultPosition: getDefaultPosition(),
      controlSize: getControlSize(),
      viewport: getViewportSize(),
      offset,
      margin: options.viewportMargin ?? VIEWPORT_MARGIN
    })
  }

  function syncPosition() {
    if (!import.meta.client || !options.enabled.value) {
      resolvedPosition.value = null
      return
    }

    resolvedPosition.value = computePosition()
  }

  function reconnectShellObserver() {
    if (shellResizeObserver) {
      shellResizeObserver.disconnect()
      shellResizeObserver = null
    }

    if (!import.meta.client || typeof ResizeObserver === 'undefined' || !options.shellRef.value) return

    shellResizeObserver = new ResizeObserver(() => {
      syncPosition()
    })
    shellResizeObserver.observe(options.shellRef.value)
  }

  function reconnectAnchorObserver() {
    if (anchorResizeObserver) {
      anchorResizeObserver.disconnect()
      anchorResizeObserver = null
    }

    if (!import.meta.client || typeof ResizeObserver === 'undefined') return

    const anchorElement = getEditorFloatingAnchorElement(options.anchorId.value)
    if (!anchorElement) return

    anchorResizeObserver = new ResizeObserver(() => {
      syncPosition()
    })
    anchorResizeObserver.observe(anchorElement)
  }

  function stopDrag(event?: PointerEvent) {
    if (event && dragState && event.pointerId !== dragState.pointerId) return

    window.removeEventListener('pointermove', handleDragMove)
    window.removeEventListener('pointerup', stopDrag)
    window.removeEventListener('pointercancel', stopDrag)

    dragState = null
    isDragging.value = false
  }

  function handleDragMove(event: PointerEvent) {
    if (!dragState || event.pointerId !== dragState.pointerId) return

    const nextPosition = clampFloatingPosition({
      position: {
        x: dragState.startPosition.x + event.clientX - dragState.startClientX,
        y: dragState.startPosition.y + event.clientY - dragState.startClientY
      },
      controlSize: getControlSize(),
      viewport: getViewportSize(),
      margin: options.viewportMargin ?? VIEWPORT_MARGIN
    })

    options.setOffset(toFloatingControlOffset(nextPosition, getDefaultPosition()))
    resolvedPosition.value = nextPosition
  }

  function startDrag(event: PointerEvent) {
    if (!options.enabled.value) return

    const startPosition = resolvedPosition.value ?? computePosition()
    dragState = {
      pointerId: event.pointerId,
      startClientX: event.clientX,
      startClientY: event.clientY,
      startPosition
    }
    isDragging.value = true

    window.addEventListener('pointermove', handleDragMove)
    window.addEventListener('pointerup', stopDrag)
    window.addEventListener('pointercancel', stopDrag)
  }

  function handleWindowResize() {
    syncPosition()
  }

  onMounted(() => {
    if (!import.meta.client) return

    reconnectShellObserver()
    reconnectAnchorObserver()
    requestAnimationFrame(() => syncPosition())
    window.addEventListener('resize', handleWindowResize)
  })

  onBeforeUnmount(() => {
    if (!import.meta.client) return

    window.removeEventListener('resize', handleWindowResize)
    if (shellResizeObserver) shellResizeObserver.disconnect()
    if (anchorResizeObserver) anchorResizeObserver.disconnect()
    stopDrag()
  })

  watch(() => options.shellRef.value, () => {
    if (!import.meta.client) return
    reconnectShellObserver()
    requestAnimationFrame(() => syncPosition())
  })

  watch(
    () => [options.enabled.value, options.anchorId.value, registryVersion.value] as const,
    ([enabled]) => {
      if (!import.meta.client) return
      if (!enabled) {
        stopDrag()
        resolvedPosition.value = null
      }
      reconnectAnchorObserver()
      requestAnimationFrame(() => syncPosition())
    },
    { immediate: true }
  )

  watch(
    () => {
      const offset = options.getOffset()
      return offset ? `${offset.dx}:${offset.dy}` : null
    },
    () => {
      if (!import.meta.client) return
      requestAnimationFrame(() => syncPosition())
    }
  )

  const style = computed<CSSProperties | undefined>(() => {
    if (!import.meta.client) return undefined
    if (!options.enabled.value) return undefined

    const position = resolvedPosition.value ?? computePosition()
    return {
      ...(options.includeFixedPosition ? { position: 'fixed' as const } : {}),
      left: `${position.x}px`,
      top: `${position.y}px`
    }
  })

  return {
    style,
    isDragging,
    syncPosition,
    startDrag,
    stopDrag
  }
}
