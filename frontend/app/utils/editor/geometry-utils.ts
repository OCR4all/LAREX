/**
 * Geometry utility functions for calculations and transformations
 */

import type { Point } from '@/models/editor'

/**
 * Normalizes a vector
 * @param x - X component
 * @param y - Y component
 * @returns Normalized vector with x, y properties
 */
export function normalize(x: number, y: number): Point {
  const len = Math.sqrt(x * x + y * y)
  if (len < 0.0001) {
    return { x: 0, y: 0 }
  }
  return { x: x / len, y: y / len }
}

/**
 * Calculates the perpendicular normal to a line segment
 * @param p1 - Start point of line segment
 * @param p2 - End point of line segment
 * @returns Normal vector perpendicular to the line
 */
export function calculateNormal(p1: Point, p2: Point): Point {
  const dx = p2.x - p1.x
  const dy = p2.y - p1.y
  return normalize(-dy, dx)
}

/**
 * Creates a rectangle polygon for fullscreen rendering
 * @param left - Left coordinate (default: -2)
 * @param bottom - Bottom coordinate (default: -2)
 * @param right - Right coordinate (default: 2)
 * @param top - Top coordinate (default: 2)
 * @returns Array of four points forming a rectangle
 */
export function createFullscreenRect(left = -2, bottom = -2, right = 2, top = 2): Point[] {
  return [
    { x: left, y: bottom }, // Bottom left
    { x: right, y: bottom }, // Bottom right
    { x: right, y: top }, // Top right
    { x: left, y: top } // Top left
  ]
}

/**
 * Flattens triangle indices to vertex coordinates
 * @param points - Array of points with x, y properties
 * @param indices - Array of triangle indices
 * @returns Flattened vertex coordinates for triangles
 */
export function triangulateToVertices(points: Point[], indices: number[]): Float32Array {
  const vertices = new Float32Array(indices.length * 2)
  for (let i = 0; i < indices.length; i++) {
    const index = indices[i]!
    const vertex = points[index]!
    vertices[i * 2] = vertex.x
    vertices[i * 2 + 1] = vertex.y
  }
  return vertices
}
