export interface CanvasInstanceState {
  [key: string]: unknown
}

const activeCanvasId = ref<string | null>(null)
const canvasInstances = reactive(new Map<string, CanvasInstanceState>())

export interface UseCanvasStateReturn {
  activeCanvasId: Ref<string | null>
  canvasInstances: Map<string, CanvasInstanceState>
  activeCanvas: ComputedRef<CanvasInstanceState | undefined>

  registerCanvas: (id: string, canvasState: CanvasInstanceState) => void
  unregisterCanvas: (id: string) => void
  setActiveCanvas: (id: string) => void
  getActiveCanvas: () => CanvasInstanceState | undefined
  isCanvasActive: (id: string) => boolean
}

export function useCanvasState(): UseCanvasStateReturn {
  const registerCanvas = (id: string, canvasState: CanvasInstanceState): void => {
    canvasInstances.set(id, canvasState)
    if (!activeCanvasId.value) {
      activeCanvasId.value = id // Set first canvas as active
    }
  }

  const unregisterCanvas = (id: string): void => {
    canvasInstances.delete(id)
    if (activeCanvasId.value === id && canvasInstances.size > 0) {
      const keys = Array.from(canvasInstances.keys())
      activeCanvasId.value = keys[0] ?? null
    } else if (canvasInstances.size === 0) {
      activeCanvasId.value = null
    }
  }

  const setActiveCanvas = (id: string): void => {
    if (canvasInstances.has(id)) {
      activeCanvasId.value = id
    }
  }

  const getActiveCanvas = (): CanvasInstanceState | undefined => {
    return activeCanvasId.value ? canvasInstances.get(activeCanvasId.value) : undefined
  }

  const activeCanvas = computed(() => {
    return activeCanvasId.value ? canvasInstances.get(activeCanvasId.value) : undefined
  })

  const isCanvasActive = (id: string): boolean => {
    return activeCanvasId.value === id
  }

  return {
    activeCanvasId,
    canvasInstances,
    activeCanvas,

    registerCanvas,
    unregisterCanvas,
    setActiveCanvas,
    getActiveCanvas,
    isCanvasActive
  }
}
