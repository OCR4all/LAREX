// GPU textures live outside the decoded-image LRU. Bound their aggregate size
// across preview canvases. Explicit full-resolution views opt out of this budget.
// Release reservations when a renderer is disposed.
const allocations = new Map<object, { bytes: number, bounded: boolean }>()
const BUDGET = 128 * 1024 * 1024
export function reserveRasterMemory(owner: object, width: number, height: number, bounded = true): () => void {
  const previous = allocations.get(owner)
  const bytes = width * height * 4
  const used = [...allocations.entries()].reduce((sum, [key, value]) => sum + (key === owner || !value.bounded ? 0 : value.bytes), 0)
  if (bounded && used + bytes > BUDGET) throw new Error('Image views exceed the display memory limit. Close another image view and retry.')
  allocations.set(owner, { bytes, bounded })
  return () => {
    if (previous === undefined) allocations.delete(owner)
    else allocations.set(owner, previous)
  }
}
export function releaseRasterMemory(owner: object): void {
  allocations.delete(owner)
}
