/**
 * Coordinate transformation utilities for the Umbra image editor.
 * Handles conversion between different coordinate systems:
 * - Screen coordinates (pixels)
 * - Clip coordinates (-1 to 1)
 * - World coordinates (image space, -1 to 1 normalized)
 * - Image coordinates (0 to image width/height in pixels)
 * - Normalized coordinates (0 to 1)
 */

import type { Point, AspectRatioScale, ImageSize, View } from '@/models/editor'

const DEFAULT_ROTATION_COS = 1
const DEFAULT_ROTATION_SIN = 0
const DEFAULT_ROTATION_ASPECT = 1
const DEFAULT_SCALE = 1
const MIN_ROTATION_MAGNITUDE = 1e-6

/**
 * Convert normalized coordinates (0-1) to image pixel coordinates
 * @param normalized - Point with values in 0-1 range
 * @param imageSize - Image dimensions in pixels
 * @returns Point in pixel coordinates (0 to width/height)
 */
export function normalizedToImage(normalized: Point, imageSize: ImageSize): Point {
  return {
    x: normalized.x * imageSize.width,
    y: normalized.y * imageSize.height
  }
}

/**
 * Convert image pixel coordinates to normalized coordinates (0-1)
 * @param image - Point in pixel coordinates
 * @param imageSize - Image dimensions in pixels
 * @returns Point with values in 0-1 range
 */
export function imageToNormalized(image: Point, imageSize: ImageSize): Point {
  return {
    x: image.x / imageSize.width,
    y: image.y / imageSize.height
  }
}

/**
 * Convert world coordinates (-1 to 1) to image pixel coordinates
 * @param world - Point in world coordinates (-1 to 1)
 * @param imageSize - Image dimensions in pixels
 * @returns Point in pixel coordinates
 */
export function worldToImage(world: Point, imageSize: ImageSize): Point {
  const normalized = {
    x: (world.x + 1) / 2,
    y: (1 - world.y) / 2
  }
  return normalizedToImage(normalized, imageSize)
}

/**
 * Convert image pixel coordinates to world coordinates (-1 to 1)
 * @param image - Point in pixel coordinates
 * @param imageSize - Image dimensions in pixels
 * @returns Point in world coordinates (-1 to 1)
 */
export function imageToWorld(image: Point, imageSize: ImageSize): Point {
  const normalized = imageToNormalized(image, imageSize)
  return {
    x: normalized.x * 2 - 1,
    y: 1 - normalized.y * 2
  }
}

/**
 * Convert array of normalized points to image pixel coordinates
 */
export function normalizedArrayToImage(points: Point[], imageSize: ImageSize): Point[] {
  return points.map(p => normalizedToImage(p, imageSize))
}

/**
 * Convert array of image pixel points to normalized coordinates
 */
export function imageArrayToNormalized(points: Point[], imageSize: ImageSize): Point[] {
  return points.map(p => imageToNormalized(p, imageSize))
}

function normalizeScale(value: number | undefined): number {
  if (typeof value === 'number' && isFinite(value) && value !== 0) {
    return value
  }
  return DEFAULT_SCALE
}

function normalizeRotationAspect(scale: Partial<AspectRatioScale> | null | undefined): number {
  const rotationAspect = scale?.rotationAspect
  if (typeof rotationAspect === 'number' && isFinite(rotationAspect) && rotationAspect > 0) {
    return rotationAspect
  }
  return DEFAULT_ROTATION_ASPECT
}

export function normalizeRotationComponents(scale: Partial<AspectRatioScale> | null | undefined): { rotationCos: number, rotationSin: number } {
  const rotationCos = scale?.rotationCos
  const rotationSin = scale?.rotationSin

  if (
    typeof rotationCos !== 'number'
    || typeof rotationSin !== 'number'
    || !isFinite(rotationCos)
    || !isFinite(rotationSin)
  ) {
    return { rotationCos: DEFAULT_ROTATION_COS, rotationSin: DEFAULT_ROTATION_SIN }
  }

  const magnitude = Math.hypot(rotationCos, rotationSin)
  if (!isFinite(magnitude) || magnitude < MIN_ROTATION_MAGNITUDE) {
    return { rotationCos: DEFAULT_ROTATION_COS, rotationSin: DEFAULT_ROTATION_SIN }
  }

  return {
    rotationCos: rotationCos / magnitude,
    rotationSin: rotationSin / magnitude
  }
}

export function applyRotation(point: Point, scale: Partial<AspectRatioScale> | null | undefined): Point {
  const { rotationCos, rotationSin } = normalizeRotationComponents(scale)
  const rotationAspect = normalizeRotationAspect(scale)
  return {
    x: point.x * rotationCos - (point.y * rotationSin) / rotationAspect,
    y: point.x * rotationSin * rotationAspect + point.y * rotationCos
  }
}

export function applyInverseRotation(point: Point, scale: Partial<AspectRatioScale> | null | undefined): Point {
  const { rotationCos, rotationSin } = normalizeRotationComponents(scale)
  const rotationAspect = normalizeRotationAspect(scale)
  return {
    x: point.x * rotationCos + (point.y * rotationSin) / rotationAspect,
    y: -point.x * rotationSin * rotationAspect + point.y * rotationCos
  }
}

export function worldToClipCoords(world: Point, view: View, aspectRatioScale: AspectRatioScale): Point {
  const scaleX = normalizeScale(aspectRatioScale.scaleX)
  const scaleY = normalizeScale(aspectRatioScale.scaleY)

  const scaled = {
    x: ((world.x * view.zoom) + view.offsetX) * scaleX,
    y: ((world.y * view.zoom) + view.offsetY) * scaleY
  }

  return applyRotation(scaled, aspectRatioScale)
}

export function clipToWorldCoords(clip: Point, view: View, aspectRatioScale: AspectRatioScale): Point {
  const scaleX = normalizeScale(aspectRatioScale.scaleX)
  const scaleY = normalizeScale(aspectRatioScale.scaleY)
  const zoom = (typeof view.zoom === 'number' && isFinite(view.zoom) && view.zoom !== 0) ? view.zoom : 1

  const unrotated = applyInverseRotation(clip, aspectRatioScale)

  return {
    x: ((unrotated.x / scaleX) - view.offsetX) / zoom,
    y: ((unrotated.y / scaleY) - view.offsetY) / zoom
  }
}

/**
 * Convert screen coordinates from a mouse event to world coordinates.
 *
 * @param e - Mouse event with clientX and clientY
 * @param canvas - HTML canvas element
 * @param view - Current view state (zoom and offset)
 * @param aspectRatioScale - Aspect ratio scaling factors
 * @returns World coordinates as Point
 */
export function getWorldCoordsFromEvent(
  e: MouseEvent,
  canvas: HTMLCanvasElement,
  view: View,
  aspectRatioScale: AspectRatioScale
): Point {
  const rect = canvas.getBoundingClientRect()

  const mouseClipX = ((e.clientX - rect.left) / canvas.clientWidth) * 2 - 1
  const mouseClipY = -(((e.clientY - rect.top) / canvas.clientHeight) * 2 - 1)

  return clipToWorldCoords({ x: mouseClipX, y: mouseClipY }, view, aspectRatioScale)
}

/**
 * Convert pixels to world coordinates at current zoom level.
 *
 * @param pixels - Pixel distance
 * @param view - Current view state
 * @returns World coordinate distance
 */
export function pixelsToWorld(pixels: number, view: View): number {
  return pixels / view.zoom
}

/**
 * Clamp a point to world boundaries.
 *
 * @param point - Point to clamp
 * @param bounds - World boundaries
 * @returns Clamped point
 */
export function clampToWorldBounds(point: Point, bounds: { minX: number, maxX: number, minY: number, maxY: number }): Point {
  return {
    x: Math.max(bounds.minX, Math.min(bounds.maxX, point.x)),
    y: Math.max(bounds.minY, Math.min(bounds.maxY, point.y))
  }
}

/**
 * Check if a point is within image boundaries.
 * Note: World coordinates are normalized where the image spans from -1 to 1.
 *
 * @param point - Point to check (in world coordinates)
 * @param _imageSize - Image dimensions (width, height) - unused but kept for API compatibility
 * @returns True if point is within boundaries
 */
export function isPointWithinImageBounds(point: Point, _imageSize?: { width: number, height: number }): boolean {
  return point.x >= -1 && point.x <= 1
    && point.y >= -1 && point.y <= 1
}

/**
 * Get image boundaries as bounds object for clamping.
 * Note: World coordinates are normalized where the image spans from -1 to 1.
 *
 * @param _imageSize - Image dimensions (width, height) - unused but kept for API compatibility
 * @returns Bounds object
 */
export function getImageBounds(_imageSize?: { width: number, height: number }) {
  return {
    minX: -1,
    maxX: 1,
    minY: -1,
    maxY: 1
  }
}
