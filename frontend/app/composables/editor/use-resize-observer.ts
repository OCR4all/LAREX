export function useResizeObserver(
  canvasRef: Ref<HTMLCanvasElement | null>,
  onResize?: (width: number, height: number) => void
): void {
  let resizeObserver: ResizeObserver | null = null

  onMounted(() => {
    if (!canvasRef.value) return

    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const { width, height } = entry.contentRect

        if (width > 0 && height > 0 && onResize) onResize(width, height)
      }
    })

    resizeObserver.observe(canvasRef.value)
  })

  onBeforeUnmount(() => {
    if (resizeObserver) {
      resizeObserver.disconnect()
      resizeObserver = null
    }
  })
}
