import { Polygon } from '@/models/editor/geometry'
import { isClosedPolygonSelfIntersecting } from './hit-detection'
import type { ImageSize, Point } from '@/models/editor'
import { imageToWorld, worldToImage } from './coordinates'

/** Buffer normalized editor geometry by an image-pixel distance. */
export function bufferPolygonInPixels(polygon: Polygon, distance: number, imageSize: ImageSize): Polygon {
  if (distance === 0 || imageSize.width <= 0 || imageSize.height <= 0) {
    return new Polygon(polygon.points.map(([x, y]) => [x, y]))
  }

  const pixels = polygon.points.map(([x, y]) => worldToImage({ x, y }, imageSize))
  // Polygon.buffer uses left-hand normals; choose the outward side for either winding.
  const signedArea = pixels.reduce((area, point, index) => {
    const next = pixels[(index + 1) % pixels.length]!
    return area + point.x * next.y - next.x * point.y
  }, 0)
  const buffered = new Polygon(pixels.map(p => [p.x, p.y]))
    .buffer(signedArea > 0 ? -distance : distance)

  return new Polygon(buffered.points.map(([x, y]) => {
    const point = imageToWorld({ x, y }, imageSize)
    return [point.x, point.y]
  }))
}

/** Stop before an offset folds an edge back or crosses another edge. */
export function isBufferedPolygonValid(original: Point[], buffered: Point[]): boolean {
  if (buffered.length < 3 || buffered.length !== original.length || buffered.some(p => !Number.isFinite(p.x) || !Number.isFinite(p.y))) return false
  for (let i = 0; i < original.length; i++) {
    const next = (i + 1) % original.length
    const dx = original[next]!.x - original[i]!.x
    const dy = original[next]!.y - original[i]!.y
    if (dx === 0 && dy === 0) continue
    const bx = buffered[next]!.x - buffered[i]!.x
    const by = buffered[next]!.y - buffered[i]!.y
    if (dx * bx + dy * by <= 0) return false
  }
  return !isClosedPolygonSelfIntersecting(buffered)
}

/** Find the contiguous usable range at the slider's one-pixel resolution. */
export function getBufferDistanceRange(
  polygon: Polygon,
  imageSize: ImageSize,
  validate: (points: Point[], distance: number) => boolean
): { min: number, max: number } {
  if (imageSize.width <= 0 || imageSize.height <= 0 || polygon.points.length < 3) return { min: 0, max: 0 }
  const original = polygon.points.map(([x, y]) => ({ x, y }))
  const unitBuffer = bufferPolygonInPixels(polygon, 1, imageSize)
  const offsets = unitBuffer.points.map(([x, y], i) => ({ x: x - original[i]!.x, y: y - original[i]!.y }))
  // Without image/parent constraints, growth is unbounded. Offer an image diagonal.
  const searchLimit = Math.floor(Math.hypot(imageSize.width, imageSize.height))
  function limit(direction: number): number {
    for (let pixels = 1; pixels <= searchLimit; pixels++) {
      const distance = direction * pixels
      const points = original.map((p, i) => ({
        x: p.x + offsets[i]!.x * distance,
        y: p.y + offsets[i]!.y * distance
      }))
      if (!isBufferedPolygonValid(original, points) || !validate(points, distance)) {
        return direction * (pixels - 1) || 0
      }
    }
    return direction * searchLimit
  }
  // Recheck endpoints with the same calculation as preview/Apply to avoid rounding
  // differences between interpolated offsets and the actual pixel conversion.
  function checkedLimit(direction: number): number {
    let distance = limit(direction)
    while (distance !== 0) {
      const points = bufferPolygonInPixels(polygon, distance, imageSize).points.map(([x, y]) => ({ x, y }))
      if (isBufferedPolygonValid(original, points) && validate(points, distance)) break
      distance -= direction
    }
    return distance
  }
  return { min: checkedLimit(-1), max: checkedLimit(1) }
}
