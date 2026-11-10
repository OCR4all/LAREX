import { toRaw } from 'vue'
import type { Point } from '@/models/editor'

interface Shape {
  id: string
  points: Point[]
}

function hasSamePoints(current: Point[], next: Point[]): boolean {
  if (current.length !== next.length) return false
  for (let index = 0; index < next.length; index++) {
    const a = current[index]
    const b = next[index]
    if (!a || !b || a.x !== b.x || a.y !== b.y) return false
  }
  return true
}

function isSameShape<T extends Shape>(current: T, next: T): boolean {
  const keys = Object.keys(next) as Array<keyof T>
  if (keys.length !== Object.keys(current).length) return false
  return keys.every(key => key === 'points'
    ? hasSamePoints(current.points, next.points)
    : Object.is(current[key], next[key]))
}

/**
 * Bring `shapes` in line with a fresh projection of the document. Shapes whose
 * projection is unchanged keep their object, so a document edit only replaces
 * the shapes it touched instead of every shape on the page.
 *
 * @returns whether any shape was added, removed, replaced or moved
 */
export function reconcileShapes<T extends Shape>(shapes: T[], next: T[]): boolean {
  const current = toRaw(shapes)
  const currentById = new Map<string, T>()
  for (const shape of current) currentById.set(shape.id, shape)

  let changed = current.length !== next.length
  const reconciled = next.map((shape, index) => {
    const existing = currentById.get(shape.id)
    const kept = existing && isSameShape(existing, shape) ? existing : shape
    if (kept !== current[index]) changed = true
    return kept
  })

  if (changed) shapes.splice(0, shapes.length, ...reconciled)
  return changed
}
