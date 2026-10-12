import { getCurrentScope, onScopeDispose, watch, type WatchStopHandle } from 'vue'

/**
 * Watch array membership separately from each shape. A point edit should only
 * traverse that shape, and a reorder should only update the affected indices.
 */
export function watchShapes<T extends { id: string }>(
  shapes: T[],
  onChange: (shape: T, index: number) => void,
  onRemove: (shape: T) => void = () => {}
): WatchStopHandle {
  const entries = new Map<string, { shape: T, index: number, stop: WatchStopHandle }>()
  const stopArray = watch(shapes, () => {
    const current = new Set(shapes.map(shape => shape.id))
    for (const [id, entry] of entries) {
      if (current.has(id)) continue
      entry.stop()
      entries.delete(id)
      onRemove(entry.shape)
    }

    shapes.forEach((shape, index) => {
      const existing = entries.get(shape.id)
      if (existing?.shape === shape) {
        if (existing.index !== index) {
          existing.index = index
          onChange(shape, index)
        }
        return
      }

      existing?.stop()
      const entry = { shape, index, stop: () => {} }
      entry.stop = watch(shape, () => onChange(shape, entry.index), { deep: true })
      entries.set(shape.id, entry)
      onChange(shape, index)
    })
  }, { deep: 1, immediate: true })

  const stop = () => {
    stopArray()
    for (const entry of entries.values()) entry.stop()
    entries.clear()
  }
  // Item watches are created in a watcher callback, outside component setup.
  // Dispose them explicitly with the owning canvas's effect scope.
  if (getCurrentScope()) onScopeDispose(stop)
  return stop
}
